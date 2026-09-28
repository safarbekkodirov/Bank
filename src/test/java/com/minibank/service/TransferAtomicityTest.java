package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.minibank.dto.transaction.TransferRequest;
import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * 🔴 TRANSFER АТОМАДДЫК ТЕСТИ (тапшырманын 25-бөлүмү).
 *
 * 🧡 БҮЛ КЛАСС @Transactional ЭМЕС. Себеби:
 *     Тесттин өзүндө @Transactional болсо, тест бүткөндө базанын
 *     баары РОЛЛБЭК болуп, биз «тест өттү» деп ойлоп, чыныгы
 *     ЖАШЫРТПАЙ калабыз. Бизге керек — чыныгы COMMIT/ROLLBACK.
 *
 * 🔴 TRANSFER'ТЕ БҮГҮН АРТИШКАНЫ ӨЗГӮЧӨ: PHASE 5те эки жазуу
 *    эле болчу (баланс + бир журнал). Бул жерде ТӨРТ:
 *        1. source балансы КЕМИРЕТ
 *        2. destination балансы КӨБӨТ
 *        3. TRANSFER_OUT жазуусу
 *        4. TRANSFER_IN жазуусу
 *    Эгер 1,2 иштесе, бирок 3 же 4 ката болсо — колдонуучунун
 *    АКЧАСЫ ПАЙДАСЫЗ ЖОГОЛУП, эсептеги саны менен эсептеги
 *    тарих ТАК КАЛЫП КАЛАД. Бул — эң жаман «акча жоголду» абалы.
 *    Биз бул ЖОК экенин РЕАЛДЫ РОЛЛБЭК менен далилдейбиз.
 */
@SpringBootTest
class TransferAtomicityTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private MoneyService moneyService;

    @Autowired
    private AccountRepository accountRepository;

    /** 🧠 ОРУН АЛГАН (spy) — калган методдор иштейт, биз гана
     *  `save()` катасын «жасап» алабыз. */
    @MockitoSpyBean
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void cleanTables() {
        // 🧡 ТАРТИБ МААНИЛҮҮ: transactions -> accounts -> users (FK RESTRICT).
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User givenUserWithBalance(String email, String balance) {
        User user = userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode("Password123!"), Role.USER));
        accountService.createAccount(user.getId());
        moneyService.deposit(user.getId(), new BigDecimal(balance), "seed");
        return user;
    }

    private User givenUserWithEmptyAccount(String email) {
        User user = userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode("Password123!"), Role.USER));
        accountService.createAccount(user.getId());
        return user;
    }

    private BigDecimal balanceInDatabase(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    private long countOf(User user, TransactionType type) {
        Long accountId = accountRepository.findByUserId(user.getId()).orElseThrow().getId();
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .filter(t -> t.getType() == type)
                .count();
    }

    private TransferRequest request(String toAccountNumber, String amount) {
        return new TransferRequest(toAccountNumber, new BigDecimal(amount), null);
    }

    private String numberOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getAccountNumber();
    }

    // ==================== 1. ЖУРНАЛ САКТАЛБАСА ====================

    @Test
    @DisplayName("1. 🔴 1-кадам: TRANSFER_OUT save КАТАСЫ -> ЭКӨӨ баланс ӨЗГӨРБӨЙТ")
    void firstTransactionSaveFailureRollsBackBothBalances() {
        User a = givenUserWithBalance("atomic-1-a@example.com", "1000.0000");
        User b = givenUserWithBalance("atomic-1-b@example.com", "500.0000");

        // 🔴 Биринчи журнал жазуусу ишке ТИЙБЕЙ ҮЧҮН ката берет.
        doThrow(new DataIntegrityViolationException("simulated journal insert failure"))
                .when(transactionRepository).save(any(Transaction.class));

        assertThatThrownBy(() -> transferService.transfer(a.getId(), request(numberOf(b), "300.0000")))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 🔴🔴 ЭКӨӨ БАЛАНС ТАЗА ҮЧҮН.
        assertThat(balanceInDatabase(a)).isEqualByComparingTo("1000.0000");
        assertThat(balanceInDatabase(b)).isEqualByComparingTo("500.0000");

        // 🧡 ЖУРНАЛДА ЭЧ НЕРСЕ ЖОК.
        assertThat(countOf(a, TransactionType.TRANSFER_OUT)).isZero();
        assertThat(countOf(b, TransactionType.TRANSFER_IN)).isZero();
    }

    @Test
    @DisplayName("2. 🔴 2-кадам: TRANSFER_IN save КАТАСЫ -> ЭКӨӨ баланс + OUT жазуусу да КАЙТЫЛАТ")
    void secondTransactionSaveFailureRollsBackEverything() {
        User a = givenUserWithBalance("atomic-2-a@example.com", "1000.0000");
        User b = givenUserWithBalance("atomic-2-b@example.com", "500.0000");

        // 🧡 БУЛ ТЕСТ ӨЗГӢЧӮН АЛТЫНЧЫ — «жарым жолдо» ката.
        //     Биринчи жазуу (OUT) САКТАЛЫП, ЭКИНЧИ (IN) ката берет.
        //     Эгер @Transactional иштебэсе: A = 700, B = 500 (акча
        //     КАЙДА? 300 жок болмокчу) жана журналда жалганча жазуу.
        doThrow(new DataIntegrityViolationException("simulated second insert failure"))
                .when(transactionRepository)
                .save(argThat(t -> t.getType() == TransactionType.TRANSFER_IN));

        assertThatThrownBy(() -> transferService.transfer(a.getId(), request(numberOf(b), "300.0000")))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 🔴 ЭКӨӨ БАЛАНС КАЙТЫЛДЫ.
        assertThat(balanceInDatabase(a)).isEqualByComparingTo("1000.0000");
        assertThat(balanceInDatabase(b)).isEqualByComparingTo("500.0000");

        // 🔴🔴 ЖАРЫМ ЖОЛДУК ЖАЗУУ ДА КАЛБАЙТ — «OUT» жазылган
        //    көрүнөт, бирок чыныгында РОЛЛБЭК болгон.
        assertThat(countOf(a, TransactionType.TRANSFER_OUT)).isZero();
        assertThat(countOf(b, TransactionType.TRANSFER_IN)).isZero();
    }

    @Test
    @DisplayName("3. 🔴 Баланстар БАЗАГА ЖЕТКЕЙ ИЛГИРИ САЛЫНГАН -> РОЛЛБЭК")
    void balancesActuallyFlushedToDatabaseAreRolledBack() {
        User a = givenUserWithBalance("atomic-3-a@example.com", "1000.0000");
        User b = givenUserWithBalance("atomic-3-b@example.com", "500.0000");

        // 🧡 БУЛ — ЭҢ КИЙИНТЫ ТЕСТ. Биз атайын flush() чакырып,
        //     эки UPDATE'ти ЧЫНГЫ PostgreSQL'ге ЖЕТКИРЕБИЗ, кийин
        //     ката ташталат. Ошондо күткөнүбүз: базада «UPDATE
        //     accounts SET balance = 700/800» чыныгы ИЛГЕРДИ,
        //     бирок транзакция КАЙТЫЛГАНДУН КАРАЙ баланс кайра
        //     1000/500. Демек бул РЕАЛДЫ РОЛЛБЭК далилдеген тест.
        doAnswer(invocation -> {
            entityManager.flush();   // <- эки UPDATE чыныгы базага кетет
            throw new DataIntegrityViolationException("journal failed AFTER balance updates");
        }).when(transactionRepository).save(any(Transaction.class));

        assertThatThrownBy(() -> transferService.transfer(a.getId(), request(numberOf(b), "300.0000")))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 🔴 БУЛ ЖЕРДЕ ЭЧТЕНБЕЧ КУЧТҮҮ: flush кылганыбыз менен
        //    базада 700/800 болуп ТАБЫЛГАН болсо, бул ассерш өтмөйт.
        assertThat(balanceInDatabase(a)).isEqualByComparingTo("1000.0000");
        assertThat(balanceInDatabase(b)).isEqualByComparingTo("500.0000");

        assertThat(countOf(a, TransactionType.TRANSFER_OUT)).isZero();
        assertThat(countOf(b, TransactionType.TRANSFER_IN)).isZero();
    }

    @Test
    @DisplayName("4. 🧡 Ийгилик болгонда ТӨРТ жазуу тең САКТАЛАТ (эки баланс + OUT + IN)")
    void allFourWritesCommitTogetherOnSuccess() {
        User a = givenUserWithBalance("atomic-4-a@example.com", "1000.0000");
        User b = givenUserWithBalance("atomic-4-b@example.com", "500.0000");

        transferService.transfer(a.getId(), request(numberOf(b), "300.0000"));

        assertThat(balanceInDatabase(a)).isEqualByComparingTo("700.0000");
        assertThat(balanceInDatabase(b)).isEqualByComparingTo("800.0000");
        assertThat(countOf(a, TransactionType.TRANSFER_OUT)).isEqualTo(1);
        assertThat(countOf(b, TransactionType.TRANSFER_IN)).isEqualTo(1);
    }

    // MORE_TESTS
}
