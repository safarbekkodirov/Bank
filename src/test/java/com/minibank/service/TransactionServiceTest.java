package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.common.PageResponse;
import com.minibank.dto.transaction.TransactionFilter;
import com.minibank.dto.transaction.TransactionResponse;
import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InvalidFilterException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

/**
 * SERVICE TESTLERI - transaction history biziňServiceimiz.
 *
 *  BU TEST REAL POSTGRESQL BILEN IŞLEÝÄR.
 *     @SpringBootTest -> Spring konteksti doly göterilýär,
 *     çig mal bazasyna (PostgreSQL/Docker) birleşýäris.
 *     Ýalňyş raýatlary barlamak üçin H2 ýaly ýalňyş bazany
 *     ULANMAÝARYS: H2-de PostgreSQL dialecti we CHECK kadalary
 *     başgaça işleýär, şonuň üçin öwrenilen ýalňyş ýok.
 *
 *  @Transactional - testiň dowamynda bitewi sazlanýar. Eger test
 *     näbelli ýalňyş ýüze çykarsa, ähli ýazgylar YZyna dolandyrylýar
 *     (rollback) - ýagny testler birbirine GAPMAÝAR.
 */
@SpringBootTest
@Transactional
class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void cleanTables() {
        // 🧡 Tertip MÖHÜM: transactions -> accounts -> users.
        //    Ýokarydan aşak aýyrmak zerur: esabiň ýazgysy bolmaly
        //    ýokaryda (transactions) bolmasa, esabi aýyrmak gadagan.
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- KÖMEKÇILER ----------

    private User givenUser(String email) {
        return userRepository.save(new User(
                "Test " + email, email, passwordEncoder.encode("Password123!"),
                Role.USER));
    }

    private Account givenAccountFor(User user) {
        return accountRepository.save(new Account(
                user, "40817810" + String.format("%010d", user.getId()),
                Account.RUB, BigDecimal.ZERO.setScale(4)));
    }

    /** Bir öýdügi el bilen döredýäris - göni repository arkaly. */
    private Transaction givenTransaction(Account account, TransactionType type,
                                        String amount, String balanceAfter,
                                        String description, Instant createdAt) {
        Transaction transaction = new Transaction(
                account, type, new BigDecimal(amount),
                new BigDecimal(balanceAfter), description);
        Transaction saved = transactionRepository.save(transaction);
        // 🧡 created_at-i el bilen goýýarys (test üçin gerek ýok bolsa
        //    yza süýşýär - ýöne wagty saýlamak gerek bolan ýagdaýda).
        if (createdAt != null) {
            // 🔴 MAÝLY YERI: JPQL @Modifying UPDATE bilen wagty
            //    üýtgedýäris, soňra EntityManager-iň kachesini
            //    ARASSALAMALY (clear). Sebäbi: JPQL UPDATE bir
            //    "toplum" hereket - Hibernate ol ýazgynyň obýegini
            //    öz kachesinde TÄZEDELEÝÄR diýip bilmeýär.
            //    Eger arassalamasak, indiki SELECT ýatdaş ýazgy
            //    gaýtaryp berýär (stale read) - test ýalňyş
            //    netije görýär. Bu - "ýazuw-yzarlan" düzgüniniň
            //    hökmany hökmi: her toplum ýazuwdan soň clear().
            transactionRepository.updateCreatedAt(saved.getId(), createdAt);
            transactionRepository.flush();
            entityManager.clear();
        }
        return saved;
    }

    /** Ýumşak filter - ähli parametrlar boş (default page 0, size 20). */
    private TransactionFilter noFilter() {
        return new TransactionFilter(null, null, null, null, null, null, null);
    }

    private PageResponse<TransactionResponse> historyOf(User user) {
        return transactionService.getMyTransactions(user.getId(), noFilter());
    }

    // ---------- 1. ÖZ TARHYHY ----------

    @Test
    @DisplayName("1. kollonuwçynyň öz taryhy gaýtarylyp")
    void ownTransactionHistoryIsReturned() {
        User user = givenUser("own@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "500", "500", "seed", null);

        PageResponse<TransactionResponse> result = historyOf(user);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).type()).isEqualTo("DEPOSIT");
        assertThat(result.content().get(0).amount()).isEqualByComparingTo("500.0000");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
    }

    @Test
    @DisplayName("2. boş taryh - 200, content bos, KATA däl")
    void emptyHistoryReturnsEmptyPage() {
        User user = givenUser("empty@example.com");
        givenAccountFor(user);

        PageResponse<TransactionResponse> result = historyOf(user);

        // 🧡 Bu KATA DÄL - bu dogry netije (task 13).
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
    }

    // ---------- 2. KARTLAMAK (MAPPING) ----------

    @Test
    @DisplayName("3. ähli meýdanlar dogry göçürilýär (null description hem)")
    void mappingIsCorrectIncludingNullDescription() {
        User user = givenUser("map@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.WITHDRAW, "120.5000", "380.0000",
                "rent", Instant.parse("2026-01-15T10:30:00Z"));
        givenTransaction(account, TransactionType.DEPOSIT, "100", "500", null, null);

        PageResponse<TransactionResponse> result = historyOf(user);

        assertThat(result.content()).hasSize(2);
        // iň täze birinji
        TransactionResponse newest = result.content().get(0);
        assertThat(newest.type()).isEqualTo("DEPOSIT");
        assertThat(newest.amount()).isEqualByComparingTo("100");
        assertThat(newest.balanceAfter()).isEqualByComparingTo("500");
        assertThat(newest.description()).isNull();

        TransactionResponse older = result.content().get(1);
        assertThat(older.id()).isNotNull();
        assertThat(older.type()).isEqualTo("WITHDRAW");
        assertThat(older.description()).isEqualTo("rent");
        assertThat(older.createdAt()).isEqualTo(Instant.parse("2026-01-15T10:30:00Z"));
        assertThat(older.amount()).isEqualByComparingTo("120.5000");
        assertThat(older.balanceAfter()).isEqualByComparingTo("380");
    }

    @Test
    @DisplayName("4. DTO-da parol ýa-da user obýegi ýok")
    void responseHasNoInternalData() {
        User user = givenUser("secure@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "10", "10", "d", null);

        // 🧡 DTO-da Entity ýok - diňe primitiwler we String.
        //    Bu test şeýle ýagdaýy goraýar: kime bir gün getAccount()
        //    goýsa - bu test ÇALÝAR we ýalňyşy saklaýar.
        TransactionResponse response = historyOf(user).content().get(0);
        assertThat(response).hasOnlyFields(
                "id", "type", "amount", "balanceAfter", "description", "createdAt");
    }

    // ---------- 3. ESAP ÝOGSA ----------

    @Test
    @DisplayName("5. esaby ýok bolmaly bolmasa -> 404 (AccountNotFoundException)")
    void accountNotFoundThrows() {
        User user = givenUser("noaccount@example.com");
        // esap AÇMADYK

        assertThatThrownBy(() -> historyOf(user))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("No bank account found");
    }

    // ---------- 4. SORTLAMAK ----------

    @Test
    @DisplayName("6. iň täze ýazgy BIRINJI (createdAt DESC)")
    void newestTransactionComesFirst() {
        User user = givenUser("sort@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "1", "1", "1st",
                Instant.parse("2026-01-01T00:00:00Z"));
        givenTransaction(account, TransactionType.DEPOSIT, "2", "3", "2nd",
                Instant.parse("2026-06-01T00:00:00Z"));
        givenTransaction(account, TransactionType.DEPOSIT, "3", "6", "3rd",
                Instant.parse("2026-12-31T00:00:00Z"));

        List<TransactionResponse> content = historyOf(user).content();

        assertThat(content).extracting(TransactionResponse::description)
                .containsExactly("3rd", "2nd", "1st");
    }

    @Test
    @DisplayName("7. birmeňzeş wagt -> id DESC (dETERMINISTIK, tertip bozulmaýar)")
    void sameTimestampIsSortedByIdDesc() {
        User user = givenUser("stable@example.com");
        Account account = givenAccountFor(user);
        // ÜÇ SANY BIRMEŇEŞ WAGT - bu ýagdaý tertibi döredip ýok eder.
        Instant same = Instant.parse("2026-05-05T05:05:05Z");
        givenTransaction(account, TransactionType.DEPOSIT, "10", "10", "a", same);
        givenTransaction(account, TransactionType.DEPOSIT, "20", "30", "b", same);
        givenTransaction(account, TransactionType.DEPOSIT, "30", "60", "c", same);

        List<TransactionResponse> content = historyOf(user).content();

        // id DESC -> iň ýokary id birinji -> "c", "b", "a"
        assertThat(content).extracting(TransactionResponse::description)
                .containsExactly("c", "b", "a");
        // 🧡 we tertip DAVAM EDÝÄR: her bir gezek gaýtadan soragda
        //    şeýle bir tertip gelýär (id ýok bolsa - tertip üýtgeýärdi).
        assertThat(historyOf(user).content()).extracting(TransactionResponse::description)
                .containsExactly("c", "b", "a");
    }

    // ---------- 5. PAGINATION ----------

    @Test
    @DisplayName("8. pagination 45 ýazgy: 20 / 20 / 5, totalPages=3")
    void paginationSplitsHistory() {
        User user = givenUser("page@example.com");
        Account account = givenAccountFor(user);
        for (int i = 0; i < 45; i++) {
            givenTransaction(account, TransactionType.DEPOSIT,
                    "1", String.valueOf(i + 1), "tx" + i,
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        }

        PageResponse<TransactionResponse> p0 = filter(user, 0, 20);
        PageResponse<TransactionResponse> p1 = filter(user, 1, 20);
        PageResponse<TransactionResponse> p2 = filter(user, 2, 20);

        assertThat(p0.content()).hasSize(20);
        assertThat(p1.content()).hasSize(20);
        assertThat(p2.content()).hasSize(5);

        assertThat(p0.totalElements()).isEqualTo(45);
        assertThat(p0.totalPages()).isEqualTo(3);

        assertThat(p0.first()).isTrue();
        assertThat(p0.last()).isFalse();
        assertThat(p1.first()).isFalse();
        assertThat(p1.last()).isFalse();
        assertThat(p2.first()).isFalse();
        assertThat(p2.last()).isTrue();
    }

    @Test
    @DisplayName("9. bir ýazgy IKI sahypada GAYTALANMAÝAR")
    void paginationDoesNotRepeatOrSkipRecords() {
        User user = givenUser("nodup@example.com");
        Account account = givenAccountFor(user);
        for (int i = 0; i < 45; i++) {
            givenTransaction(account, TransactionType.DEPOSIT, "1",
                    String.valueOf(i + 1), "tx" + i,
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        }

        List<Long> ids = new java.util.ArrayList<>();
        for (int p = 0; p < 3; p++) {
            filter(user, p, 20).content().forEach(r -> ids.add(r.id()));
        }

        // 🧡 Ýarym ýagdaý ("кайда 20 ýazgy?") barlanýar:
        //    hemmesi 45 UNIQUE bolmaly, gaýtalanma ýok.
        assertThat(ids).hasSize(45).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("10. soňky sahypadan ýokary sahypa -> boş, ýalňyş däl")
    void pageBeyondTotalReturnsEmptyContent() {
        User user = givenUser("beyond@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "5", "5", "one", null);

        PageResponse<TransactionResponse> result = filter(user, 99, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    // ---------- 6. TYPE FILTER ----------

    @Test
    @DisplayName("11. type=DEPOSIT -> diňe DEPOSIT")
    void typeFilterReturnsOnlyThatType() {
        User user = givenUser("type@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "10", "10", "d", null);
        givenTransaction(account, TransactionType.WITHDRAW, "10", "10", "w", null);
        givenTransaction(account, TransactionType.DEPOSIT, "10", "20", "d2", null);

        PageResponse<TransactionResponse> result = transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, null, "DEPOSIT",
                        null, null, null, null));

        assertThat(result.content()).extracting(TransactionResponse::type)
                .containsOnly("DEPOSIT");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("12. nädogry type -> 400 (InvalidFilterException)")
    void invalidTypeIsRejected() {
        User user = givenUser("badtype@example.com");
        givenAccountFor(user);

        assertThatThrownBy(() -> transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, null, "INVALID",
                        null, null, null, null)))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("Invalid transaction type");
    }

    // ---------- 7. VALIDATION ----------

    @Test
    @DisplayName("13. page=-1 -> 400")
    void negativePageIsRejected() {
        User user = givenUser("negpage@example.com");
        givenAccountFor(user);

        assertThatThrownBy(() -> transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(-1, null, null,
                        null, null, null, null)))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("page");
    }

    @Test
    @DisplayName("14. size=0 we size=101 -> 400")
    void invalidSizeIsRejected() {
        User user = givenUser("badsize@example.com");
        givenAccountFor(user);

        assertThatThrownBy(() -> transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, 0, null,
                        null, null, null, null)))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("at least 1");

        assertThatThrownBy(() -> transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, 101, null,
                        null, null, null, null)))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("15. from > to -> 400")
    void fromAfterToIsRejected() {
        User user = givenUser("baddate@example.com");
        givenAccountFor(user);

        assertThatThrownBy(() -> transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, null, null,
                        Instant.parse("2026-10-01T00:00:00Z"),
                        Instant.parse("2026-09-01T00:00:00Z"),
                        null, null)))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("from must be before");
    }

    // ---------- 8. WAGT FILTERI ----------

    @Test
    @DisplayName("16. from filter - diňe soňky ýazgylar")
    void fromFilterReturnsRecentOnly() {
        User user = givenUser("from@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "1", "1", "old",
                Instant.parse("2026-01-01T00:00:00Z"));
        givenTransaction(account, TransactionType.DEPOSIT, "2", "3", "mid",
                Instant.parse("2026-06-15T00:00:00Z"));
        givenTransaction(account, TransactionType.DEPOSIT, "3", "6", "new",
                Instant.parse("2026-12-01T00:00:00Z"));

        PageResponse<TransactionResponse> result = transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, null, null,
                        Instant.parse("2026-06-01T00:00:00Z"), null, null, null));

        assertThat(result.content()).extracting(TransactionResponse::description)
                .containsExactly("new", "mid");
    }

    @Test
    @DisplayName("17. to filter - diňe öňki ýazgylar")
    void toFilterReturnsOldOnly() {
        User user = givenUser("to@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "1", "1", "old",
                Instant.parse("2026-01-01T00:00:00Z"));
        givenTransaction(account, TransactionType.DEPOSIT, "3", "6", "new",
                Instant.parse("2026-12-01T00:00:00Z"));

        PageResponse<TransactionResponse> result = transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, null, null,
                        null, Instant.parse("2026-06-01T00:00:00Z"), null, null));

        assertThat(result.content()).extracting(TransactionResponse::description)
                .containsExactly("old");
    }

    @Test
    @DisplayName("18. from + to -> aralykdaky ýazgylar")
    void fromAndToFiltersCombine() {
        User user = givenUser("both@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "1", "1", "jan",
                Instant.parse("2026-01-01T00:00:00Z"));
        givenTransaction(account, TransactionType.DEPOSIT, "2", "3", "sep",
                Instant.parse("2026-09-15T00:00:00Z"));
        givenTransaction(account, TransactionType.DEPOSIT, "3", "6", "nov",
                Instant.parse("2026-11-01T00:00:00Z"));

        PageResponse<TransactionResponse> result = transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, null, null,
                        Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-10-01T00:00:00Z"),
                        null, null));

        assertThat(result.content()).extracting(TransactionResponse::description)
                .containsExactly("sep");
    }

    // ---------- 9. SUMA FILTERI ----------

    @Test
    @DisplayName("19. minAmount / maxAmount - aralyk")
    void amountFiltersReturnRange() {
        User user = givenUser("amount@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "50", "50", "small", null);
        givenTransaction(account, TransactionType.DEPOSIT, "500", "550", "mid", null);
        givenTransaction(account, TransactionType.DEPOSIT, "5000", "5550", "big", null);

        PageResponse<TransactionResponse> result = transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, null, null, null, null,
                        new BigDecimal("100"), new BigDecimal("1000")));

        assertThat(result.content()).extracting(TransactionResponse::description)
                .containsExactly("mid");
    }

    @Test
    @DisplayName("20. minAmount > maxAmount -> 400")
    void minGreaterThanMaxIsRejected() {
        User user = givenUser("badamount@example.com");
        givenAccountFor(user);

        assertThatThrownBy(() -> transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(null, null, null, null, null,
                        new BigDecimal("1000"), new BigDecimal("100"))))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("minAmount");
    }

    // ---------- 10. HOWPSUZLYK (IDOR) ----------

    @Test
    @DisplayName("21. User A diňe ÖZ taryhyny görýär")
    void userASeesOnlyOwnTransactions() {
        User a = givenUser("a@example.com");
        User b = givenUser("b@example.com");
        Account accountA = givenAccountFor(a);
        Account accountB = givenAccountFor(b);

        givenTransaction(accountA, TransactionType.DEPOSIT, "100", "100", "A-money", null);
        givenTransaction(accountB, TransactionType.DEPOSIT, "999", "999", "B-money", null);

        PageResponse<TransactionResponse> aView = historyOf(a);

        assertThat(aView.content()).hasSize(1);
        assertThat(aView.content().get(0).description()).isEqualTo("A-money");
        // 🧡 B-niň maglumaty HİÇ NEREDE ýok:
        assertThat(aView.content()).extracting(TransactionResponse::description)
                .doesNotContain("B-money");
    }

    @Test
    @DisplayName("22. User B diňe ÖZ taryhyny görýär (iki tarap hem)")
    void userBSeesOnlyOwnTransactions() {
        User a = givenUser("a2@example.com");
        User b = givenUser("b2@example.com");
        Account accountA = givenAccountFor(a);
        Account accountB = givenAccountFor(b);

        givenTransaction(accountA, TransactionType.DEPOSIT, "100", "100", "A-money", null);
        givenTransaction(accountB, TransactionType.DEPOSIT, "999", "999", "B-money", null);

        PageResponse<TransactionResponse> bView = historyOf(b);

        assertThat(bView.content()).hasSize(1);
        assertThat(bView.content().get(0).description()).isEqualTo("B-money");
    }

    @Test
    @DisplayName("23. iki kollonuwçynyň umumy sany ýok (hasap garyşmaýar)")
    void totalsDoNotMixBetweenUsers() {
        User a = givenUser("a3@example.com");
        User b = givenUser("b3@example.com");
        Account accountA = givenAccountFor(a);
        Account accountB = givenAccountFor(b);

        for (int i = 0; i < 3; i++) {
            givenTransaction(accountA, TransactionType.DEPOSIT, "1",
                    String.valueOf(i + 1), "A" + i, null);
        }
        for (int i = 0; i < 7; i++) {
            givenTransaction(accountB, TransactionType.DEPOSIT, "1",
                    String.valueOf(i + 1), "B" + i, null);
        }

        assertThat(historyOf(a).totalElements()).isEqualTo(3);
        assertThat(historyOf(b).totalElements()).isEqualTo(7);
    }

    // ---------- 11. PHASE 5/6 YZYLYLYGY ----------

    @Test
    @DisplayName("24. PHASE 5/6 ýazgylary dogry tertipde we balanceAfter bazadaky bilen deň")
    void historyShowsRealPhase5And6Operations() {
        User user = givenUser("consist@example.com");
        Account account = givenAccountFor(user);

        // emeli tertip: deposit -> withdraw -> transfer_in
        givenTransaction(account, TransactionType.DEPOSIT, "1000", "1000",
                "salem", Instant.parse("2026-01-01T00:00:00Z"));
        givenTransaction(account, TransactionType.WITHDRAW, "100", "900",
                "al", Instant.parse("2026-01-02T00:00:00Z"));
        givenTransaction(account, TransactionType.TRANSFER_IN, "50", "950",
                "geldi", Instant.parse("2026-01-03T00:00:00Z"));

        List<TransactionResponse> content = historyOf(user).content();

        // 🧡 NEWEST FIRST: in -> out -> deposit tertibi
        assertThat(content).extracting(TransactionResponse::type)
                .containsExactly("TRANSFER_IN", "WITHDRAW", "DEPOSIT");
        // 🧡 balanceAfter - her biriniň öz balansy, BAZANYŇ GÖRNÜŞI
        //    boýunça: NUMERIC(19,4) => 4 ýaý düýdüli => "950.0000".
        //    Bu ýerde scale-iň dogry gelmegi MÖHÜM: klient tarapdan
        //    "950.0000" görkezilýär, "950" däl - bank UI-da hakyky
        //    görkezüş şeýle bolmaly. Şonuň üçin bu test scale-i
        //    hem barlaýar (göçürme Service-de ýitirilmeýär).
        assertThat(content).extracting(TransactionResponse::balanceAfter)
                .containsExactly(
                        new BigDecimal("950.0000"),
                        new BigDecimal("900.0000"),
                        new BigDecimal("1000.0000"));
    }

    @Test
    @DisplayName("25. ýazgy özgerdirilmeýär (read-only) - görkezmek ýazma däl")
    void historyDoesNotModifyData() {
        User user = givenUser("readonly@example.com");
        Account account = givenAccountFor(user);
        givenTransaction(account, TransactionType.DEPOSIT, "42", "42", "d", null);
        long before = transactionRepository.count();
        String balanceBefore = account.getBalance().toPlainString();

        historyOf(user);
        historyOf(user);
        historyOf(user);

        // 🧡 3 gezek okadýarys - ýazgy sany we balans üýtgemeli däl.
        assertThat(transactionRepository.count()).isEqualTo(before);
        assertThat(accountRepository.findById(account.getId()).orElseThrow()
                .getBalance().toPlainString()).isEqualTo(balanceBefore);
    }

    // ---------- KÖMEKÇI ----------

    private PageResponse<TransactionResponse> filter(User user, int page, int size) {
        return transactionService.getMyTransactions(
                user.getId(), new TransactionFilter(page, size, null,
                        null, null, null, null));
    }
}
