package com.minibank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtProperties;
import com.minibank.security.JwtService;

/**
 * ==========================================================================
 *  ADMIN API SINOZLARY — PHASE 8
 * ==========================================================================
 *
 *  🔴 BU FAZYŇ IŇ MÖHÜM TESTLERI — 2 SANY ZAT BARLADY:
 *
 *   1. KOOPSUZLYK MATRISA (36-njy bend)
 *      Her endpoint uchun 3 hal:
 *        - TOKEN YOK  -> 401 (tanilmadyk)
 *        - USER rolu  -> 403 (tanyldy, rugsat yok)
 *        - ADMIN rolu -> 200 (rugsat bar)
 *      Bu matritsa HASAPLAÝAR, ýazmaýar: "haýsy ýagdaýda haýsy
 *      jogap gerek" diýip ýazylýar we soňra URL arkaly barlanýar.
 *      Sebäbi: has ýalňyş şu ýerde bolýar - gorag ýazdyk ýaly
 *      görünip, emma .anyRequest() ony basyp ýatýar ýaly.
 *
 *   2. PAROL HASYNYŇ SIZMAÝŞY
 *      9, 11-njy bender: "passwordHash heç haçan chykmaly däl".
 *      Bu DIŇE söz bilen barlanbayar - göni göni jogap tekstinde
 *      "password" sözü barlanýar. Biziŋ soragymyzyň HEMMESINI
 *      (list we bir ýekelik) birikdirip.
 *
 *  🟡 MOCKMVC: chyky REST stegini sınaýar - Security çyрпаклары,
 *      Jackson, GlobalExceptionHandler. Port açylmaýar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminApiSecurityTest {

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

    private static final String PASSWORD = "Password123!";

    private User admin;
    private User userA;
    private User userB;
    private Account accountA;
    private Account accountB;

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // 🔴 AÝDYŇ ÝANYYDA ADMIN DÜZÜLÝÄR - göz önünde däl.
        //    Bu ýerde ýalňyş iň ýykse ederdi: "admin testleri
        //    ýok" diýip, eger tizim ADMIN-a hiç açyk bermese,
        //    hemme testler "403 beklenen" ýaly ýalňyş geçerdi
        //    we ýöne hakyky gorag barlanmaýardy.
        admin = userRepository.save(new User(
                "Admin Boss", "admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN));

        userA = userRepository.save(new User(
                "Ali A", "ali@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER));
        userB = userRepository.save(new User(
                "Bekir B", "bekir@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER));

        accountA = givenAccount(userA);
        accountB = givenAccount(userB);
    }

    private Account givenAccount(User user) {
        return accountRepository.save(new Account(
                user, "40817810" + String.format("%010d", user.getId()),
                Account.RUB, BigDecimal.ZERO.setScale(4)));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private void deposit(User user, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + "}"))
                .andExpect(status().isOk());
    }

    /** 5-nji bend: HER bir endpoint üçin hal. */
    private void checkEndpoint(String path, User owner) throws Exception {
        // 1) TOKEN YOK -> 401
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        // 2) USER roli -> 403  (admin endpointleri üçin)
        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isForbidden());

        // 3) ADMIN roli -> 200
        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk());
    }

    // ==================================================================
    //  1) KOOPSUZLYK MATRISA — 5 endpoint
    // ==================================================================

    @Test
    @DisplayName("MATRISA 1. /api/admin/users: yok=401, USER=403, ADMIN=200")
    void adminUsersMatrix() throws Exception {
        checkEndpoint("/api/admin/users", userA);
    }

    @Test
    @DisplayName("MATRISA 2. /api/admin/users/{id}: yok=401, USER=403, ADMIN=200")
    void adminUserByIdMatrix() throws Exception {
        checkEndpoint("/api/admin/users/" + userA.getId(), userA);
    }

    @Test
    @DisplayName("MATRISA 3. /api/admin/accounts: yok=401, USER=403, ADMIN=200")
    void adminAccountsMatrix() throws Exception {
        checkEndpoint("/api/admin/accounts", userA);
    }

    @Test
    @DisplayName("MATRISA 4. /api/admin/accounts/{id}: yok=401, USER=403, ADMIN=200")
    void adminAccountByIdMatrix() throws Exception {
        checkEndpoint("/api/admin/accounts/" + accountA.getId(), userA);
    }

    @Test
    @DisplayName("MATRISA 5. /api/admin/accounts/{id}/transactions: 401/403/200")
    void adminTransactionsMatrix() throws Exception {
        checkEndpoint(
                "/api/admin/accounts/" + accountA.getId() + "/transactions", userA);
    }

    // ==================================================================
    //  2) 401 / 403 ÝAŞYRYK MAGLUMAT BOLMALY DÄL (38-njy bend)
    // ==================================================================

    @Test
    @DisplayName("6. 403 jogaby 'Access denied' diýip, maglumat SYZMAÝAR")
    void forbiddenResponseDoesNotLeakInformation() throws Exception {
        String body = mockMvc.perform(get("/api/admin/accounts/" + accountB.getId())
                        .header("Authorization", "Bearer " + tokenFor(userA)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        // 🔴 "B-nin esebi bar" diýen maglumat SIZMAÝAR
        assertThat(body).doesNotContain(accountB.getAccountNumber());
        assertThat(body).doesNotContain("bekir@example.com");

        // 🧡 38-njy bend: "ýurumyň öz conventionuny saklaýarys" - biz
        //    täze tekst döredýäris däl. RestAccessDeniedHandler
        //    ýazdygy ýaly: "You do not have permission to access
        //    this resource". Bu ýeterlik: B-nin bar/ýokdygy
        //    aýrylmaýar, sebäbi eňir sema-da "permission to access
        //    this resource" diýip ýazýar - umumy jogap.
        assertThat(body).contains("You do not have permission to access this resource");

        // 🛡️ goşmaça: esep bar/ýok barlandyryşy ýok. Sebäbi
        //    404-de "Account with id 577 not found" diýsek -
        //    ol 403-e garşy MAGLUMAT SIZDIRÝAR (esep bar ekenmi
        //    aýdyň bolýar). Şonuň üçin 403 UMUMY bolmaly.
        assertThat(body).doesNotContain("not found");
        assertThat(body).doesNotContain("Account with id");
    }

    @Test
    @DisplayName("7. USER /api/admin/users/{id} -> 403, maglumat syzmaýar")
    void userCannotGuessOthersData() throws Exception {
        String body = mockMvc.perform(get("/api/admin/users/" + userB.getId())
                        .header("Authorization", "Bearer " + tokenFor(userA)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("bekir@example.com");
        assertThat(body).doesNotContain("Bekir");
    }

    // ==================================================================
    //  3) PAROL HASY (9, 11, 29-njy bender)
    // ==================================================================

    @Test
    @DisplayName("8. parol hasy HECH QAÇAN sınmaz (list we bir ýekelik)")
    void passwordHashIsNeverExposed() throws Exception {
        String list = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String one = mockMvc.perform(get("/api/admin/users/" + userA.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String body : List.of(list, one)) {
            assertThat(body).doesNotContain("password");
            assertThat(body).doesNotContain("$2a$");   // BCrypt belgisi
            assertThat(body).doesNotContain("$2b$");
        }
    }

    // ==================================================================
    //  4) IDOR: USER A -> B-nin esebi (19-njy bend)
    // ==================================================================

    @Test
    @DisplayName("9. USER A B-nin esebini soraýar -> 403, ADMIN -> 200")
    void userCannotReadOthersButAdminCan() throws Exception {
        deposit(userB, "500");

        String path = "/api/admin/accounts/" + accountB.getId() + "/transactions";

        // USER A -> 403
        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + tokenFor(userA)))
                .andExpect(status().isForbidden());

        // ADMIN -> 200 we B-nin ýazgysyny görýär
        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"));
    }

    @Test
    @DisplayName("10. ADMIN başga kollonuwçynyň esebini we taryhyny barlaýar")
    void adminCanInspectAnyAccount() throws Exception {
        deposit(userA, "100");
        deposit(userB, "200");

        // A-nyň esebi - diňe A-nyň ýazgysy
        mockMvc.perform(get("/api/admin/accounts/" + accountA.getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // B-nyň esebi - diňe B-nyň ýazgysy
        mockMvc.perform(get("/api/admin/accounts/" + accountB.getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ==================================================================
    //  5) ADMIN ÖZŇ USER ENDPOINT'LERINI KOLLANA ALÝAR (25-njy bend)
    // ==================================================================

    @Test
    @DisplayName("11. ADMIN /api/accounts/me ulanyp biler (200) - right aýrylmaýar")
    void adminCanUseUserEndpoints() throws Exception {
        // 🟡 /api/accounts/me -> AccountResponse (esep, EMES user!).
        //    Bu AccountController-da ýaşáýar (PHASE 4). Şonuň üçin
        //    onda "email"/"role" ýok - esap görkezýär.
        //    🧡 Esap ÝOK bolmasa 404 (hesap ýok düzgüni) - şonuň
        //    üçin admin hem bir esap açýarys: 25-njy bend diýipdi
        //    "admin öz user endpoint'lerini ulanа biler", emma
        //    esep ýok bolsa olar işlemez. Bu ýalňyş köp ýüze çykýar.
        givenAccount(admin);

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").exists())
                .andExpect(jsonPath("$.currency").value("RUB"));
    }

    @Test
    @DisplayName("12. ADMIN /api/accounts/me/transactions ulanyp biler (200)")
    void adminCanUseOwnTransactionHistory() throws Exception {
        // 🟡 ADMIN hem ADAM: öz esebi bolmaly bolýar, ýogsam 404.
        //    Bu ýerde esep açýarys, soň ýerine tanyşýarys.
        givenAccount(admin);

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("13. USER öz endpoint'i üýtgemedi (200) - kýynjylyk ýok")
    void userEndpointsUnchangedForUser() throws Exception {
        // 🧡 KIMDIR KÖRÜNÜŞI ÜÇIN DOGRY ENDPOINT: /api/users/me
        //    (UserController). "email"/"role" şu ýerde bolar.
        //    /api/accounts/me bolsa ESEP görkezýär (email ýok) -
        //    iki ýer garyşdyrmak iň ýygy ýalňyş bolýar.
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tokenFor(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ali@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    // ==================================================================
    //  6) TOKEN HALTLARY
    // ==================================================================

    @Test
    @DisplayName("14. buzulgan token -> 401 (admin endpoint)")
    void brokenTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("15. basga syrr bilen imzalanan token -> 401")
    void forgedTokenReturns401() throws Exception {
        String forged = io.jsonwebtoken.Jwts.builder()
                .subject("1")
                .claim("role", "ADMIN")     // ýalança admin!
                .issuer("minibank")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
                .signWith(io.jsonwebtoken.security.Keys
                        .hmacShaKeyFor(("another-secret-long-enough-32-bytes-here-ok")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("16. JWT-da ýalyn ADMIN ýazylandy, bazada USER -> 403 (DB authoritative)")
    void dbRoleIsAuthoritativeNotTokenClaim() throws Exception {
        // 🧡 BU TEST 3-nji BENDIŇ "DB role authoritative" düzgünini barlaýar.
        //    Token diňe ID taşıýar. ROL bazadan alynýar. Şonuň üçen
        //    token-i "kol bilen" üýtgetmek mümkün däl - ol imzalanan.
        //    Emma: eger token role-y basyp ýazsak (imza-çözgüt bilen
        //    çäksiz bilelikde, ýalňyş ýerde), bazadaky USER hökm
        //    edýär. Bu ýerde biziň çylykmyz:
        //        imza dogry, role çykylanda -> 403.
        String tokenWithForgedAdminRole = io.jsonwebtoken.Jwts.builder()
                .subject(String.valueOf(userA.getId()))
                .claim("role", "ADMIN")     // ýALŇAŇ AÝDYŞ
                .issuer("minibank")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
                .signWith(jwtSigningKeyForTests())
                .compact();

        // Bazada userA = USER. Token "ADMIN" diýip ýalança aýdýar.
        // JOGAP: 403 - çünki çyрпаk role-i BAZADAN okap alýar.
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + tokenWithForgedAdminRole))
                .andExpect(status().isForbidden());
    }

    /**
     * 🔐 Test üçin HAKYKY imza açary - JwtProperties-den alynýar.
     *
     * 🧡 NÄME ÜÇIN Spring-den aýýarys, ýokarydan getirilen (.env) ýok?
     *     Syr .env FAÝLYNDAN gelýär, we ol faýl Spring-e ähli
     *     başlanýyş wagtynda import edilýär (application.yml-de
     *     "config: import: optional:file:.env"). Şonuň üçin
     *     System.getenv("JWT_SECRET") işlemeýär - bu proccessiň
     *     daşky gurşawyna bagly, faýl ýok.
     *     JwtProperties BEAN bolsa şol faýly OKAP alan ýer -
     *     şonuň üçin ol ýeňil we dogry ýol.
     *
     * 🧡 SIR DIŇE ŞU YERDE, TEST KODYNYŇ IÇINDE.
     *     Biz ony hiç haçan loglamaýarys.
     */
    private javax.crypto.SecretKey jwtSigningKeyForTests() {
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ==================================================================
    //  7) YAZMA ENDPOINTLERI ÝOK (26/27/28-njy bender)
    // ==================================================================

    @Test
    @DisplayName("17. admin deposit/withdraw/role/delete ÝOK (404/405)")
    void adminWriteEndpointsDoNotExist() throws Exception {
        String auth = "Bearer " + tokenFor(admin);

        // 🟡 NÄME ÜÇIN 404, ÝARYM 405 DÄL?
        //     405 = "bu URL bar, ýöne bu usul (POST) bolmaýar".
        //     404 = "bu URL-iň ýene belli bir usuly hem ýok".
        //     Biziň ýagdaýymyz: /api/admin/accounts/{id}/deposit diýen
        //     controller usuly GAÝTADAN ÝAZYLMAÝAR (biz ýazmak
        //     islegenimiz däl - 26/27/28-njy bend). Spring şonda
        //     NoResourceFoundException taşlaýar, GlobalExceptionHandler
        //     4-faýza görkezýär.
        //     Netije: bu test HEM ýazmak ýokdygyny, HEM nädogry
        //     URL-lere düşüp barmaýandygyny tassyklaýar.
        //     🔴 MÖHÜM: 4-faýza görkezmeýär - sebäbi bu ýerde
        //     parol ýa-da başga maglumat ýok.

        // Money - admin üçin ýazmak ýok
        mockMvc.perform(post("/api/admin/accounts/" + accountA.getId() + "/deposit")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/accounts/" + accountA.getId() + "/withdraw")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100}"))
                .andExpect(status().isNotFound());

        // Role üýtgetme ýok
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/users/" + userA.getId() + "/role")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"ADMIN\"}"))
                .andExpect(status().isNotFound());

        // 🟡 Ýok etme - bu ýagdaýda 405, sebäbi /api/admin/users/{id}
        //     URÝ ÝARYM BILEN BOLÝAR (GET admin user by id), ýöne
        //     DELETE usuly ýok. "Bu ýer bar, emme bu usul däl"
        //     diýen many. 404 däl!
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/users/" + userA.getId())
                        .header("Authorization", auth))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
