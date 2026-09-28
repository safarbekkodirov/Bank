package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

/**
 * 🔴 ПАРАЛЛЕЛЛДҮЛҮК ТЕСТ (тапшырманын 12-бөлүмү) — эң маанилүү тест.
 *
 * 🧠 ПРОБЛЕМА (чыныгы ката):
 *     баланс = 1000. Эки адам ОДНО убакта withdraw 700 сурашты.
 *         1-адам ОКУДУ: 1000 → 1000-700 = 300 → ЖАЗДЫ
 *         2-адам ДА ОШОЛ убакта ОКУДУ: 1000 → 1000-700 = 300 → ЖАЗДЫ
 *     Жыйынтык: экөө «ийгилик», бирок баланс = 300.
 *     Ал эми 1000-700=300, кийин 700 КАЛБАЙТ -> 409 болуу керек эле.
 *     Демек: бир ийгилик + бир 409. Биздин кулопка ошонду берет.
 *
 * 🧠 ЧЕЧИМ (биздеги): findByUserIdForUpdate -> SELECT ... FOR UPDATE
 *     PostgreSQL'де бул ЖАЗУУЧУ кулопка: экинчи суроо БИРИНЧИСИ
 *     БҮТКӨНЧӨ ТОКТОЙТ. Бүткөндөн кийин АЛ ТАЗА ОКУЙТ — 300 көрөт,
 *     демек 700 > 300 -> InsufficientFundsException.
 *
 * 🧠 БУЛ КЛАСС @Transactional ЭМЕС — ЭЧКАДА!
 *     Себеби: эгер тесттин өзү транзакцияда болсо, ар бир желе
 *     ОШО транзакциянын ичинде чургап, биз эч качан чындык
 *     «эки башка байланыш» абалын көрбөйбүз. Бизге ар бир
 *     желе ӨЗ транзакциясын, ӨЗ байланышын ачуу керек —
 *     чыныгы эки колдонуучунун жашоосуна окшош.
 *
 * 🧠 ДЕТЕРМИНИЗМ (биз күткөн натыйжа КАЙСЫ ЧЫГАРЫНА
 *     карабай ТАСТЫМДЫ):
 *     Кайсы желе биринчи болушуна карабай ЖЫЙЫНТЫ БИР:
 *         • 700 <= 1000 -> бирөө ИЙГИЛИК
 *         • 700 > 300   -> экинчиси 409
 *     Демек «бир ийгилик + бир 409 + баланс 300» — БАРДЫК
 *     иреттүүлөрдө чыныгы. Биз ушул жыйынтыкты талап кылабыз.
 */
@SpringBootTest
class MoneyOperationConcurrencyTest {

    /** Тест күткөн өткөн убакыт. Ашыкча күтпөйбүз — тест жай кетсе,
     *  «түгөнбөй калды» деп чыныгы катаны көрсөтөбүз. */
    private static final long TIMEOUT_SECONDS = 20;

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

    /**
     * 🧠 Эсеби АЧЫЛГАН, бирок БОШ (0.0000) колдонуучу.
     *
     * 🟡 ЭҢ КӨП ЖАЙ СЫНОО БУЛ ЖЕРДЕ: башка бардык жардамчы
     *    методдорун `givenUserWithBalance(..., "0.0000")` деп
     *    чакырыштан. БИРАК БИЗДИН ТАПШЫРМАБЫЗ БОЮНЧА amount
     *    0 БОЛУШУ МҮМКҮН ЭМЕС -> deposit 0 чакыруу ИЙГИЛИКСИЗ
     *    InvalidAmountException которет. Бул ТУУРА иштеши
     *    («0 салууга болобу?» — жок, 400). Биз «бос эсеп» керек
     *    болгондо, аны АЧЫП, көчүрүү КЫЛБАЙ калдырыбыз.
     */
    private User givenUserWithEmptyAccount(String email) {
        User user = userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode("Password123!"), Role.USER));
        accountService.createAccount(user.getId());
        return user;
    }

    private Long accountIdOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getId();
    }

    private BigDecimal balanceInDatabase(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    private List<Transaction> ledgerOf(User user) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountIdOf(user));
    }

    /**
     * 🧠 ОДНО УБАКТА иштеген N желе — «кайсы биринчи» тапшырманын
     *    ыйык эмес. Ошондуктан биз аларды «бирдей учурда» үстүнөн
     *    СУРОБҮЛҮҢ менен боштотобуз: CountDownLatch деген ар бир желе
     *    «мен даярмын» деп турганда, биздин эки «старт!» деген
     *    жолу аларды БИРГЕ өсүртөт. Биз «күт» деген сөзбүлбөйт —
     *    биз «башта» деген белгини күтөбүз.
     */
    private List<Future<String>> runConcurrently(int threadCount,
                                                  java.util.function.IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(pool.submit((Callable<String>) () -> {
                ready.countDown();      // «мен даярмын»
                start.await(10, TimeUnit.SECONDS);   // «старт!» күтүү
                try {
                    task.accept(index);
                    return "OK";
                } catch (InsufficientFundsException ex) {
                    return "INSUFFICIENT_FUNDS";
                } catch (Exception ex) {
                    return "ERROR: " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
                }
            }));
        }

        // 🧡 Бардыгы даяр болгонду күт, кийин БААРЫН бирге өсүрт.
        assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("Бардык желе даяр болду").isTrue();
        start.countDown();

        for (Future<String> future : futures) {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return futures;
    }

    private List<String> outcomesOf(List<Future<String>> futures) throws Exception {
        return futures.stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    // ==================== 1. НЕГИЗГИ КЕЙИН ====================

    @Test
    @DisplayName("1. 🔴 withdraw 700 + withdraw 700 (баланс 1000) -> 1 ийгилик + 1 x 409")
    void concurrentWithdrawsNeverMakeBalanceNegative() throws Exception {
        User user = givenUserWithBalance("concurrent-700@example.com", "1000.0000");

        // 🔴 Тапшырманын чыныгы кейи: 1000 баланс, эки параллель 700.
        List<String> outcomes = outcomesOf(runConcurrently(2, index ->
                moneyService.withdraw(user.getId(), new BigDecimal("700.0000"), "race-" + index)));

        // 🧡 ДЕТЕРМИНИЗМ: «кайсы желе ушул» — тапшырманын ыйык эмес.
        //    БИРАК «бир ийгилик + бир 409» — АЛДАГЫДАЙ өткөн иреттин
        //    БААРЫНДА чыныгы. Демек бул тест туруктуу (flaky ЭМЕС).
        assertThat(outcomes)
                .as("Бир ийгилик, бир InsufficientFunds (башка ката ЖОК)")
                .containsExactlyInAnyOrder("OK", "INSUFFICIENT_FUNDS");

        // 🔴 ЭҢ МААНИЛҮҮ ТЕКШЕРҮҮ: баланс 1000-700=300. -400 ЭМЕС.
        assertThat(balanceInDatabase(user)).isEqualByComparingTo("300.0000");

        // 🔴 БАЛАНС ТЕРС БОЛУШТУГУН ТЕКШЕРҮҮ (негизги талап).
        assertThat(balanceInDatabase(user).signum()).isNotNegative();
    }

    @Test
    @DisplayName("2. 🧡 Ийгилик болгон WITHDRAW -> 1 гана журнал жазуусу")
    void onlySuccessfulWithdrawCreatesOneTransaction() throws Exception {
        User user = givenUserWithBalance("concurrent-ledger@example.com", "1000.0000");

        runConcurrently(2, index ->
                moneyService.withdraw(user.getId(), new BigDecimal("700.0000"), "race-" + index));

        List<Transaction> ledger = ledgerOf(user);

        // 🧡 БААРЫ: 1 DEPOSIT (көчүрүү, 1000) + 1 WITHDRAW (ийгилик).
        //    ЭКИНЧИ WITHDRAW ЖАЗЫЛБАЙТ — ал 409 болгон, демек
        //    ката -> РОЛЛБЭК -> жазуу ЖОК. «14-бөлүм»: бир ийгилик
        //    = бир жазуу, эмес эки жазуу.
        assertThat(ledger).hasSize(2);

        List<Transaction> withdraws = ledger.stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAW)
                .toList();
        assertThat(withdraws).hasSize(1);

        Transaction withdraw = withdraws.get(0);
        assertThat(withdraw.getAmount()).isEqualByComparingTo("700.0000");
        assertThat(withdraw.getBalanceAfter()).isEqualByComparingTo("300.0000");

        // 🧡 15-бөлүм: WITHDRAW дагы ПОЗИТИВДУУ (700, -700 ЭМЕС).
        assertThat(withdraw.getAmount().signum()).isPositive();
    }

    // ==================== 2. БАШКА КЕЙИНДЕР ====================

    @Test
    @DisplayName("3. 🔴 withdraw 800 + withdraw 700 (баланс 1000) -> бир 409, баланс ТЕРС ЭМЕС")
    void concurrentWithdrawsWithDifferentAmountsNeverGoNegative() throws Exception {
        User user = givenUserWithBalance("concurrent-mixed@example.com", "1000.0000");

        // 🧡 Бирөө 800, бирөө 700 сурапты. БИЗ КАЙСЫ ЖЕЛЕ
        //    БИРИНЧИ БОЛГОНУН БИЛБЕЙБИЗ — жана билгибиз КЕРЕК
        //    ЭМЕС. Ике жол бар:
        //      • 800 биринчи -> 1000-800 = 200 -> 700 > 200 -> 409
        //      • 700 биринчи -> 1000-700 = 300 -> 800 > 300 -> 409
        //    🔴 АЛДАГЫДАЙ ЭЧ КАЙСЫ ЖЕЛЕ УШУЛ -> бирөө 409 АЛАТ,
        //    демек экөө тең ийгилик КЫЛЫНБАЙТ.
        List<String> outcomes = outcomesOf(runConcurrently(2, index ->
                moneyService.withdraw(user.getId(),
                        new BigDecimal(index == 0 ? "800.0000" : "700.0000"),
                        "race-" + index)));

        assertThat(outcomes)
                .as("Бир ийгилик, бир InsufficientFunds")
                .containsExactlyInAnyOrder("OK", "INSUFFICIENT_FUNDS");

        // 🧡 ЧЫНГЫ ДЕТЕРМИНИСТ ТЕКШЕРҮҮ: айкылып жашылган сумманын
        //    көлөмүнөн түзүлгөн эмес — ИЙГИЛИК болгон сумманын
        //    көлөмүнөн. Демек кайсы желе жеңсе, формула бирдей иштейт.
        int winner = "OK".equals(outcomes.get(0)) ? 0 : 1;
        BigDecimal winnerAmount = new BigDecimal(winner == 0 ? "800.0000" : "700.0000");

        // 🔴 БАЛАНС 1000 − ийгилик болгон сумма. ЭЧ КАЧАН терс эмес.
        BigDecimal expected = new BigDecimal("1000.0000").subtract(winnerAmount);
        assertThat(balanceInDatabase(user)).isEqualByComparingTo(expected);
        assertThat(balanceInDatabase(user).signum()).isNotNegative();

        // 🧡 Журнал: 1 көчүрүү + 1 ийгилик = 2 (калган желе → РОЛЛБЭК).
        assertThat(ledgerOf(user)).hasSize(2);
    }

    @Test
    @DisplayName("4. 🧡 5 параллель withdraw 700 (баланс 1000) -> БИР гана ийгилик")
    void fiveConcurrentWithdrawsAllowExactlyOneSuccess() throws Exception {
        User user = givenUserWithBalance("concurrent-five@example.com", "1000.0000");

        AtomicInteger successes = new AtomicInteger();
        List<String> outcomes = outcomesOf(runConcurrently(5, index -> {
            moneyService.withdraw(user.getId(), new BigDecimal("700.0000"), "race-" + index);
            successes.incrementAndGet();
        }));

        // 🔴 5 желе, бирок 1000/700 -> БИР өткөн, 4 калды. 5 ийгилик
        //    болсо, баланс 1000 - 5x700 = -2500 болуп калмакчы эле.
        assertThat(successes.get()).isEqualTo(1);
        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("INSUFFICIENT_FUNDS"::equals).hasSize(4);

        assertThat(balanceInDatabase(user)).isEqualByComparingTo("300.0000");
        assertThat(balanceInDatabase(user).signum()).isNotNegative();

        // 🧡 Журнал: 1 көчүрүү + 1 ийгилик = 2. (4 ката = 4 РОЛЛБЭК)
        assertThat(ledgerOf(user)).hasSize(2);
    }

    // ==================== 3. ДЕПОЗИТ ТАРАПЫ ====================

    @Test
    @DisplayName("5. 🧡 10 параллель deposit 100 -> БААРЫ ийгилик, 1000.0000 (бөгөтүп калуу жок)")
    void concurrentDepositsAreNotLost() throws Exception {
        User user = givenUserWithEmptyAccount("concurrent-deposit@example.com");

        // 🔴 БУЛ ТЕСТ БАШКА ТИПТЕГИ КАТАГЫ ТЕКШЕРӨТ: «бөгөтүп калуу»
        //    (lost update). 10 желе бирге 100 салса, кулопка
        //    болгонсо -> 10 x 100 = 1000.0000. Кулопка БОЛБОГОНSO
        //    (жалгаштыруу) -> баланс 100.0000 болуп, 9 салуу
        //    ЖОГОЛУП КАЛАР ЭЛЕ. Акча «жоголгон» абал — эң жаман
        //    ката: эсеп акчасыз, журналда жазуу бар.
        List<String> outcomes = outcomesOf(runConcurrently(10, index ->
                moneyService.deposit(user.getId(), new BigDecimal("100.0000"), "race-" + index)));

        assertThat(outcomes).as("Бардык 10 салуу ийгилик болду")
                .doesNotContain("ERROR: null");

        assertThat(balanceInDatabase(user)).isEqualByComparingTo("1000.0000");

        // 🧡 10 салуу = 10 жазуу. Жана «жоголгон» жазуу ЖОК.
        assertThat(ledgerOf(user)).hasSize(10);
    }

    // ==================== 4. КУЛОПКАНЫН ӨЗҮ ====================

    @Test
    @DisplayName("6. 🛡️ 10 аралаш операция -> эч ката жок, баланс ТЕРС ЭМЕС, «жоголгон» жазуу ЖОК")
    void manyConcurrentOperationsAllSucceedOrFailCleanly() throws Exception {
        User user = givenUserWithBalance("concurrent-mixed-ops@example.com", "1000.0000");

        // 🧡 Жатыш: 5 салуу (+100) жана 5 чыгаруу (-150).
        //    Чыгаруунун 5и БААРЫ ийгилик болбошү МҮМКҮН (эгер
        //    салуулар кечикип үлгөнсө). Ошондуктан биз ЖАЛГЫН
        //    натыйжаны талап кылбайбыз — биз ТЕРС БОЛУШТУГУн
        //    жана «эч нерсе жоголгонду» текшеребиз.
        List<String> outcomes = outcomesOf(runConcurrently(10, index -> {
            if (index % 2 == 0) {
                moneyService.deposit(user.getId(), new BigDecimal("100.0000"), "dep-" + index);
            } else {
                moneyService.withdraw(user.getId(), new BigDecimal("150.0000"), "wd-" + index);
            }
        }));

        // 🧡 Күтпөгөн ката (deadlock, кулопка убакыттын өтүшү) ЖОК.
        assertThat(outcomes).noneMatch(o -> o.startsWith("ERROR"));

        // 🔴 ГЛАВНОЕ: эсеп ТЕРС БОЛУШТУГУН ЖОК.
        BigDecimal finalBalance = balanceInDatabase(user);
        assertThat(finalBalance.signum()).isNotNegative();

        // 🧡 «Бөгөтүп калуу» жок: жазуулардын саны = ийгиликтер.
        long successfulWithdrawals = ledgerOf(user).stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAW).count();
        long successfulDeposits = ledgerOf(user).stream()
                .filter(t -> t.getType() == TransactionType.DEPOSIT).count() - 1; // -1 = көчүрүү

        // 🧡 Жана математика: 1000 + салуулар*100 − чыгаруулар*150.
        BigDecimal expected = new BigDecimal("1000.0000")
                .add(BigDecimal.valueOf(successfulDeposits * 100L))
                .subtract(BigDecimal.valueOf(successfulWithdrawals * 150L));
        assertThat(finalBalance).isEqualByComparingTo(expected);
    }
}
