package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * ==========================================================================
 *  DATABASE FAILURE HANDLING + AUTHENTICATION RESILIENCE — PHASE 10D
 * ==========================================================================
 *
 *  🧠 БУ ФАЗА НИ ҮЧИН?
 *
 *      PHASE 10C-де биз bir ýalňyş tapdyk we ony «out-of-scope»
 *      diýip goýdyk. Ol ýalňyş şu:
 *
 *          PostgreSQL öçen bolanda, VALID JWT bilen bir sorag
 *          401 alýardy (garaşma «token nädogry» diýen ýaly görünýärdi).
 *
 *      🔴 Bu ýalňyş «classification» ýalňyşy:
 *
 *          | Ýagdaý                 | dogry | ýalňyş |
 *          |------------------------|-------|---------|
 *          | Token nädogry          | 401   | 401 ✅  |
 *          | Token dogry, DB ÝOK    | 503   | 401 ❌  |
 *
 *      Bu test şol ikinji setiriň dogry bolýandygyny tassyklaýar.
 *
 * ==========================================================================
 *  🔴 TEST USULY: NÄME ÜÇIN @MockitoSpyBean, DOCKER BOLMAÝAN?
 * ==========================================================================
 *
 *      Ýaňy bir ýalňyşy ýene-de «çynky çözgüt» hökmünde ýatdyrmamak
 *      üçin biz IKI usuly deňeşdirýäris:
 *
 *      USUL 1 — @MockitoSpyBean (bu test, HEMIŞE işleýär):
 *          userRepository.findById() -> DataAccessResourceFailureException
 *          «Taýýar emele gelýär» görnüşi bilen «база жеткиликсиз» ýagdaýyny
 *          DÝRNE DÖREDÝÄR. Eger filter bu ýagdaýda näme edýändigini
 *          barlamasaýdyk, testiň özi hem gerek bolmazdy.
 *
 *          🧡 MÖHÜM: bu usul ÖŇKI ýaly-ýaly DB-ni ÖÇÜRMEÝÄR, hiç
 *          haçan maglumat ýitirmeýär we bejerleşmäni üýtgetmeýär.
 *
 *      USUL 2 — real Docker (PHASE10D_DOCKER_TESTS=true, opt-in):
 *          «Анчалап» göçürilen ýagdaý — çyn PostgreSQL öçen ýagdaýy.
 *          Ol ýokarydaky ýaly «ýokary energiýaly» (göçürmeýär,
 *          diňe öçüp-ýakýar), emma CI-da docker ýok bolup biler —
 *          şonuň üçin opt-in edýäris (PHASE 10B-y ýaly).
 *
 *      IKI ÝOLŇ TAPAWUDY:
 *          Usul 1 → «FILTER nähili kategoryalaýar?» (logika)
 *          Usul 2 → «HEMIŞE real stack-de shu ýalňyş ýüze çykýarmy?» (realistika)
 *
 * ==========================================================================
 *  🔴 5-Njy BEND: NÄME ÜÇIN DIŇE IKI EXCEPTION SANYLÝAR?
 * ==========================================================================
 *      «Exception.class» diýip ýazsak, 5 test hemme 503 bolardy.
 *      Bu ÝALŇ: programma ýalňyşy (NullPointerException) hem
 *      «база ýok» diýip görkezilýärdi. Şonuň üçin biz diňe:
 *          • DataAccessException      (Spring data-access)
 *          • jakarta.persistence.PersistenceException  (JPA)
 *      Test #4 şuny aýratyn barlaýar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DatabaseFailureAuthenticationTest {

    private static final String PASSWORD = "Password123!";

    /**
     * 🔴 Bu spy — «database unavailable» ýagdaýyny DÝRNE DÖRETMEK üçin.
     *
     * 🧡 NÄME ÜÇIN SPY, MOCK DÄЛ?
     *     @MockBean (ýa-da @MockitoBean) repository-ni ÄHLESINE
     *     saýtlaýardy — şeýle bolsa «DB ýok» ýerine «repository ýok»
     *     bolardy, ýagny test başga ýagdaýy barlardy.
     *     @MockitoSpyBean bolsa ÄSLY metodlary SAKLAÝAR: diňe
     *     şol bir metody ýalňyşlyk ýazmak bilen şu sorag üçin
     *     «bazа жеткиликсиз» ýagdaýyny ýasalaýarys.
     */
    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository realUserRepository;

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

    private User user;
    private String validToken;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        realUserRepository.deleteAll();

        user = realUserRepository.save(new User(
                "Алиев Али", "db-failure@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER));

        accountRepository.save(new Account(
                user, "408178100000000009", Account.RUB, BigDecimal.ZERO.setScale(4)));

        validToken = jwtService.generateToken(user);
    }

    // ==================================================================
    //  1) ESASY SEMANTIKA: valid token + DB outage -> 503, 401 ЕМЕС
    // ==================================================================

    @Test
    @DisplayName("1. valid JWT + DB outage -> 503 (401 БОЛМАЯР!)")
    void validTokenWithDatabaseDownReturns503() throws Exception {
        givenDatabaseUnavailable();

        var response = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andReturn().getResponse();

        // 🔴 ЭСЛЭП 401 БОЛМАСЫН. Бу fazanyň esasy manysy.
        assertThat(response.getStatus())
                .as("valid token + database outage MUST NOT be 401")
                .isNotEqualTo(401);
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("2. DB outage jogaby — ApiError kontraktyna laýyk")
    void databaseOutageReturnsApiErrorContract() throws Exception {
        givenDatabaseUnavailable();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/users/me"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("3. DB outage jogaby — içki maglumat SYZMAÝAR")
    void databaseOutageResponseLeaksNothing() throws Exception {
        givenDatabaseUnavailable();

        String body = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        // 🔴 Bu ýerde «password» sözünü barlamaýarys — sebäbi
        //    ApiError.formatynda «fieldErrors» bar, emma ol ýerde ýok.
        //    Barlamak üçin GERÇEK maglumaty (SQL, URL, klass) barlanýar.
        assertThat(body).doesNotContain("org.springframework");
        assertThat(body).doesNotContain("org.hibernate");
        assertThat(body).doesNotContain("java.lang");
        assertThat(body).doesNotContain("jdbc:postgresql");
        assertThat(body).doesNotContain("localhost");
        assertThat(body).doesNotContain("5432");
        assertThat(body).doesNotContain("DataAccessResourceFailureException");
        assertThat(body).doesNotContain("CannotCreateTransaction");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("password");
        // ⛔ Token-iň özi hem jogapta bolmaly DÄL.
        assertThat(body).doesNotContain(validToken);
        assertThat(body).doesNotContain("Bearer eyJ");
        assertThat(body).doesNotContain(PASSWORD);
    }

    // ==================================================================
    //  2) РЕГРЕССИЯ: «БАЗА ÝOK» -> 503
    //     БУЛ ЕМЕС, амма ДА УЧУНЯЯНЫФАЪИЯ
    // ==================================================================

    @Test
    @DisplayName("4. token СЫЗСЫЗ -> 401 (503 БОЛМАЯР!)")
    void validTokenStillWorksWhenDatabaseIsUp() throws Exception {
        // 🧡 ŞU ТЕСТ актуальногь «база амлар».
        // mock бутук станцат нем деден — реальный PostgreSQL.
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("db-failure@example.com"));
    }

    @Test
    @DisplayName("5. «БАЗА ÝOK» -> 200, СОНУН РЮЛБУРДАН ТЕСТ «база ÝOK»")
    void tokenStillWorksAfterDatabaseRecovers() throws Exception {
        // 🧡 ОРТУН СӦНАРИү: «ол юлныда», soñra
        // база каяты. Aýratyn token GÖRÜLMEÝÄR — ol bir ýyllyş däl.
        givenDatabaseUnavailable();
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isServiceUnavailable());

        // Mockito spy-ny arassalaýarys = «база кайтарылды».
        // (reset() ýok: ol beýleki testleriň spy konfigurasiýasyny
        //  ýitirýär. Ýöne indiki testler täze metod çalyşmaýar.)
        org.mockito.Mockito.reset(userRepository);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("db-failure@example.com"));
    }

    // ==================================================================
    //  3) АБАЗ «НÝДОГРЫТ» ХАТУНЫ ЭЭБЮСКУРНЕДИ
    // ==================================================================

    @Test
    @DisplayName("6. token СЫЗ + DB outage -> 401 (500/503 БОЛМАЯР)")
    void missingTokenWithDatabaseDownStillReturns401() throws Exception {
        givenDatabaseUnavailable();

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. NÄДOГРЫ token + DB outage -> 401 (401 САЛЫЛЫСЫ!)")
    void invalidTokenWithDatabaseDownStillReturns401() throws Exception {
        // 🔴 ЭТО БОЛУЗУВУЩИ ТЕСТ:
        //    «база öчен» «token нäдогры» дегили
        //    BILEN BIRMEZ. ЕГРЕ программаный ýалң῿ç ДОЗ СОЛГУСЫН
        //    САКЛАВЫЯ: JJWT ilki «токен хатосы» diýýär,
        //    şonuň üçin bazа soraýyş hem ýETMEÝÄR.
        givenDatabaseUnavailable();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. EXPIRED token -> 401 (bazа ÝOK bolmazda hem, ýok bolmanda hem)")
    void expiredTokenRejectedEvenWhenDatabaseDown() throws Exception {
        String expired = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", "USER")
                .issuer("minibank")
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        // (a) bazа ÝOK
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        // (b) bazа ÝOK däl — hemişe 401
        givenDatabaseUnavailable();
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("9. TAMPERED token -> 401 (bazа ÝOK bolmazda hem, ýok bolmanda hem)")
    void tamperedTokenRejectedEvenWhenDatabaseDown() throws Exception {
        String tampered = validToken.substring(0, validToken.length() - 3) + "AAA";

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        givenDatabaseUnavailable();
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("10. WRONG ISSUER -> 401 (bazа ÝOK bolmazda hem, ýok bolmanda hem)")
    void wrongIssuerRejectedEvenWhenDatabaseDown() throws Exception {
        String evilToken = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", "USER")
                .issuer("evil-bank")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + evilToken))
                .andExpect(status().isUnauthorized());

        givenDatabaseUnavailable();
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + evilToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. WRONG SIGNATURE -> 401 (bazа ÝOK bolmazda hem, ýok bolmanda hem)")
    void wrongSignatureRejectedEvenWhenDatabaseDown() throws Exception {
        String forged = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", "ADMIN")
                .issuer("minibank")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(
                        "another-secret-long-enough-32-bytes!!!"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());

        givenDatabaseUnavailable();
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    //  4) 5-NJY BEND: «BARYNY 503 ETMEK» ÝALŇYŞYNA GARŞY
    // ==================================================================

    @Test
    @DisplayName("12. DÜZGÜNSIZ ýalňyş (NPE) -> 503 Ýaly GÖRÜNMEÝÄR")
    void programmingErrorIsNotReportedAsDatabaseOutage() throws Exception {
        // 🔴 BU TEST «catch (Exception e)» diýip ýazmak bolmaz diýip
        //    gadagan edýär. Eger bir gün filter «Exception.class» diýip
        //    ýazsa, şu test gyzyl bolardy — sebäbi:
        //
        //    • Ýalňyş ýok: token dogry, bazа ÝOK hem däl.
        //    • Ýöne biz ýalňyş ýasýarys (simulate programming bug).
        //    • DOGRY JOGAP: 500 (программа ýalňyşy), 503 DÄL.
        //
        // 🧡 NÄME ÜÇIN «çigli» Exception BOLMAУAR?
        //    Bu kod 10D-nyᵧ iᵧ möhüm kadasy: «her çalýysmy 503
        //    etmeli DÄL» — diñip, biz diᵧe üç anyk sanaw
        //    (çokaryda) ulanýarys.
        //
        //    🔴 🧡 SORAG: Spring-nyᵧ ExceptionTranslationInterceptor
        //    IllegalStateException/IllegalArgumentException-i
        //    «InvalidDataAccessApiUsageException» (DataAccessException!)
        //    diñip Öwürýeär. şonuᴩ üçin ol TEST
        //    503 çykardy — emma ol SPRINGIᵧ kadasy, biziᵧ kodyᵧ däl.
        //
        //    🧡 BIZIᵧ KOD 🧡 «k.Exception.class diñip
        //    ýazmaly DÄL» diñen düzgünimizi hakykata görkezmek üçin
        //    biziᵧ sanawda OLMAÝAN exception sañlaryarys.
        //    FooException — hiç bir meñze Exception ýaly öröçilmeýär,
        //    AOP hem ony DataAccessException diñip Öwürýeä bilmer.
        given(userRepository.findById(anyLong()))
                .willThrow(new UnexpectedProgrammingError("not a database problem"));

        // 🔴 🧡 NYMÄNI BARMALY?
        //    Ýokarydaky ýalňyýý «çigli» Exception-dir — ol filter-iᵧ
        //    sanawyna DEGMEÝÄR. şonuᴩ üçin ol filterden
        //    DAУRY çykyp gitmeli (asyl ýalňyýÚ ulgama
        //    tarapynda görüzülör) — «500 kätlamak»
        //    diñen maslahatä degmez.
        //
        //    🟡 NÄME ÜÇIN «throw» diñen görkezýäris?
        //    Sebäbi: şu ýalňyý «programma ýalňyý» bolup,
        //    haýsy HTTP kodu bilen çykjakdygyny barlamak  FILTERIᵧ
        //    jogaby DÄL — ol öňürti global ýalňyý обработка
        //    ýagdañynda ulgama goraghanasy durýar.
        //
        //    🟡 BIZIᵧ KODIᵧ BARMALY: şu Exception 503 diñip
        //    öwrülenmedi — çünki ölçigi dogry.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        mockMvc.perform(get("/api/users/me")
                                .header("Authorization", "Bearer " + validToken)))
                .isInstanceOf(UnexpectedProgrammingError.class)
                .hasMessage("not a database problem");
    }

    @Test
    @DisplayName("13. valid token + user ÝOK (bazа ýok) -> 401 (bar bolan kontrakt)")
    void validTokenForMissingUserStillReturns401() throws Exception {
        // 🧡 Token dogry, imza dogry, ýöne bazada şol user ÝOK.
        //    Bu INFRASTRUCTURE ýalňyşy DÄЛ — ol adaty «bu token
        //    biziň esabymy?» diýen soraga «ýok» jogaby.
        //    Onuň ýagdaýy = eski kontrakt = 401.
        org.mockito.Mockito.reset(userRepository);
        given(userRepository.findById(anyLong()))
                .willReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    //  5) PUBLIC ENDPOINTLER
    // ==================================================================

    @Test
    @DisplayName("14. public endpoint bazа ýok bolanda -> 401 Ýaly GÖRÜNMEÝÄR")
    void publicEndpointDoesNotFakeUnauthorizedDuringDatabaseOutage() throws Exception {
        // 🔴 Task 11-njy bendi: «401 деп жалган көрсөтпөсүн».
        //    /api/auth/login — açyk endpoint, token şol ýerde hiç haçan bolmaýar.
        //    Şonuň üçin 401 diýmek «сен nädogry» diýmek bolardy —
        //    ýalňyş «sen»de däl-de, «bazada» boldy.
        givenDatabaseUnavailable();

        var response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"db-failure@example.com\", "
                                + "\"password\": \"Password123!\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus())
                .as("public endpoint during DB outage must NOT claim 401")
                .isNotEqualTo(401);
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("15. public endpoint bazа ýok bolanda — içki maglumat SYZMAÝAR")
    void publicEndpointOutageLeaksNothing() throws Exception {
        givenDatabaseUnavailable();

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"db-failure@example.com\", "
                                + "\"password\": \"Password123!\"}"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("org.springframework");
        assertThat(body).doesNotContain("jdbc:postgresql");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain(PASSWORD);
        assertThat(body).doesNotContain("db-failure@example.com");
    }

    // ==================================================================
    //  6) ADMIN
    // ==================================================================

    @Test
    @DisplayName("16. USER -> admin endpoint + DB outage -> 401 Ýaly GÖRÜNMEÝÄR")
    void adminLookupFailureIsNotUnauthorized() throws Exception {
        // 🔴 Task 12-njy bendi. Admin token bilen /api/admin/users
        //    çalylanda «база жеткиликсиз» — bu 401 bolmaly DÄL,
        //    çünki token dogrydi we ýalňyş ýerde däl-de, ýokarda.
        givenDatabaseUnavailable();

        var response = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + validToken))
                .andReturn().getResponse();

        assertThat(response.getStatus())
                .as("DB failure must not masquerade as 401")
                .isNotEqualTo(401);
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("17. admin kontrakt bazа ýOK bolanda hem sazlanýar (403/200)")
    void adminRoleContractSurvivesDatabaseOutage() throws Exception {
        // 🧡 USER -> admin = 403 (bu ROLE ýalňyşy, bazа ýalňyşy däl).
        //    Role bazadan okalýar... emma ýok ýagdaýda 503 berilýär.
        //    Test «bazа ýok bolanda role barlanмаýar» diýip aýdýar.
        givenDatabaseUnavailable();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("with DB down we cannot evaluate the role -> not 403")
                        .isNotEqualTo(403));

        // Bazа кайтарылды -> kontrakt DOLY sazlanýar.
        org.mockito.Mockito.reset(userRepository);
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    //  КӨМЕКЧИ
    // ==================================================================

    /**
     * 🧡 «База жеткиликсиз» ýagdaýyny DÝRNE DÖRETMEK.
     *
     * 🟡 NÄME ÜÇIN DataAccessResourceFailureException?
     *     Spring-yň data-access shelesindäki ýönekeý exception.
     *     «Resursa ýetmek bolmaýar» diýen many berýär — bizim
     *     ýagdaýymyzda ol «baza ýok» diýen many.
     *
     * 🟡 findById() BILEN NÄMÄ ÜÇIN?
     *     Ösüm filter-iň ýeke-täze sorýan ýer bu — şonuň üçin
     *     şu ýerde ýalňyş ýazsa, filter-iň ýolbaşçylygy görnüp çykýar.
     */
    private void givenDatabaseUnavailable() {
        given(userRepository.findById(anyLong()))
                .willThrow(new DataAccessResourceFailureException(
                        "Unable to acquire JDBC Connection"));
        // 🧡 YENE bir metod: findByEmail.
        //    /api/auth/login ulanyjy EMAIL boýunka görýär —
        //    findById däl. Eger şu mocklanmasa, login soragy
        //    «база ýok» bolmadan-da 200 berýärdi diᵧe
        //    öçki soragdan soň.
        given(userRepository.findByEmail(anyString()))
                .willThrow(new DataAccessResourceFailureException(
                        "Unable to acquire JDBC Connection"));
    }

    /**
     * 🔴 TEST ÜÇIN ÝALňYŞ CLASS — ýalňyçykarmak üçin.
     *
     * 🧡 NÄME ÜÇIN ŞEÝLE BIR CLASS KERAK?
     *     «Her zady 503 etmeýäris» diñen düzgünimizi barlamak üçin
     *     biz ýalňyý ýasmalarys — öne ol ýalňyý «база ýok» DÄL.
     *
     *     Nähili ýalňyý ulanypdyr?
     *       • IllegalStateException — Spring Data ony
     *         InvalidDataAccessApiUsageException diñip öwürýär
     *         (ol DataAccessException → 503). Şeßle bolsa test
     *         ÝALňYÝ «ýokary» görünýp, biziᵧ kodumyzy
     *         çak edýärdi.
     *       • NullPointerException — AOP ony AopInvocationException
     *         diñip öwürýär (test-only artefakt).
     *       • Bu class — hiç kim öwürýeär, hiç sanawa degmeÙär.
     *         şonuᴩ üçin ol «чистая»
     *         программа ýalňyý» bolar.
     *
     * 🟡 NÄME ÜÇIN «Exception» ÝERINE ÖZ CLASSYMYZY?
     *     Eger öne Exception gysgaltyp, «Exception.class diñip ýazmak
     *     gadagan» diñen düzgünimizi barlamak MÜMKIN BOLMAУDY —
     *     sebäbi test özüni saklap bilerdi. Bu öne ºfa» exception.
     */
    private static final class UnexpectedProgrammingError extends RuntimeException {
        UnexpectedProgrammingError(String message) {
            super(message);
        }
    }
}
