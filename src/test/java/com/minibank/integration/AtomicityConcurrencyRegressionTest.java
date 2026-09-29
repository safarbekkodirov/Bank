package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;
import com.minibank.service.MoneyService;
import com.minibank.service.TransferService;
import com.minibank.dto.transaction.TransferRequest;

/**
 * ==========================================================================
 *  ATOMICITY + CONCURRENCY REGRESSION — PHASE 9 (11, 12, 34, 35)
 * ==========================================================================
 *
 *  PHASE 5/6-de concurrency we atomicity testleri BAR. Bu faza
 *  olary gaýtadan IŞLEDÝÄRIS — sebäbi "işleýärdi" diýen bir
 *  žagran goraganlygy ýok: ýarym ýyl soň başga bir adam
 *  TransferService-e bir setir ýazsa, eski testler ýene-de
 *  ýaşyl bolardy (olar öz kombinasýasyny barlaýar).
 *
 *  🧡 BU FAZANYŇ TÄZEŞI: "EKSISTIRLEÝÄRMI?" däl-de,
 *  "HEMIŞE dogrymy?" diýe gaýtadan synaýarys.
 */
@SpringBootTest
class AtomicityConcurrencyRegressionTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MoneyService moneyService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "Password123!";

    private User userA;
    private User userB;
    private Long accountIdA;
    private Long accountIdB;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        userA = userRepository.save(new User(
                "Ali", "a@x.test", passwordEncoder.encode(PASSWORD), Role.USER));
        userB = userRepository.save(new User(
                "Bekir", "b@x.test", passwordEncoder.encode(PASSWORD), Role.USER));

        accountIdA = givenAccount(userA).getId();
        accountIdB = givenAccount(userB).getId();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Account givenAccount(User user) {
        return accountRepository.save(new Account(
                user, "4081781" + String.format("%010d", user.getId()),
                Account.RUB, BigDecimal.ZERO.setScale(4)));
    }

    private String numberOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getAccountNumber();
    }

    // ==================================================================
    //  1) ROLLBACK (34-njy bend) — transaction record ýARIM KALMASIN
    // ==================================================================

    @Test
    @DisplayName("ROLLBACK 1: transfer ýarym ýagdaý -> 4 ýazwyň HEMMESI gaýtarýar")
    void transferRollsBackAllFourWritesOnFailure() {
        // 🔴 12-njy BEND: transfer = 4 ýazuw:
        //       1. source balans -
        //       2. destination balans +
        //       3. source žurnaly (TRANSFER_OUT)
        //       4. destination žurnaly (TRANSFER_IN)
        //    DIŇE 4-süniň hemmesi ýazylyp bilner. "Ýarym transfer"
        //    (akça gitdi-de, žurnal ýok) bank üçin YALŇYŞ.
        //
        //    🧡 FAILURE NÄHILI DÖREDILÝÄR? (34-njy bend)
        //    "Production code-a aýratyn failure hook KOŞMA" diýip
        //    berildi. Şonuň üçin biziň ýeke ýolumyz - ÝAÝYŞ ÝETMEÝÄN
        //    ýagdaý döretmek: transaction ýazgysyny 0SIZ ýazmaga
        //    synag edýäris. DB CHECK (amount > 0) ýalňyş berýär.
        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findById(accountIdA).orElseThrow()
                    .credit(new BigDecimal("1000"));
        });

        long countBefore = transactionRepository.count();

        // 💥 ÝALŇYŞLY ÝAZGY: amount = 0 -> CHECK (amount > 0) ýALŇYŞ
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Account source = accountRepository.findById(accountIdA).orElseThrow();
            Account dest = accountRepository.findById(accountIdB).orElseThrow();

            BigDecimal after = source.debit(new BigDecimal("300"));
            dest.credit(new BigDecimal("300"));

            transactionRepository.save(new Transaction(
                    source, TransactionType.TRANSFER_OUT,
                    BigDecimal.ZERO.setScale(4), after, "broken"));
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // 🛡️ ÄHLI ÝAZUWLAR KAYTYLÝAR: balanslar üýtgemedi
        assertThat(balanceOf(accountIdA)).isEqualByComparingTo("1000");
        assertThat(balanceOf(accountIdB)).isEqualByComparingTo("0");
        // 🧡 žurnal goşulmady
        assertThat(transactionRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("ROLLBACK 2: transfer - 2 balans + 2 žurnal = 4 ýazuw, ÄHLISI BILEN")
    void transferIsFullyAtomic() {
        // Setup: A-da 1000
        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findById(accountIdA).orElseThrow()
                    .credit(new BigDecimal("1000"));
        });

        // Üstünlikli transfer: 2 balans + 2 žurnal
        transactionTemplate.executeWithoutResult(status -> {
            transferService.transfer(userA.getId(),
                    new TransferRequest(numberOf(accountIdB),
                            new BigDecimal("300"), "atomic"));
        });

        assertThat(balanceOf(accountIdA)).isEqualByComparingTo("700");
        assertThat(balanceOf(accountIdB)).isEqualByComparingTo("300");
        assertThat(transactionRepository.count()).isEqualTo(2);

        // 🔴 ÝETMEÝÄN transfer: A-da 700 bar, 9999 soranýarys
        //    -> InsufficientFundsException -> ÄHLISI KAYTILÝAR
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            transferService.transfer(userA.getId(),
                    new TransferRequest(numberOf(accountIdB),
                            new BigDecimal("9999"), "too much"));
        })).isInstanceOf(InsufficientFundsException.class);

        // 🛡️ ÝARYM TRANSFER ÝOK: A-da 700 (üýtgemedi), B-de 300
        assertThat(balanceOf(accountIdA)).isEqualByComparingTo("700");
        assertThat(balanceOf(accountIdB)).isEqualByComparingTo("300");
        // 🧡 we žurnal-da TÄZE ýazgy ÝOK (2 öňki galypdur)
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("ROLLBACK 3: withdraw ýetmese -> balans + žurnal üýtgemeýär")
    void withdrawRollbackKeepsLedgerConsistent() {
        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findById(accountIdA).orElseThrow()
                    .credit(new BigDecimal("50"));
        });

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            moneyService.withdraw(userA.getId(), new BigDecimal("100"), "no funds");
        })).isInstanceOf(InsufficientFundsException.class);

        // 🛡️ Balans we žurnal degmedik
        assertThat(balanceOf(accountIdA)).isEqualByComparingTo("50");
        assertThat(transactionRepository.count()).isZero();
    }

    // ==================================================================
    //  2) CONCURRENCY (11, 35-njy bender)
    // ==================================================================

    @Test
    @DisplayName("CONCURRENCY 1: 20 parallel deposit -> balans ANIQ (lost update ýOK)")
    void concurrentDepositsDoNotLoseUpdates() throws Exception {
        // 🧡 Bu - 11-njy BENDIŇ esasy barlagy. 20 thread bir
        //    wagtda 100 deposit edýär.
        //    EGER KULOPKA BOLMASYDI: her thread 0 okap, 100 ýazap
        //    gutarardy -> balans 100 ýaly galardy (20x ýitgi).
        //    DOGRY: 20 x 100 = 2000.
        final int threads = 20;
        final BigDecimal amount = new BigDecimal("100");

        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findById(accountIdA).orElseThrow()
                    .credit(BigDecimal.ZERO.setScale(4));
        });

        runConcurrently(threads, i -> transactionTemplate.executeWithoutResult(status -> {
            moneyService.deposit(userA.getId(), amount, "concurrent-" + i);
        }));

        assertThat(balanceOf(accountIdA)).isEqualByComparingTo("2000");
        assertThat(transactionRepository.count()).isEqualTo(threads);
    }

    @Test
    @DisplayName("CONCURRENCY 2: A->B we B->A parallel = DEADLOCK ÝOK")
    void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        // 🔴 11-njy BEND: PHASE 6-da "kiçik ID-den başla" kadasy
        //    şu ýagdaýy çözýär. Bu test şol kadanıň ÝAŞAMAGYNY
        //    subut edýär.
        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findById(accountIdA).orElseThrow()
                    .credit(new BigDecimal("1000"));
            accountRepository.findById(accountIdB).orElseThrow()
                    .credit(new BigDecimal("1000"));
        });

        final int pairs = 15;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < pairs; i++) {
            // A -> B
            futures.add(pool.submit(() -> {
                try {
                    gate.await();
                    transferService.transfer(userA.getId(),
                            new TransferRequest(numberOf(accountIdB),
                                    new BigDecimal("10"), "a2b"));
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }));
            // B -> A
            futures.add(pool.submit(() -> {
                try {
                    gate.await();
                    transferService.transfer(userB.getId(),
                            new TransferRequest(numberOf(accountIdA),
                                    new BigDecimal("10"), "b2a"));
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }));
        }

        gate.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 🧡 0 ýalňyş: deadlock bolmady, timeout bolmady
        assertThat(failures.get()).isZero();
        // 🧡 Akcha ÝITMEDI we artmady
        assertThat(balanceOf(accountIdA)).isEqualByComparingTo("1000");
        assertThat(balanceOf(accountIdB)).isEqualByComparingTo("1000");
        // 🧡 15 JUBARLANMA, ýöne 60 ŽURNAL: her transfer
        //    IKI ýazgy ýazýar (TRANSFER_OUT A-da, TRANSFER_IN B-de).
        //    Ýagny 15 x 2 esap x 2 ýazgy = 60. Bu dogry ýagdaý -
        //    her esabyň ÖZ žurnaly bolmaly (A-nyň çeşmesi B-nyň
        //    ýazgysynda görünMEÝÄR - ýagny 15 däl, 15 x 2).
        assertThat(transactionRepository.count()).isEqualTo(4 * pairs);
    }

    @Test
    @DisplayName("CONCURRENCY 3: parallel withdraw -> balans TERSE DÜŞMEÝÄR")
    void concurrentWithdrawsNeverGoNegative() throws Exception {
        // 🧡 10-njy BEND: balans hiç haçan terse düşmeli däl.
        //    1000 akça bar, 30 thread 100 çekmäge synag edýär.
        //    Diňe 10-sy şoşar; 20-si 409 alýar.
        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findById(accountIdA).orElseThrow()
                    .credit(new BigDecimal("1000"));
        });

        final int threads = 30;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        runConcurrently(threads, i -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    moneyService.withdraw(userA.getId(), new BigDecimal("100"), "w" + i);
                });
                succeeded.incrementAndGet();
            } catch (InsufficientFundsException e) {
                rejected.incrementAndGet();
            }
        });

        // 🛡️ 10 şoşdy, 20-i galdy
        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(20);
        // 🔴 BALANS 0 - HİÇ HAÇAN TERSE DÄL
        assertThat(balanceOf(accountIdA)).isEqualByComparingTo("0");
        // 🧡 žurnalda diňe 10 ýazgy
        assertThat(transactionRepository.count()).isEqualTo(10);
    }

    // ==================================================================
    //  KÖMEKÇILAR
    // ==================================================================

    private BigDecimal balanceOf(Long accountId) {
        return transactionTemplate.execute(status ->
                accountRepository.findById(accountId)
                        .orElseThrow().getBalance());
    }

    private void runConcurrently(int threads, java.util.function.IntConsumer task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, 8));
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                gate.await();
                task.accept(index);
                return null;
            }));
        }
        gate.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }
}
