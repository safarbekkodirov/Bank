package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * 🔴 АТОМАДДЫК ТЕСТ (тапшырманын 11-бөлүмү) — «эки таамал бир иш».
 *
 * 🧠 БУЛ КЛАСС @Transactional ЭМЕС. Себеби:
 *     Тесттин өзүндө @Transactional болсо, тест бүткөндө базанын
 *     баары РОЛЛБЭК болуп, биз «тест өттү» деп ойлоп, чыныгы
 *     ЖАШЫРТПАЙ калабыз. Бизге керек — чыныгы COMMIT/ROLLBACK:
 *     операция ийгиликтен кийин көзгө көрүнөт, катадан кийин
 *     КАЙТАРЫЛЫП ЖОК болот. Демек тест транзакциясы ЖОК —
 *     ар бир кылым өз «чыныгы» транзакциясын ачат.
 *
 * 🧠 ЭЧ КИМ КАЙТАРЫЛБАСЫН ДЕП ТАЗАЛОО:
 *     Маалымат чыныгы базага САКТАЛЫП калат, демек @AfterEach
 *     менен биз таблицаларды көзөмөлсүз тазалайбыз.
 */
@SpringBootTest
class MoneyOperationAtomicityTest {

    @Autowired
    private MoneyService moneyService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    /** 🧠 ЧЫНЫГЫ эмес, ОРУН АЛГАН (spy) — калган методдор иштейт,
     *  биз гана `save()` катасын «жасап» алабыз. */
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

    /** Эсеби толгон колдонуучу. */
    private User givenUserWithBalance(String email, String balance) {
        User user = userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode("Password123!"), Role.USER));
        accountService.createAccount(user.getId());
        moneyService.deposit(user.getId(), new BigDecimal(balance), "seed");
        return user;
    }

    private Long accountIdOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getId();
    }

    /** 🔄 БАЗАГАНЫН ЧЫНГЫ АБАЛЫ — жаңы транзакцияда, ЖАНГЫ суроо менен. */
    private BigDecimal balanceInDatabase(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    private List<Transaction> ledgerOf(User user) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountIdOf(user));
    }

    // ==================== ЖУРНАЛ САКТАЛБАСА ====================

    @Test
    @DisplayName("1. 🔴 Transaction save КАТАСЫ -> баланс да ROLLBACK (deposit)")
    void balanceRollsBackWhenTransactionSaveFails() {
        User user = givenUserWithBalance("atomic-deposit@example.com", "1000.0000");

        // 🔴 «Журналды салуу» катасын ЖАСАП ЖАРАТЫБЫЗ — мисалы, диск
        //    толуп калды, байланыш үзүлдү, CHECK ережеси бузулду.
        doThrow(new DataIntegrityViolationException("simulated journal insert failure"))
                .when(transactionRepository).save(any(Transaction.class));

        assertThatThrownBy(() ->
                moneyService.deposit(user.getId(), new BigDecimal("500.0000"), "must not persist"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 🔴 ЭҢ МААНИЛҮҮ ТЕКШЕРҮҮ: баланс 1500 ЭМЕС, 1000.0000 калды.
        assertThat(balanceInDatabase(user)).isEqualByComparingTo("1000.0000");

        // 🧡 Журналда жаңы жазуу ЖОК — бары 1 (биздин «көчүрүү»).
        assertThat(ledgerOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("2. 🔴 Transaction save КАТАСЫ -> баланс да ROLLBACK (withdraw)")
    void balanceRollsBackWhenTransactionSaveFailsOnWithdraw() {
        User user = givenUserWithBalance("atomic-withdraw@example.com", "1000.0000");

        doThrow(new DataIntegrityViolationException("simulated journal insert failure"))
                .when(transactionRepository).save(any(Transaction.class));

        assertThatThrownBy(() ->
                moneyService.withdraw(user.getId(), new BigDecimal("300.0000"), "must not persist"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 🔴 БАЛАНС ТЕРС ЭМЕС, 700.0000 ЭМЕС — 1000.0000 ОРУНДА.
        assertThat(balanceInDatabase(user)).isEqualByComparingTo("1000.0000");
        assertThat(balanceInDatabase(user).signum()).isNotNegative();
        assertThat(ledgerOf(user)).hasSize(1);
    }

    // ==================== ЧЫНГЫ РОЛЛБЭК ====================

    @Test
    @DisplayName("3. 🔴 Баланс БАЗАГА ЖЕТКЕЙ ИЛГИРИ САЛЫНГАН -> РОЛЛБЭК")
    void balanceUpdateActuallyFlushedToDatabaseIsRolledBack() {
        User user = givenUserWithBalance("atomic-flush@example.com", "1000.0000");

        // 🧡 БУЛ — ЭҢ КЫЙЫНТЫ ТЕСТ. Жогорудагы эки тест «экөө бир
        //    иш» экенин көрсөтөт, бирок аларда UPDATE базага
        //    ЖЕТПЕЙ ЭЛЕ (Hibernate балансты COMMITте гана жазат),
        //    демек «эч нерсэ болгон эмес» деп сыноо үнүшү
        //    боло кошош. БИЗДЕ бул жагымдуураак: биз атайын
        //    flush() чакырып, UPDATE'ти ЧЫНГЫ PostgreSQL'ге
        //    ЖЕТКИРЕБИЗ — кийин ката ташталат.
        //
        //    Ошондо күткөнүбүз: PostgreSQL'де «UPDATE accounts
        //    SET balance = 1500» чыныгы ИЛГЕРДИ, бирок транзакция
        //    КАЙТЫЛГАНДУН КАРАЙ баланс кайра 1000.0000.
        doAnswer(invocation -> {
            entityManager.flush();   // <- UPDATE чыныгы базага кетет
            throw new DataIntegrityViolationException("journal insert failed AFTER balance update");
        }).when(transactionRepository).save(any(Transaction.class));

        assertThatThrownBy(() ->
                moneyService.deposit(user.getId(), new BigDecimal("500.0000"), "flushed then failed"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 🔴 БУЛ ЖЕРДЕ ЭЧТЕНБЕЧ КУЧТҮҮ: flush кылганыбыз менен
        //    PostgreSQL'де 1500.0000 болуп ТАБЫЛГАН болсо, бул
        //    ассерш өтмөйт. Демек тест РЕАЛДЫ ROLLBACK'ти текшерет.
        assertThat(balanceInDatabase(user)).isEqualByComparingTo("1000.0000");
        assertThat(ledgerOf(user)).hasSize(1);
    }

    // ==================== ИЙГИЛИК ====================

    @Test
    @DisplayName("4. 🧡 Ийгилик болгондо эки жазуу да САКТАЛАТ (бир иш = эки таамал)")
    void bothWritesCommitTogetherOnSuccess() {
        User user = givenUserWithBalance("atomic-success@example.com", "1000.0000");

        // 🧡 Ката жок — spy тийбейт, чыныгы INSERT жүрөт.
        moneyService.deposit(user.getId(), new BigDecimal("500.0000"), "ok");

        assertThat(balanceInDatabase(user)).isEqualByComparingTo("1500.0000");

        List<Transaction> ledger = ledgerOf(user);
        assertThat(ledger).hasSize(2);

        Transaction latest = ledger.get(0);   // эң жаңысы биринчи
        assertThat(latest.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(latest.getAmount()).isEqualByComparingTo("500.0000");
        assertThat(latest.getBalanceAfter()).isEqualByComparingTo("1500.0000");

        // 🧡 15-бөлүм: сумма ПОЗИТИВДУУ.
        assertThat(latest.getAmount().signum()).isPositive();
        assertThat(latest.getAccount().getId()).isNotNull();
    }
}
