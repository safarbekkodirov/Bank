package com.minibank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtService;

/**
 * API TESTLERI - GET /api/accounts/me/transactions
 *
 * 🧡 MOCKMVC NÄME ÜÇIN? Chyky REST stekini sınaýar:
 *     JSON deserializasiýa, Security çyрпаkлары, @RequestParam
 *     öwürüşi, GlobalExceptionHandler, HTTP kody - hemmesi hakyky.
 *     Port açylmaýar (ýarym çözgüt däl), ýöne çig mal PostgreSQL.
 *
 * 🛡️ IŇ MÖHÜM TESTLER: howpsuzlyk bölümi (test 18-20).
 *     Bu ýerde iki kollonuwçynyň taryhynyň BIRINJILIGI
 *     barlanýar - bu PHASE 7-nyň esasy howpsuzlyk talaby.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionHistoryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private static final String PASSWORD = "Password123!";

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- KÖMEKÇILER ----------

    private User givenUser(String email) {
        return userRepository.save(new User(
                "Test " + email, email, passwordEncoder.encode(PASSWORD),
                Role.USER));
    }

    /** Esaby ARKALYKDAN açýarys (API arkaly açmak hem mümkün, emma bu ýerde gerek däl). */
    private User givenUserWithAccount(String email) {
        User user = givenUser(email);
        accountRepository.save(new com.minibank.entity.Account(
                user, "40817810" + String.format("%010d", user.getId()),
                com.minibank.entity.Account.RUB,
                BigDecimal.ZERO.setScale(4)));
        return user;
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private String accountNumberOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getAccountNumber();
    }

    private void deposit(User user, String amount, String description) throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount
                                + ", \"description\": \"" + description + "\"}"))
                .andExpect(status().isOk());
    }

    private void withdraw(User user, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + "}"))
                .andExpect(status().isOk());
    }

    private void transfer(User from, String toNumber, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(from))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toAccountNumber\": \"" + toNumber
                                + "\", \"amount\": " + amount + "}"))
                .andExpect(status().isOk());
    }

    // ---------- 1. DOGRY JOGAP ----------

    @Test
    @DisplayName("1. dogry token -> 200 we toparmagyň gurluşy")
    void validTokenReturns200() throws Exception {
        User user = givenUserWithAccount("ok@example.com");
        deposit(user, "500", "seed");

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                // 🧡 STRUKTURA: content + metadata hökmany
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[0].amount").exists())
                .andExpect(jsonPath("$.content[0].balanceAfter").exists())
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("2. ýokary default size 20 (men ýazmadym)")
    void defaultSizeIs20() throws Exception {
        User user = givenUserWithAccount("default@example.com");
        deposit(user, "100", "one");

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.page").value(0));
    }

    // ---------- 2. HOWPSUZLYK: 401 ----------

    @Test
    @DisplayName("3. token ÝOK -> 401")
    void noTokenReturns401() throws Exception {
        givenUserWithAccount("notoken@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. BUZULGAN token -> 401")
    void brokenTokenReturns401() throws Exception {
        User user = givenUserWithAccount("broken@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. basga syrr bilen imzalanan token -> 401")
    void tokenSignedWithWrongSecretReturns401() throws Exception {
        // Başga syrr - JwtService ulanýan ýerden däl
        String forged = io.jsonwebtoken.Jwts.builder()
                .subject("1")
                .claim("role", "USER")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
                .signWith(io.jsonwebtoken.security.Keys
                        .hmacShaKeyFor(("another-secret-that-is-long-enough-32-bytes-x")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6. GET /api/accounts/{id}/transactions ÝOK -> 404 (IDOR ýoly ýapyk)")
    void pathVariableEndpointDoesNotExist() throws Exception {
        User user = givenUserWithAccount("idortest@example.com");

        // 🔴 "open" ýaly /73/transactions ýazsaýdy - IDOR bolardy.
        //    Biz ýazmadyk -> 404 (endpoint ýok).
        mockMvc.perform(get("/api/accounts/73/transactions")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNotFound());
    }

    // ---------- 3. BOŞ TARHYH ----------

    @Test
    @DisplayName("7. boş taryh -> 200 we content=[] (KATA DÄL)")
    void emptyHistoryReturns200() throws Exception {
        User user = givenUserWithAccount("empty@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("8. esaby ýok bolmaly bolmasa -> 404")
    void accountNotFoundReturns404() throws Exception {
        User user = givenUser("noaccount@example.com");   // esap AÇMADYK

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNotFound());
    }

    // ---------- 4. PAGINATION ----------

    @Test
    @DisplayName("9. pagination 45 ýazgy: 20 / 20 / 5")
    void paginationOverApi() throws Exception {
        User user = givenUserWithAccount("page@example.com");
        for (int i = 0; i < 45; i++) {
            deposit(user, "1", "tx" + i);
        }

        mockMvc.perform(get("/api/accounts/me/transactions?page=0&size=20")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(45))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/accounts/me/transactions?page=1&size=20")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.first").value(false));

        mockMvc.perform(get("/api/accounts/me/transactions?page=2&size=20")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("10. size=5 -> sahypalar 5/5 ga bölünýär")
    void customSizeWorks() throws Exception {
        User user = givenUserWithAccount("size5@example.com");
        for (int i = 0; i < 12; i++) {
            deposit(user, "1", "tx" + i);
        }

        mockMvc.perform(get("/api/accounts/me/transactions?page=0&size=5")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    // ---------- 5. VALIDATION: 400 ----------

    @Test
    @DisplayName("11. page=-1 -> 400")
    void negativePageReturns400() throws Exception {
        User user = givenUserWithAccount("negpage@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions?page=-1")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("12. size=0 -> 400")
    void zeroSizeReturns400() throws Exception {
        User user = givenUserWithAccount("size0@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions?size=0")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("13. size=101 -> 400 (iň ýokary 100)")
    void tooLargeSizeReturns400() throws Exception {
        User user = givenUserWithAccount("size101@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions?size=101")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("14. nädogry type -> 400")
    void invalidTypeReturns400() throws Exception {
        User user = givenUserWithAccount("badtype@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions?type=INVALID")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("15. from > to -> 400")
    void fromAfterToReturns400() throws Exception {
        User user = givenUserWithAccount("baddates@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions"
                        + "?from=2026-10-01T00:00:00Z&to=2026-09-01T00:00:00Z")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("16. nädogry wagt formaty -> 400")
    void invalidDateFormatReturns400() throws Exception {
        User user = givenUserWithAccount("badformat@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions?from=not-a-date")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    // ---------- 6. TYPE FILTER ----------

    @Test
    @DisplayName("17. type=DEPOSIT / WITHDRAW / TRANSFER_IN / TRANSFER_OUT - her biri işleýär")
    void allFourTypesAppearAndFilter() throws Exception {
        User a = givenUserWithAccount("types-a@example.com");
        User b = givenUserWithAccount("types-b@example.com");

        deposit(a, "1000", "salem");
        withdraw(a, "100");
        transfer(a, accountNumberOf(b), "200");     // a -> b
        transfer(b, accountNumberOf(a), "50");      // b -> a

        // A-nyň tarhyh: 4 sany ýazgy
        String aToken = tokenFor(a);
        mockMvc.perform(get("/api/accounts/me/transactions?size=20")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                // 🔴 Iň TÄZE BIRINJI: TRANSFER_IN (b-den gelen 50)
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.content[1].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.content[2].type").value("WITHDRAW"))
                .andExpect(jsonPath("$.content[3].type").value("DEPOSIT"));

        // type=DEPOSIT -> diňe DEPOSIT
        mockMvc.perform(get("/api/accounts/me/transactions?type=DEPOSIT")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"));

        // type=TRANSFER_OUT -> diňe TRANSFER_OUT
        mockMvc.perform(get("/api/accounts/me/transactions?type=TRANSFER_OUT")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_OUT"));
    }

    // ---------- 7. HOWPSUZLYK: IDOR (IŇ MÖHÜM) ----------

    @Test
    @DisplayName("18. User A diňe ÖZ ýazgylaryny görýär (B-niň ýazgysy ÝOK)")
    void userASeesOnlyOwnTransactions() throws Exception {
        User a = givenUserWithAccount("idor-a@example.com");
        User b = givenUserWithAccount("idor-b@example.com");

        deposit(a, "1000", "A-deposit");
        deposit(b, "999999", "B-deposit");

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("A-deposit"))
                // 🛡️ B-niň maglumaty HİÇ NEREDE ýok
                .andExpect(jsonPath("$.content[0].description")
                        .value(org.hamcrest.Matchers.not("B-deposit")));
    }

    @Test
    @DisplayName("19. User B diňe ÖZ ýazgylaryny görýär (iki tarap hem barlanýar)")
    void userBSeesOnlyOwnTransactions() throws Exception {
        User a = givenUserWithAccount("idor2-a@example.com");
        User b = givenUserWithAccount("idor2-b@example.com");

        deposit(a, "1000", "A-deposit");
        deposit(b, "999999", "B-deposit");

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("B-deposit"));
    }

    @Test
    @DisplayName("20. ?userId=BAŞGANYŇ -> ÖŇKI kollonuwçynyň taryhy (öňürtçilik ÝOK)")
    void queryParametersCannotOverrideAuthenticatedUser() throws Exception {
        User a = givenUserWithAccount("attack-a@example.com");
        User b = givenUserWithAccount("attack-b@example.com");

        deposit(a, "1000", "A-private");
        deposit(b, "999999", "B-private");

        // 🛡️ KLIENT HAJATY: "A-nyň tokeni bilen B-nyň taryhyny
        //    al" diýip ümün edýär. ?userId= B-niň hakyky id-si.
        mockMvc.perform(get("/api/accounts/me/transactions?userId=" + b.getId())
                        .header("Authorization", "Bearer " + tokenFor(a)))
                .andExpect(status().isOk())
                // B-niň maglumaty ÝOK, A-nyň özi görünýär
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("A-private"))
                .andExpect(jsonPath("$.content[0].description")
                        .value(org.hamcrest.Matchers.not("B-private")));
    }

    @Test
    @DisplayName("21. ?accountId=BAŞGANYŇ -> hem üstünlik çözülmeýär")
    void accountIdParameterCannotOverrideEither() throws Exception {
        User a = givenUserWithAccount("attack2-a@example.com");
        User b = givenUserWithAccount("attack2-b@example.com");

        deposit(a, "1000", "A-private");
        deposit(b, "999999", "B-private");

        Long bAccountId = accountRepository.findByUserId(b.getId())
                .orElseThrow().getId();

        mockMvc.perform(get("/api/accounts/me/transactions?accountId=" + bAccountId)
                        .header("Authorization", "Bearer " + tokenFor(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("A-private"));
    }

    // ---------- 8. WAGT FILTERI ----------

    @Test
    @DisplayName("22. from / to / from+to - ISO-8601 arkaly")
    void dateFiltersWork() throws Exception {
        User user = givenUserWithAccount("dates@example.com");
        deposit(user, "10", "now");

        // mundan ÖŇKI ýazga ýok - şonuň üçin from ileri -> boş
        mockMvc.perform(get("/api/accounts/me/transactions"
                        + "?from=2030-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // mundan öňki aralyk -> ýazga BAR
        mockMvc.perform(get("/api/accounts/me/transactions"
                        + "?from=2020-01-01T00:00:00Z&to=2030-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------- 9. SUMA FILTERI ----------

    @Test
    @DisplayName("23. minAmount / maxAmount - aralyk")
    void amountFiltersWork() throws Exception {
        User user = givenUserWithAccount("amounts@example.com");
        deposit(user, "50", "small");
        deposit(user, "500", "mid");
        deposit(user, "5000", "big");

        mockMvc.perform(get("/api/accounts/me/transactions"
                        + "?minAmount=100&maxAmount=1000")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("mid"));
    }

    @Test
    @DisplayName("24. minAmount > maxAmount -> 400")
    void minGreaterThanMaxReturns400() throws Exception {
        User user = givenUserWithAccount("badamount@example.com");

        mockMvc.perform(get("/api/accounts/me/transactions"
                        + "?minAmount=1000&maxAmount=100")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    // ---------- 10. HOWPSUZ JOGAP ----------

    @Test
    @DisplayName("25. jogapda parol, user obýegi ýa-da ýalňyş ýok")
    void responseHasNoSensitiveDataOrStackTrace() throws Exception {
        User user = givenUserWithAccount("safe@example.com");
        deposit(user, "500", "seed");

        String body = mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 🔴 Hiç bir duýgur maglumat çykmaýar
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("at com.minibank");
        assertThat(body).doesNotContain("accountId");
    }

    // ---------- 11. CONSISTENCY (PHASE 5/6) ----------

    @Test
    @DisplayName("26. PHASE 5/6 ýazgylary dogry tertipde we balanceAfter bazadaky bilen deň")
    void historyIsConsistentWithRealOperations() throws Exception {
        User a = givenUserWithAccount("consist-a@example.com");
        User b = givenUserWithAccount("consist-b@example.com");

        deposit(a, "1000", "deposit");
        withdraw(a, "100");
        transfer(a, accountNumberOf(b), "200");

        // 🔴 Esasanň balansy: 1000 - 100 - 200 = 700
        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(a)))
                .andExpect(status().isOk())
                // newest first
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.content[0].amount").value(200.0000))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(700.0000))
                .andExpect(jsonPath("$.content[1].type").value("WITHDRAW"))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(900.0000))
                .andExpect(jsonPath("$.content[2].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[2].balanceAfter").value(1000.0000));

        // 🧡 we B-niň tarhyhda A-nyň ýazgysy ÝOK
        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"));
    }
}
