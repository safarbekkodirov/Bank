package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.transaction.MoneyOperationResponse;
import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.exception.InvalidAmountException;
import com.minibank.exception.InvalidDescriptionException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

/**
 * АКЧА ОПЕРАЦИЯЛАРЫНЫН ТЕСТИ (Service катмары) — чыныгы PostgreSQL менен.
 *
 * 🧡 Биз бул жерде эки чоң суроого жооп издейбиз:
 *     1. БИЗНЕС-ЭРЕЖЕ — «0 салууга болобу?», «калгандан көп
 *        чыгарууга болобу?» (тапшырманын 8-9-бөлүмдөрү)
 *     2. ЖУРНАЛ — «бир операция = бир жазуу» (тапшырманын 14-бөлүмү)
 *
 * 🧠 ЧЫНЫГЫ БАЗА, ЭМНЕ? Ошол эле себеби PHASE 4тегидей:
 *     1. Java Entity база менен таптак келеби (ddl-auto=validate)
 *     2. Маалымат базасындагы CHECK эрежелери ЧЫНЫГЫ иштеп жатабы
 */
@SpringBootTest
@Transactional
class MoneyOperationServiceTest {

    @Autowired
    private MoneyService moneyService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 🧡 БАЗАГА ТҮЗДҮГҮН ЖАЗУУ (raw SQL) — DB'нин CHECK эрежесинин
     *  чыныгы иштешине текшерүү үчүн. JPA'нын объекттери аркылуу
     *  «паразит» жазуу кылууга биздин ак таза жолубуз жок. */
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void cleanTables() {
        // 🧡 ТАРТИБ МААНИЛҮҮ: transactions -> accounts -> users.
        //    Себеби: эгер transactions'ти унутсак, эсеп өчүрүлгөндө
        //    эсепке байланыштуу операциялар ON DELETE RESTRICT менен
        //    КАРГАЙТ («эсепти өчүрүп болбосо, анын 700 салып алган
        //    тарыхы бар эле» деп).
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User givenUserWithAccount(String email) {
        User user = userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode("Password123!"), Role.USER));
        accountService.createAccount(user.getId());
        return user;
    }

    private User givenUserWithoutAccount(String email) {
        return userRepository.save(new User(
                "Эсепсіз " + email, email,
                passwordEncoder.encode("Password123!"), Role.USER));
    }

    /** Журналдагы жалгыз жазуу — «бир операция = бир жазуу» текшерүү үчүн. */
    private Transaction onlyTransaction(Long accountId) {
        List<Transaction> all = transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId);
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    /** Журналдын эң ЖАҮСИ (жалгыз эмес, бир нече болгон учурда). */
    private Transaction onlyLastTransaction(Long accountId) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).get(0);
    }

    private Long accountIdOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getId();
    }

    private BigDecimal balanceOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    // ==================== DEPOSIT (тапшырма 1-11) ====================

    @Test
    @DisplayName("1. 💰 Deposit ийгиликтүү (жаооп толук)")
    void depositSucceeds() {
        User user = givenUserWithAccount("deposit@example.com");

        MoneyOperationResponse response =
                moneyService.deposit(user.getId(), new BigDecimal("500.0000"), "Cash deposit");

        assertThat(response).isNotNull();
        assertThat(response.accountNumber()).isNotBlank();
        assertThat(response.currency()).isEqualTo("RUB");
        assertThat(response.amount()).isEqualByComparingTo("500.0000");
        assertThat(response.balance()).isEqualByComparingTo("500.0000");
        assertThat(response.transactionId()).isNotNull();
    }

    @Test
    @DisplayName("2. 💰 Deposit балансты АСЫЛТАРЫТ (1000 + 500 = 1500)")
    void depositIncreasesBalance() {
        User user = givenUserWithAccount("increase@example.com");
        moneyService.deposit(user.getId(), new BigDecimal("1000.0000"), null);

        MoneyOperationResponse response =
                moneyService.deposit(user.getId(), new BigDecimal("500.0000"), null);

        assertThat(response.balance()).isEqualByComparingTo("1500.0000");
        assertThat(balanceOf(user)).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("3. 💰 Deposit -> бир DEPOSIT жазуусу түзүлөт")
    void depositCreatesTransaction() {
        User user = givenUserWithAccount("tx@example.com");

        moneyService.deposit(user.getId(), new BigDecimal("250.5000"), null);

        assertThat(transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountIdOf(user)))
                .hasSize(1);
    }

    @Test
    @DisplayName("4. 💰 Жазуунун түрү = DEPOSIT")
    void depositTransactionTypeIsDeposit() {
        User user = givenUserWithAccount("type@example.com");

        moneyService.deposit(user.getId(), new BigDecimal("100.0000"), null);

        assertThat(onlyTransaction(accountIdOf(user)).getType())
                .isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    @DisplayName("5. 💰 Жазуунун суммасы = салган сумма")
    void depositTransactionAmountIsCorrect() {
        User user = givenUserWithAccount("amount@example.com");

        moneyService.deposit(user.getId(), new BigDecimal("250.5000"), null);

        assertThat(onlyTransaction(accountIdOf(user)).getAmount())
                .isEqualByComparingTo("250.5000");
    }

    @Test
    @DisplayName("6. 💰 Жазуунун balanceAfter = ОПЕРАЦИЯДАН КИЙИНКИ баланс")
    void depositTransactionBalanceAfterIsCorrect() {
        User user = givenUserWithAccount("after@example.com");
        moneyService.deposit(user.getId(), new BigDecimal("1000.0000"), null);

        moneyService.deposit(user.getId(), new BigDecimal("250.5000"), null);

        // 🧡 БАЙКО: 1250.5000, ЭС 250.5000. «Канча салдым» эмес —
        //    «операциядан КИЙИН канча болду» деп жазылат.
        assertThat(onlyLastTransaction(accountIdOf(user)).getBalanceAfter())
                .isEqualByComparingTo("1250.5000");
    }

    @Test
    @DisplayName("7. 💰 Description сакталат")
    void depositSavesDescription() {
        User user = givenUserWithAccount("description@example.com");

        moneyService.deposit(user.getId(), new BigDecimal("10.0000"), "Cash deposit at ATM");

        assertThat(onlyTransaction(accountIdOf(user)).getDescription())
                .isEqualTo("Cash deposit at ATM");
    }

    @Test
    @DisplayName("8. 💰 🔴 Сумма = 0 -> InvalidAmountException (400)")
    void zeroAmountIsRejected() {
        User user = givenUserWithAccount("zero@example.com");

        assertThatThrownBy(() -> moneyService.deposit(user.getId(), new BigDecimal("0"), null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than 0");

        assertThat(balanceOf(user)).isEqualByComparingTo("0.0000");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("9. 💰 🔴 Сумма < 0 -> InvalidAmountException (400)")
    void negativeAmountIsRejected() {
        User user = givenUserWithAccount("negative@example.com");

        assertThatThrownBy(() -> moneyService.deposit(user.getId(), new BigDecimal("-100"), null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than 0");

        assertThat(balanceOf(user)).isEqualByComparingTo("0.0000");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("10. 💰 🔴 4төн көп ондук бөлүк -> 400. ТЕГЕРЕКТЕБЕЙБИЗ")
    void tooManyDecimalsAreRejected() {
        User user = givenUserWithAccount("scale@example.com");

        assertThatThrownBy(() ->
                moneyService.deposit(user.getId(), new BigDecimal("100.12345"), null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("4 decimal places");

        // 🧡 КРИТИК: баланс 100.1235 КЫЛЫП ТЕГЕРЕКТЕБЕДИ. Эгер
        //    кылса, колдонуучу «мен 100.12 жаздим, эми 100.1235
        //    көрүнөт» деп дат кылып, колдонмого ишеничтөнөр эле.
        assertThat(balanceOf(user)).isEqualByComparingTo("0.0000");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("11. 💰 Эсеп ЖОК -> AccountNotFoundException (404)")
    void depositWithoutAccountThrows() {
        User user = givenUserWithoutAccount("no-acc@example.com");

        assertThatThrownBy(() -> moneyService.deposit(user.getId(), new BigDecimal("100"), null))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("No bank account");
    }

    @Test
    @DisplayName("12. 💰 Сумма жок (null) -> InvalidAmountException")
    void nullAmountIsRejected() {
        User user = givenUserWithAccount("null@example.com");

        assertThatThrownBy(() -> moneyService.deposit(user.getId(), null, null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("13. 💰 4 каттуу жазылуунун БАРДЫК түрлөрү кабылданат")
    void fourDecimalFormatsAreAccepted() {
        User user = givenUserWithAccount("formats@example.com");

        moneyService.deposit(user.getId(), new BigDecimal("100"), null);
        moneyService.deposit(user.getId(), new BigDecimal("100.5"), null);
        moneyService.deposit(user.getId(), new BigDecimal("100.50"), null);
        MoneyOperationResponse last =
                moneyService.deposit(user.getId(), new BigDecimal("100.5000"), null);

        // 🧡 КАТТУУЛУК (scale) — ар башка, МААНИ — баланс 401.5000.
        //    100 + 100.5 + 100.50 + 100.5000 = 401.5
        assertThat(last.balance()).isEqualByComparingTo("401.5000");
        assertThat(balanceOf(user).scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("14. 💰 Description бербесе -> null сакталат (бош «изо» жок)")
    void missingDescriptionIsStoredAsNull() {
        User user = givenUserWithAccount("no-description@example.com");

        moneyService.deposit(user.getId(), new BigDecimal("10.0000"), null);

        assertThat(onlyTransaction(accountIdOf(user)).getDescription()).isNull();
    }

    @Test
    @DisplayName("15. 💰 500 белгиден узун description -> 400")
    void tooLongDescriptionIsRejected() {
        User user = givenUserWithAccount("long-description@example.com");

        assertThatThrownBy(() -> moneyService.deposit(
                user.getId(), new BigDecimal("10.0000"), "а".repeat(501)))
                .isInstanceOf(InvalidDescriptionException.class)
                .hasMessageContaining("500");

        assertThat(transactionRepository.count()).isZero();
    }

    // ==================== WITHDRAW (тапшырма 13-25) ====================

    /** Эсепке 1000 салып, колдонуучуну кайтарабыз. */
    private User givenUserWithBalance(String email) {
        User user = givenUserWithAccount(email);
        moneyService.deposit(user.getId(), new BigDecimal("1000.0000"), "initial");
        return user;
    }

    @Test
    @DisplayName("16. 💸 Withdraw ийгиликтүү (жаооп толук)")
    void withdrawSucceeds() {
        User user = givenUserWithBalance("withdraw@example.com");

        MoneyOperationResponse response =
                moneyService.withdraw(user.getId(), new BigDecimal("300.0000"), "Cash withdrawal");

        assertThat(response).isNotNull();
        assertThat(response.currency()).isEqualTo("RUB");
        assertThat(response.amount()).isEqualByComparingTo("300.0000");
        assertThat(response.balance()).isEqualByComparingTo("700.0000");
        assertThat(response.transactionId()).isNotNull();
    }

    @Test
    @DisplayName("17. 💸 Withdraw балансты КЕМИРҮЙТ (1500 − 300 = 1200)")
    void withdrawDecreasesBalance() {
        User user = givenUserWithBalance("decrease@example.com");
        moneyService.deposit(user.getId(), new BigDecimal("500.0000"), "more");
        assertThat(balanceOf(user)).isEqualByComparingTo("1500.0000");

        MoneyOperationResponse response =
                moneyService.withdraw(user.getId(), new BigDecimal("300.0000"), null);

        assertThat(response.balance()).isEqualByComparingTo("1200.0000");
        assertThat(balanceOf(user)).isEqualByComparingTo("1200.0000");
    }

    @Test
    @DisplayName("18. 💸 Withdraw -> бир WITHDRAW жазуусу түзүлөт")
    void withdrawCreatesTransaction() {
        User user = givenUserWithBalance("w-tx@example.com");

        moneyService.withdraw(user.getId(), new BigDecimal("100.0000"), null);

        // 🧡 БАРЫ 2: баштапкы 1000 салуу + ушул чыгаруу
        assertThat(transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountIdOf(user)))
                .hasSize(2);
    }

    @Test
    @DisplayName("19. 💸 Жазуунун түрү = WITHDRAW")
    void withdrawTransactionTypeIsWithdraw() {
        User user = givenUserWithBalance("w-type@example.com");

        moneyService.withdraw(user.getId(), new BigDecimal("100.0000"), null);

        // 🧡 findByAccountIdOrderByCreatedAtDesc -> эң ЖАҢЫСЫ БАШТА,
        //    демек чыгаруунун жазуусу биринчи келеди.
        assertThat(onlyLastTransaction(accountIdOf(user)).getType())
                .isEqualTo(TransactionType.WITHDRAW);
    }

    @Test
    @DisplayName("20. 💸 🔴 Жазуунун суммасы ПОЗИТИВДУУ (200, -200 ЭМЕС)")
    void withdrawTransactionAmountStaysPositive() {
        User user = givenUserWithBalance("w-amount@example.com");

        moneyService.withdraw(user.getId(), new BigDecimal("200.0000"), null);

        // 🧡 БУЛ ТАПШЫРМАНЫН 15-БӨЛҮМҮ: WITHDRAW -200 ЭМЕС, +200.
        //    Себеби: БАЗАДА CHECK (amount > 0) бар — терс жазсак,
        //    маалымат базасы БИЗДИ ТАШТАРТМАЙТ. Демек бул
        //    «маалымат базасынын четиги» жана биз аны бузууга
        //    аракет кылмак эмес.
        BigDecimal amount = onlyLastTransaction(accountIdOf(user)).getAmount();
        assertThat(amount).isEqualByComparingTo("200.0000");
        assertThat(amount.signum()).isPositive();
    }

    @Test
    @DisplayName("21. 💸 Жазуунун balanceAfter = чыгаруудан КИЙИНКИ баланс")
    void withdrawTransactionBalanceAfterIsCorrect() {
        User user = givenUserWithBalance("w-after@example.com");

        moneyService.withdraw(user.getId(), new BigDecimal("200.0000"), null);

        assertThat(onlyLastTransaction(accountIdOf(user)).getBalanceAfter())
                .isEqualByComparingTo("800.0000");
    }

    @Test
    @DisplayName("22. 💸 Withdraw description сакталат")
    void withdrawSavesDescription() {
        User user = givenUserWithBalance("w-description@example.com");

        moneyService.withdraw(user.getId(), new BigDecimal("10.0000"), "ATM withdrawal");

        assertThat(onlyLastTransaction(accountIdOf(user)).getDescription())
                .isEqualTo("ATM withdrawal");
    }

    @Test
    @DisplayName("23. 💸 🔴 🔴 Калгандан КӨП чыгаруу -> InsufficientFundsException (409)")
    void insufficientFundsIsRejected() {
        User user = givenUserWithAccount("insufficient@example.com");
        moneyService.deposit(user.getId(), new BigDecimal("1000.0000"), null);

        // 🧡 1000.0001 — ОГОН 1 тыйын. Бүтүн сандар менен
        //    «жөнөкөй» мисалдар көп учурда «иштейт» болуп чыгат
        //    (себеби Java'да float так эмес), демек БИЗ БОРЖО
        //    ЧЫГАРЫП, эң жагымдуу чегини көрсөтөбүз.
        assertThatThrownBy(() ->
                moneyService.withdraw(user.getId(), new BigDecimal("1000.0001"), "too much"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        // 🔴 БАЛАНС ОЗГӨРБӨДҮ — терс болгон эмес, 1000.0000 калды
        assertThat(balanceOf(user)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("24. 💸 БАЛАНСИНЧА чыгаруу -> 0.0000 (тең уруксат)")
    void withdrawingExactBalanceLeavesZero() {
        User user = givenUserWithBalance("exact@example.com");

        MoneyOperationResponse response =
                moneyService.withdraw(user.getId(), new BigDecimal("1000.0000"), "all of it");

        assertThat(response.balance()).isEqualByComparingTo("0.0000");
        // 🧡 ФОРМАТ да чоң: "0" ЭМЕС, "0.0000" болууга тийиш
        assertThat(balanceOf(user).scale()).isEqualTo(4);
        assertThat(balanceOf(user)).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("25. 💸 🔴 Сумма = 0 -> InvalidAmountException")
    void withdrawZeroAmountIsRejected() {
        User user = givenUserWithBalance("w-zero@example.com");

        assertThatThrownBy(() -> moneyService.withdraw(user.getId(), new BigDecimal("0"), null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than 0");

        assertThat(balanceOf(user)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("26. 💸 🔴 Сумма < 0 -> InvalidAmountException")
    void withdrawNegativeAmountIsRejected() {
        User user = givenUserWithBalance("w-negative@example.com");

        assertThatThrownBy(() -> moneyService.withdraw(user.getId(), new BigDecimal("-100"), null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than 0");

        assertThat(balanceOf(user)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("27. 💸 🔴 4төн көп ондук бөлүк -> 400")
    void withdrawTooManyDecimalsAreRejected() {
        User user = givenUserWithBalance("w-scale@example.com");

        assertThatThrownBy(() ->
                moneyService.withdraw(user.getId(), new BigDecimal("100.12345"), null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("4 decimal places");

        assertThat(balanceOf(user)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("28. 💸 Эсеп ЖОК -> AccountNotFoundException (404)")
    void withdrawWithoutAccountThrows() {
        User user = givenUserWithoutAccount("w-no-acc@example.com");

        assertThatThrownBy(() -> moneyService.withdraw(user.getId(), new BigDecimal("10"), null))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("No bank account");
    }

    @Test
    @DisplayName("29. 💸 Эсеп БОШ (0.0000) -> 409 InsufficientFunds")
    void withdrawFromEmptyAccountIsRejected() {
        User user = givenUserWithAccount("empty@example.com");

        assertThatThrownBy(() -> moneyService.withdraw(user.getId(), new BigDecimal("0.0001"), null))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balanceOf(user)).isEqualByComparingTo("0.0000");
    }

    // ============ БҮТҮНДӨГҮ КАТМАР: ЖУРНАЛ + БАЗА + КООПСУЗДУК ============

    @Test
    @DisplayName("30. 📖 Бүтүн тарих: 1000 +500 = 1500, −200 = 1300 (тапшырма 14)")
    void fullLedgerChainStaysConsistent() {
        User user = givenUserWithBalance("chain@example.com");

        moneyService.deposit(user.getId(), new BigDecimal("500.0000"), "Cash deposit");
        moneyService.withdraw(user.getId(), new BigDecimal("200.0000"), "Cash withdrawal");

        assertThat(balanceOf(user)).isEqualByComparingTo("1300.0000");

        // 🧡 Эң ЖАҮСИНЕЙИН (timestamp боюнча) -> WITHDRAW 200 / 1300
        List<Transaction> ledger =
                transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountIdOf(user));
        assertThat(ledger).hasSize(3);

        Transaction withdrawTx = ledger.get(0);
        assertThat(withdrawTx.getType()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(withdrawTx.getAmount()).isEqualByComparingTo("200.0000");
        assertThat(withdrawTx.getBalanceAfter()).isEqualByComparingTo("1300.0000");

        Transaction depositTx = ledger.get(1);
        assertThat(depositTx.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(depositTx.getAmount()).isEqualByComparingTo("500.0000");
        assertThat(depositTx.getBalanceAfter()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("31. 📖 Каждая операция = бир жазуу (эсеп 100 баштап)")
    void everyOperationCreatesExactlyOneTransaction() {
        User user = givenUserWithAccount("count@example.com");

        moneyService.deposit(user.getId(), new BigDecimal("10"), null);
        moneyService.deposit(user.getId(), new BigDecimal("10"), null);
        moneyService.withdraw(user.getId(), new BigDecimal("5"), null);

        assertThat(transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(accountIdOf(user))).hasSize(3);
    }

    @Test
    @DisplayName("32. 🛡️ Маалымат базасы ТЕРС балансты өзи каргайт (CHECK)")
    void databaseRejectsNegativeBalance() {
        User user = givenUserWithAccount("db-check@example.com");
        Long accountId = accountIdOf(user);

        // 🧡 БУЛ ТЕСТ БИЗДИН ЭМЕС, МААЛЫМАТ БАЗАСЫНЫН ЧЕТИГИ:
        //    CHECK (balance >= 0) (V1__init.sql). Биз Java'да канча
        //    да коргоо коюп, акыркы сырткы чеги DB'де. Бул тест
        //    «DB чыныгы иштеп жатабы?» деп текшерет.
        // 🧠 Катанын түрү: JPA'нын өзүнүнүкү
        //    (jakarta.persistence.PersistenceException) — Spring'дин
        //    «DataIntegrityViolationException» көтөрмөсү Repository/
        //    JdbcTemplate катматында гана пайда болот. Биз бул
        //    жерде түз EntityManager менен жазып жатабыз.
        assertThatThrownBy(() -> entityManager
                .createQuery("update Account a set a.balance = -1 where a.id = :id")
                .setParameter("id", accountId)
                .executeUpdate())
                .isInstanceOf(jakarta.persistence.PersistenceException.class)
                .hasMessageContaining("ck_accounts_balance_non_negative");
    }

    @Test
    @DisplayName("33. 🛡️ Маалымат базасы БЕЛГИСИЗ type'ти кабылдабайт")
    void databaseRejectsUnknownTransactionType() {
        User user = givenUserWithAccount("db-type@example.com");
        Long accountId = accountIdOf(user);

        // 🧡 'TRANSFER' — V5 миграциясынан КИЙИН азыр чыныгы
        //    эмес. БУЛ БИЗДИН МАКЕТИБИЗДИ ТЕКШЕРҮҮ: enum'га жана
        //    базанын CHECK'ине чыныгы эмес түр кирсе, DB каргайт.
        // 🧡 createNATIVEQuery — JPQL'де raw INSERT ЖАЗЫЛБАЙТ
        //    («бейтарап SQL»). Биз PostgreSQL'дин өз SQL'ин
        //    жазабыз.
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        insert into transactions (account_id, type, amount, balance_after, created_at)
                        values (?1, 'TRANSFER', 10, 10, now())
                        """)
                .setParameter(1, accountId)
                .executeUpdate())
                .isInstanceOf(jakarta.persistence.PersistenceException.class)
                .hasMessageContaining("ck_transactions_type");
    }

    @Test
    @DisplayName("34. 🛡️ Жоопто колдонуучу жана пароль ЖОК (Entity ≠ DTO)")
    void responseContainsNoSensitiveData() {
        User user = givenUserWithAccount("safe@example.com");

        MoneyOperationResponse depositResponse =
                moneyService.deposit(user.getId(), new BigDecimal("10"), null);
        MoneyOperationResponse withdrawResponse =
                moneyService.withdraw(user.getId(), new BigDecimal("5"), null);

        for (MoneyOperationResponse response : new MoneyOperationResponse[]{
                depositResponse, withdrawResponse}) {
            assertThat(response.toString())
                    .doesNotContain("password")
                    .doesNotContain("passwordHash")
                    .doesNotContain("$2a$")
                    .doesNotContain("email")
                    .contains("RUB");
        }
    }
}