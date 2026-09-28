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
import java.util.function.IntConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.minibank.dto.transaction.TransferRequest;
import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

/**
 * 🔴 TRANSFER ПАРАЛЛЕЛЛДҮЛҮК ТЕСТИ (тапшырманын 26-бөлүмү) — DEADLOCK.
 *
 * 🧠 БҮЛ КЛАСС @Transactional ЭМЕС — ЭЧКАДА! Себеби: эгер тесттин
 *     өзү транзакцияда болсо, ар бир желе ОШО транзакциянын ичинде
 *     чургап, биз эч качан чындык «эки башка байланыш» абалын
 *     көрбөйбүз. Бизге ар бир желе ӨЗ транзакциясын, ӨЗ
 *     байланышын ачуу керек — чыныгы эки колдонуучунун жашоосуна
 *     окшош.
 *
 * 🧡 27-БӨЛҮМДӨГҮ ЭҢ МААНИЛҮҮ ТАРТИП:
 *     «Кайсы request биринчи бүтүшү маанилүү ЭМЕС». Биз
 *     талап кылган нерсе — ИНВАРИАНТТАР:
 *         • deadlock ЖОК
 *         • баланс бузулбайт (коррупция жок)
 *         • жалпы сумма сакталат
 *         • ийгиликтерге туура жазуулар
 *     Биз ЭЧ КАЧАН «A 300 берди, B 200 берди» деп жазбайбыз —
 *     ал убакытка байланыштуу жана flaky болуп чыгат.
 */
@SpringBootTest
class TransferConcurrencyTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private MoneyService moneyService;

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

    private BigDecimal balanceInDatabase(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    private String numberOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getAccountNumber();
    }

    private long countOf(User user, TransactionType type) {
        Long accountId = accountRepository.findByUserId(user.getId()).orElseThrow().getId();
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .filter(t -> t.getType() == type)
                .count();
    }

    /**
     * 🧠 ОДНО УБАКТА иштеген N желе. CountDownLatch менен:
     *     ар бир желе «мен даярмын» деп турат, биз «старт!» деп
     *     БААРЫН бирге өсүртөбүз. Биз убакытты күтпөйт, башташ
     *     белгини күтөбүз.
     */
    private List<String> runConcurrently(int threadCount, IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            // 🧡 i'ни КОПШОК көчүрмөгө салабыз: lambda (жабык
            //    функция) ичинде «өзгөрүүчү» жаштыруучу
            //    которулбош керек (final же «чыныгы final»).
            //    Демек биз index деп өзүбүз көчүрмө жасайбыз.
            final int index = i;
            futures.add(pool.submit((Callable<String>) () -> {
                ready.countDown();
                start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

        assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("Бардык желе даяр болду").isTrue();
        start.countDown();

        List<String> outcomes = new java.util.ArrayList<>();
        for (Future<String> future : futures) {
            outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return outcomes;
    }

    // ==================== TEST A — КАРАМА-КАРШЫ БАГЫТ ====================

    @Test
    @DisplayName("1. 🔴🔴 A→B (100) + B→A (200) параллель -> DEADLOCK ЖОК, жалпы 2000")
    void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        User a = givenUserWithBalance("deadlock-a@example.com", "1000.0000");
        User b = givenUserWithBalance("deadlock-b@example.com", "1000.0000");

        String numberA = numberOf(a);
        String numberB = numberOf(b);
        BigDecimal totalBefore = balanceInDatabase(a).add(balanceInDatabase(b));
        assertThat(totalBefore).isEqualByComparingTo("2000.0000");

        // 🔴 ЭҢ КАМКОРЛУК КЕЙИН: эки суроо КАРАМА-КАРШЫ багытта,
        //    ОДНО убакта. Кулопкалар сөлөмөс болсо — 💀 DEADLOCK.
        //    Биздин ID-кичинелик тартип аны ТОКТОТУШУ керек.
        List<String> outcomes = runConcurrently(2, index -> {
            if (index == 0) {
                transferService.transfer(a.getId(),
                        new TransferRequest(numberB, new BigDecimal("100.0000"), "A->B"));
            } else {
                transferService.transfer(b.getId(),
                        new TransferRequest(numberA, new BigDecimal("200.0000"), "B->A"));
            }
        });

        // 🔴🔴 DEADLOCK болгондо PostgreSQL биринчи суроону ӨЛТҮРӨТ
        //    ("deadlock detected", SQLSTATE 40P01). Демек эгер
        //    коргоо иштесе, «ERROR: ...» ЖОК.
        assertThat(outcomes)
                .as("DEADLOCK ЖОК, InsufficientFunds да ЖОК (экөө үстүнөн жетише алат)")
                .doesNotContain("ERROR: null")
                .containsExactly("OK", "OK");

        // 🔴 ЖАЛПЫ СУММА САКТАЛДЫ (20-бөлүм): 2000 = 1000+1000
        BigDecimal totalAfter = balanceInDatabase(a).add(balanceInDatabase(b));
        assertThat(totalAfter).isEqualByComparingTo(totalBefore);

        // 🔴 БАЛАНС ТЕРС ЭМЕС.
        assertThat(balanceInDatabase(a).signum()).isNotNegative();
        assertThat(balanceInDatabase(b).signum()).isNotNegative();

        // 🧡 ЖУРНАЛДА: 2 OUT + 2 IN (эки transfer = 4 жазуу).
        assertThat(countOf(a, TransactionType.TRANSFER_OUT)
                + countOf(a, TransactionType.TRANSFER_IN)).isEqualTo(2);
        assertThat(countOf(b, TransactionType.TRANSFER_OUT)
                + countOf(b, TransactionType.TRANSFER_IN)).isEqualTo(2);
    }

    // ==================== TEST B — КӨП ПАРАЛЛЕЛЬ ====================

    @Test
    @DisplayName("2. 🔴 10 параллель transfer (аралаш багыт) -> deadlock жок, сакоот сакталат")
    void manyConcurrentTransfersPreserveInvariants() throws Exception {
        User a = givenUserWithBalance("many-a@example.com", "10000.0000");
        User b = givenUserWithBalance("many-b@example.com", "10000.0000");

        String numberA = numberOf(a);
        String numberB = numberOf(b);
        BigDecimal totalBefore = balanceInDatabase(a).add(balanceInDatabase(b));

        // 🧡 АРАЛАШ БАГЫТ: чээ 0,2,4,6,8 -> A→B; калган -> B→A.
        //    Бул «көп желени» абалын жаратат: эки эсеп тең урулуп,
        //    кулопка үчүн күтүп турат. Эгерде биз бир тараптуу
        //    кылсак, A өзүнөн өзүнө жана БАШКА бирге барат эле.
        List<String> outcomes = runConcurrently(10, index -> {
            if (index % 2 == 0) {
                transferService.transfer(a.getId(),
                        new TransferRequest(numberB, new BigDecimal("10.0000"), "A->B-" + index));
            } else {
                transferService.transfer(b.getId(),
                        new TransferRequest(numberA, new BigDecimal("10.0000"), "B->A-" + index));
            }
        });

        // 🔴🔴 ЭЧ КИМ «ERROR» ТЕКШЕРБЕЙТ — демек deadlock ЖОК.
        assertThat(outcomes).as("DEADLOCK/ката ЖОК")
                .doesNotContain("ERROR: null");

        // 🧡 10 x 10 = 100, эсептер 10000дөн тең — баары жетет.
        assertThat(outcomes).filteredOn("OK"::equals).hasSize(10);

        // 🔴 20-бөлүм: ЖАЛПЫ СУММА САКТАЛДЫ. Бул эң күчтүү
        //    инвариант: акча «түзүлбөйт», «жоголбойт».
        BigDecimal totalAfter = balanceInDatabase(a).add(balanceInDatabase(b));
        assertThat(totalAfter).isEqualByComparingTo(totalBefore);
        assertThat(totalAfter).isEqualByComparingTo("20000.0000");

        // 🔴 ТЕРС БАЛАНС ЖОК.
        assertThat(balanceInDatabase(a).signum()).isNotNegative();
        assertThat(balanceInDatabase(b).signum()).isNotNegative();

        // 🧡 ЖУРНАЛ САНЫ = ИЙГИЛИКТЕРГЕ ТААРЫ (10 transfer = 20 жазуу).
        assertThat(countOf(a, TransactionType.TRANSFER_OUT)
                + countOf(b, TransactionType.TRANSFER_OUT)).isEqualTo(10);
        assertThat(countOf(a, TransactionType.TRANSFER_IN)
                + countOf(b, TransactionType.TRANSFER_IN)).isEqualTo(10);
    }

    // ==================== 3. «БӨГӨТҮП КАЛУУ» ====================

    @Test
    @DisplayName("3. 🛡️ «Бөгөтүп калуу» ЖОК: 20x50 = 1000.0000 базага жетет")
    void concurrentTransfersDoNotLoseUpdates() throws Exception {
        User source = givenUserWithBalance("lost-src@example.com", "1000.0000");
        User sink = givenUserWithBalance("lost-snk@example.com", "1.0000");

        // 🧡 ЭСЕП БОШ ЭМЕС: 0 көчүрүү МҮМКҮН ЭМЕС (amount > 0,
        //    PHASE 5 эрежеси). Демек «бос» эсепти 1 «түп» менен
        //    ачып, анан так 0 болгонго чейин алып салабыз.
        moneyService.withdraw(sink.getId(), new BigDecimal("1.0000"), "drain");
        assertThat(balanceInDatabase(sink)).isEqualByComparingTo("0.0000");

        String numberSink = numberOf(sink);

        // 🔴 20 желе бирге 50 салат. Кулопка БОЛБОГОНSO
        //    (жалгаштыруу) -> көпчүлүк салуу «жоголуп» калмакчы
        //    эле: 20x50=1000 болушу керек, 50 болуп калуу мүмкүн.
        List<String> outcomes = runConcurrently(20, index ->
                transferService.transfer(source.getId(),
                        new TransferRequest(numberSink, new BigDecimal("50.0000"), "load-" + index)));

        assertThat(outcomes).noneMatch(o -> o.startsWith("ERROR"));

        // 🔴 БААРЫ 20 САЛУУ САКТАЛДЫ — бири да «жоголгон» эмес.
        assertThat(balanceInDatabase(sink)).isEqualByComparingTo("1000.0000");
        assertThat(balanceInDatabase(source)).isEqualByComparingTo("0.0000");
        assertThat(countOf(sink, TransactionType.TRANSFER_IN)).isEqualTo(20);
    }
}
