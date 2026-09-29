package com.minibank.integration;

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtProperties;
import com.minibank.security.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * ==========================================================================
 *  SECURITY HARDENING — PHASE 9
 * ==========================================================================
 *
 *  5 esasy ugur:
 *    1. JWT (15, 16, 17) — 7 hal: ýok/buzulgan/üýtgedilen/ möhleti
 *       geçen/başga imza/başga issuer/ýalňyş subject
 *    2. MASS ASSIGNMENT (19) — klient "role":"ADMIN" ýazsa
 *    3. IDOR (18) — USER başga user-yň maglumatyna ýetmeýär
 *    4. MONEY PRECISION (8, 9) — 0, -, 5-kattulyk, deňlik
 *    5. PAROL (20) — hiç bir yerde çykmaýar
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityHardeningTest {

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

    @Autowired
    private JwtProperties jwtProperties;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String PASSWORD = "Password123!";

    private User userA;
    private User userB;
    private Account accountA;
    private Account accountB;
    private String tokenA;
    private String adminToken;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User(
                "Admin", "admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN));
        adminToken = jwtService.generateToken(admin);

        userA = userRepository.save(new User(
                "Ali A", "ali@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER));
        userB = userRepository.save(new User(
                "Bekir B", "bekir@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER));

        accountA = givenAccount(userA);
        accountB = givenAccount(userB);
        tokenA = jwtService.generateToken(userA);
    }

    private Account givenAccount(User user) {
        return accountRepository.save(new Account(
                user, "40817810" + String.format("%010d", user.getId()),
                Account.RUB, BigDecimal.ZERO.setScale(4)));
    }

    private String realKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    // ==================================================================
    //  1) JWT HARDENING (15-njy bend) — 7 HAL
    // ==================================================================

    @Test
    @DisplayName("1. token ÝOK -> 401")
    void missingTokenRejected() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. MALFORMED token -> 401")
    void malformedTokenRejected() throws Exception {
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. TAMPERED token (biri harf üýtgertilip) -> 401")
    void tamperedTokenRejected() throws Exception {
        // 🔴 Biri harpy üýgetýäris -> imza ýoňulýar -> 401
        String tampered = tokenA.substring(0, tokenA.length() - 3)
                + "AAA";
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. EXPIRED token (möhleti geçen) -> 401")
    void expiredTokenRejected() throws Exception {
        // 🧡 Möhleti GEÇEN token döredýäris (1 sekunt öň).
        //    imza dogry, ýöne waýt ötken - JJWT "exp" sanyny
        //    özi barlaýar, biz kod ýazmaýarys.
        String expired = Jwts.builder()
                .subject(String.valueOf(userA.getId()))
                .claim("role", "USER")
                .issuer("minibank")
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 60_000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(
                        jwtProperties.secret().getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. WRONG SIGNATURE (başga syr) -> 401")
    void wrongSignatureRejected() throws Exception {
        String forged = Jwts.builder()
                .subject(String.valueOf(userA.getId()))
                .claim("role", "ADMIN")
                .issuer("minibank")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(
                        "another-secret-long-enough-32-bytes!!!".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6. WRONG ISSUER (başga ýazgyjydan) -> 401")
    void wrongIssuerRejected() throws Exception {
        // 🧡 Biziň tokenimiz "minibank" diýip imzalanyp. Başga
        //    ýazgyjynyň tokeni "evil-bank" - şeýle bir düşnüşde,
        //    ýöne başga ulgam. requireIssuer("minibank") reject edýär.
        String otherIssuer = Jwts.builder()
                .subject(String.valueOf(userA.getId()))
                .claim("role", "USER")
                .issuer("some-other-system")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(
                        jwtProperties.secret().getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + otherIssuer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. INVALID SUBJECT (id däl-de erbet) -> 401")
    void invalidSubjectRejected() throws Exception {
        String badSubject = Jwts.builder()
                .subject("not-a-number")     // Long.valueOf() fail bolýar
                .claim("role", "USER")
                .issuer("minibank")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(
                        jwtProperties.secret().getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        // 🧡 JwtAuthenticationFilter IllegalArgumentException-y
        //    catch edip, "tanaldi" diýmez - 401 gelýär.
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + badSubject))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    //  2) JWT ROLE AUTHORITATIVE (16, 17-njy bender)
    // ==================================================================

    @Test
    @DisplayName("8. bazada USER, token-dä ADMIN -> 403 (token ROLI ýoňulýar)")
    void dbRoleWinsOverTokenClaim() throws Exception {
        // 🧡 BU TEST 16-njy BENDIŇ ÖZÜ. Token imza dogry, ýöne
        //    içindäki role AÝDYŞ. JwtAuthenticationFilter role-i
        //    BAZADAN okaýar -> USER -> admin endpoint 403.
        String tokenSaysAdmin = Jwts.builder()
                .subject(String.valueOf(userA.getId()))
                .claim("role", "ADMIN")
                .issuer("minibank")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(
                        jwtProperties.secret().getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + tokenSaysAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. bazada ADMIN, eski token role=USER -> 200 (DB ýEŇI)")
    void dbRoleChangeTakesEffectImmediately() throws Exception {
        // 🧡 17-njy BEND: role mutation endpoint ýOG.
        //    Şonuň ýöne rol BAZADA üýtgese, indiki soragda
        //    derrew täze rol bilen barlanýar - eski token hem
        //    täze gözegçilik bilen işleýär.
        //
        //    Bu ýagdaýda: admin ÜŇÜŞTEN USER bolýarys (eski token
        //    role=ADMIN diýip durýar), soň admin endpoint soraýarys
        //    -> 403 (çünki indi ol USER). Ýa tersine: USER -> ADMIN.
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();

        // 🟡 ROLE ÜÝTGETMIK ÜÇIN - kol el bilen, admin-i ÝOK ETMEÝÄRIS.
        //    Sebäbi: admin-i ýok etsek, täze User TÄZE ID alýar, we
        //    eski token (koniň id-si) bazada ýok bolýar -> 401.
        //    Emma biz "role üýtgedi" diýen şerti synaýarys - ýok etmek
        //    däl. Şonuň üçin JpaRepository.flush() + entity-niň
        //    role meýdanyny göni üýgetýäris (identity aýratyn).
        //
        //    🧡 BU production kod ýaly "ýöne gizlin ýol" däl - bu
        //    ÝÖNE KEÇIŞ ÝAGDAÝY: admin endpoint ýOG, role mutasiýasy
        //    ýOG, şonuň üçin bu diňe test üçin. Ol ýarym ýagdaýy
        //    barlamagyň öz ýoludy.
        Long adminId = admin.getId();
        String oldToken = adminToken;

        // Baza role-i göni üýgetýäris
        entityManager.createNativeQuery(
                        "UPDATE users SET role = 'USER' WHERE id = :id")
                .setParameter("id", adminId)
                .executeUpdate();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + oldToken))
                // 🧡 Token "ADMIN" diýýardy (imza dogry, id dogry),
                //    ýöne bazada indi USER.
                //    => JwtAuthenticationFilter bazadan okap, ROLE_USER
                //    berýär => hasRole("ADMIN") üçegin geçmeýär => 403.
                .andExpect(status().isForbidden());

        // 🧡 WE tersine: yzyna ADMIN edip, şol ETI TOKEN ýene-de
        //    200 berýär - sebäbi görkeziji ID, role her gezek bazadan.
        entityManager.createNativeQuery(
                        "UPDATE users SET role = 'ADMIN' WHERE id = :id")
                .setParameter("id", adminId)
                .executeUpdate();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk());
    }

    // ==================================================================
    //  3) MASS ASSIGNMENT (19-njy bend)
    // ==================================================================

    @Test
    @DisplayName("10. registration: role=ADMIN ýazsa -> USER galýar (ýokaryga çykmak ýok)")
    void registrationCannotEscalateRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Hacker\", \"email\": \"h@x.com\","
                                + "\"password\": \"Password123!\", \"role\": \"ADMIN\"}"))
                .andExpect(status().isCreated())
                // 🛡️ UserService register-de role HAR ALWAYS goýýar:
                // Role.USER. Klientiň ýazgysy DEŇEŞDIRILMEÝÄR.
                .andExpect(jsonPath("$.role").value("USER"));

        User saved = userRepository.findByEmail("h@x.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("11. registration: balance/accountId ýazsa -> DEŇEŞDIRILMEÝÄR")
    void registrationIgnoresInternalFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Hacker\", \"email\": \"h2@x.com\","
                                + "\"password\": \"Password123!\","
                                + "\"balance\": 999999999, \"id\": 1,"
                                + "\"role\": \"ADMIN\"}"))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("h2@x.com").orElseThrow();
        // Esap hiç ýok döredilmedi (register edip özümüne açaýyn)
        assertThat(accountRepository.findByUserId(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("12. deposit: balance/userId ýazsa -> 200, ýöne BALANS hakyky")
    void depositIgnoresMassAssignment() throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100, \"balance\": 999999999,"
                                + "\"userId\": " + userB.getId() + "}"))
                .andExpect(status().isOk())
                // 🛡️ DTO-diň özünde "balance" ýa-da "userId" ýogy -
                //    Jackson olary SALLY ýatýar (bilelikde 31-njy bend).
                //    Netije: diňe 100 goşuldy, B-niň esabyna ýazylmady.
                .andExpect(jsonPath("$.balance").value(100.0000));

        Account a = accountRepository.findById(accountA.getId()).orElseThrow();
        Account b = accountRepository.findById(accountB.getId()).orElseThrow();
        assertThat(a.getBalance()).isEqualByComparingTo("100");
        assertThat(b.getBalance()).isEqualByComparingTo("0");
    }

    // ==================================================================
    //  4) IDOR (18-njy bend)
    // ==================================================================

    @Test
    @DisplayName("13. USER: ?accountId=B -> ÖZ taryhy (B ÝOK)")
    void userCannotReadOthersViaQueryParam() throws Exception {
        deposit(tokenA, "100");
        deposit(jwtService.generateToken(userB), "999");

        mockMvc.perform(get("/api/accounts/me/transactions?accountId=" + accountB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(100.0000));
    }

    @Test
    @DisplayName("14. USER: ?userId=B -> ÖZ maglumaty")
    void userCannotReadOthersViaUserIdParam() throws Exception {
        mockMvc.perform(get("/api/accounts/me/transactions?userId=" + userB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("15. USER -> /api/admin/accounts/B -> 403, maglumat SYZMAÝAR")
    void userCannotAccessAdminAccountOfOther() throws Exception {
        String body = mockMvc.perform(
                        get("/api/admin/accounts/" + accountB.getId())
                                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(accountB.getAccountNumber());
        assertThat(body).doesNotContain("bekir@example.com");
    }

    // ==================================================================
    //  5) MONEY PRECISION (8, 9, 10-njy bender)
    // ==================================================================

    @Test
    @DisplayName("16. deposit: 0, -, 5-kat -> 400 (eterlik däl DÄL)")
    void moneyValidationIsStrict() throws Exception {
        for (String amount : new String[]{"0", "-100", "100.12345"}) {
            mockMvc.perform(post("/api/accounts/me/deposit")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": " + amount + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("17. deposit: 100 we 100.0 we 100.0000 -> BARLI, semantika BIRMEŇEŞ")
    void scaleDoesNotChangeSemantics() throws Exception {
        // 🧡 9-njy BEND: 100, 100.0, 100.00, 100.0000 - hemmeleri
        //    "100" MANYSY. Balans her gezek toplam 100 ýeňil.
        deposit(tokenA, "100");      // balans 100
        deposit(tokenA, "100.0");    // balans 200
        deposit(tokenA, "100.00");   // balans 300
        deposit(tokenA, "100.0000"); // balans 400

        Account a = accountRepository.findById(accountA.getId()).orElseThrow();
        // 🔴 compareTo, equals DÄL: "400.0000".equals("400") = false,
        //    emma compareTo == 0. Şonuň üçin compareTo ulanýarys.
        assertThat(a.getBalance()).isEqualByComparingTo("400");
        // 🧡 scale HEMMELERE 4 (DB NUMERIC(19,4) - biziň kadamymyz)
        assertThat(a.getBalance().scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("18. withdraw: balansdan köp -> 409, balans ÜÝTGEMEÝÄR")
    void withdrawBeyondBalanceReturns409AndKeepsBalance() throws Exception {
        deposit(tokenA, "100");

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000}"))
                .andExpect(status().isConflict());

        Account a = accountRepository.findById(accountA.getId()).orElseThrow();
        // 🛡️ Balans 100 - ÖŇKI ÝAGDAÝDA (ters bolmady)
        assertThat(a.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("19. transfer: 0.1 + 0.2 = 0.3 ANYY (double kadasy ÝOK)")
    void decimalArithmeticIsExact() throws Exception {
        // 🔴 double bilen: 0.1 + 0.2 = 0.30000000000000004
        //    BigDecimal bilen: tam 0.3
        deposit(tokenA, "0.1");
        deposit(tokenA, "0.2");

        Account a = accountRepository.findById(accountA.getId()).orElseThrow();
        assertThat(a.getBalance()).isEqualByComparingTo("0.3");
        assertThat(a.getBalance().toPlainString()).isEqualTo("0.3000");
    }

    // ==================================================================
    //  6) PAROL (20-njy bend)
    // ==================================================================

    @Test
    @DisplayName("20. parol: login, /me, register, error jogaplarynda ÝOK")
    void passwordNeverAppearsInAnyResponse() throws Exception {
        String[] bodies = new String[5];

        bodies[0] = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ali@example.com\","
                                + "\"password\": \"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();

        bodies[1] = mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getContentAsString();

        bodies[2] = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"N\", \"email\": \"n@x.com\","
                                + "\"password\": \"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();

        // Ýalňyş jogaby (ýalňyş parol bilen)
        bodies[3] = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ali@example.com\", \"password\": \"wrong\"}"))
                .andReturn().getResponse().getContentAsString();

        // Admin sanawy
        bodies[4] = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();

        for (String body : bodies) {
            assertThat(body).doesNotContain(PASSWORD);
            assertThat(body).doesNotContain("passwordHash");
            assertThat(body).doesNotContain("$2a$");
            assertThat(body).doesNotContain("$2b$");
        }

        // 🧡 Bazada ýöne hem BCrypt (açyk parol ýOK)
        User saved = userRepository.findByEmail("ali@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
    }

    // ==================================================================
    //  KÖMEKÇI
    // ==================================================================

    private void deposit(String token, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + "}"))
                .andExpect(status().isOk());
    }
}
