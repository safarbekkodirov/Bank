package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtService;

/**
 * ==========================================================================
 *  OBSERVABILITY — PHASE 10C
 * ==========================================================================
 *
 *  🧠 БУЛ ТЕСТ НИ ÜÇIN?
 *
 *      Actuator — ҮЧҮНЧҮ тарап китепканасы. Эке шатлы:
 *          1) бизики: SecurityConfig, application.yml, filter
 *          2) аныкы:   Spring Boot'тун actuator endpoints-i
 *
 *      «Spring Boot 3.6'da health endpoint-i üýtgedi» ýaly bir
 *      ýalňyşlyk diňe TEST ýalňyşlyk. Aýratyn-da şeýle ýagdaýlar
 *      bolýar:
 *          · Actuator goşuldy — ýöne /actuator/env AÇYK galdy
 *            («meýilnamalaýyn» diýip gutarýardy — чабуççulyk!)
 *          · health details «always» — jogapda jdbc: URL çykýar
 *          · Actuator goşuldy — ýöne /api/** ачылыp галды
 *
 *      Şonуň üçin biz ýazujymyz «şu endpoint şu ýagdaýda шу netije».
 *
 * ==========================================================================
 *  🔴 ÝUZDU SENSITIVE-ENDPOINT БАРЛАГЫ: ДЕНЫЛЛ ДӘЛ, ЭНЕМ?
 * ==========================================================================
 *      /actuator/env, /actuator/heapdump ... — olar «exposure.include»
 *      sebäpli АЧЫЛМАЯР (404/403). Emma bir gün näbelli bir adam
 *      application.yml-fa ulanyp, ýa-da «actuator» diýen zol ýalňyşlyk
 *      bilen açyp berse — biz ЕҮЕ аңлатmaly. Şonuň üçin test admin
 *      token bilen-de «гирМЭЙӘР» diýip barlaýар.
 *
 * ==========================================================================
 *  🔴 ОШУК БОЛМАК: sanawlar «мык» ölçeg üçин ýöne şеýle ýalňyşлык дәл.
 *  Actual ýalňyш — .env-däки АНИКИ баһаларын (паролн, JWT
 *  syrн) jogapda ýа-da logda ýüze çykmagy. Шоңуң üçін «password»
 *  диэн СӨЗ дәл, ё һақықы ГЫММАТ барланары.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObservabilityTest {

    private static final String HEALTH_PATH = "/actuator/health";
    private static final String METRICS_PATH = "/actuator/metrics";
    private static final String API_DOCS_PATH = "/v3/api-docs";

    /** Spring Boot 3.x-ң HTTP server metrikasynyn һақықы ады. */
    private static final String HTTP_REQUESTS_METRIC = "http.server.requests";

    /**
     * 🔴 Bu sanaw «мық» — SecurityConfig-де permitAll дийilýän
     *     ЎАЛЊЫШ ýazylsa, test ғызыл болаýар (testiң мәны).
     */
    private static final String[] PUBLIC_API_PATHS = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/metrics",
    };

    /** 🔴 Bu sanaw һич һачан ачық болмалы дәл. */
    private static final String[] SENSITIVE_ACTUATOR_PATHS = {
            "/actuator",
            "/actuator/env",
            "/actuator/configprops",
            "/actuator/heapdump",
            "/actuator/threaddump",
            "/actuator/loggers",
            "/actuator/beans",
            "/actuator/mappings",
            "/actuator/scheduledtasks",
            "/actuator/sessions",
    };

    /** 🔴 Ýапық болмалы гәлән ескі endpointлер (регресс). */
    private static final String[] PROTECTED_API_PATHS = {
            "/api/users/me",
            "/api/accounts/me",
            "/api/accounts/me/transactions",
            "/api/admin/users",
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

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

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User(
                "Admin", "admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN));

        User user = userRepository.save(new User(
                "Ali A", "ali@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER));

        accountRepository.save(new Account(
                user, "408178100000000001", Account.RUB, BigDecimal.ZERO.setScale(4)));

        userToken = jwtService.generateToken(user);
        adminToken = jwtService.generateToken(admin);
    }

    // ==================================================================
    //  1) HEALTH
    // ==================================================================

    @Test
    @DisplayName("1. /actuator/health -> 200, status=UP (tokensiz — monitoring açyk)")
    void healthIsPublicAndUp() throws Exception {
        mockMvc.perform(get(HEALTH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("2. /actuator/health — Authorization header ÄHLISIZ işleýär")
    void healthNeedsNoTokenAtAll() throws Exception {
        // 🧡 Bu ýerde «Bearer...» diýen header һич һачан goşулмаяр.
        //    Ýöne komment hökmünde goşulýar: eger filter ýok bolsa,
        //    һич һачан ýазылмаяр — logyň «Authorization: Bearer ...»
        //    bolmagyny yzarlaýarys.
        mockMvc.perform(get(HEALTH_PATH))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("3. /actuator/health — DB ады, URL, пароль, файл ýoly СИЗМАÝAR")
    void healthResponseLeaksNothing() throws Exception {
        String body = mockMvc.perform(get(HEALTH_PATH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 🧡 show-details: never -> göni «details» bölümi ýок.
        assertThat(body).doesNotContain("details");
        assertThat(body).doesNotContain("components");

        // show-components: never -> içki bölümleriň («db») ады ýок.
        assertThat(body).doesNotContain("\"db\"");

        // ⛔ Bularyň hemmesi болмаса, «health» diýen ýalňyşлык ýок.
        assertThat(body).doesNotContain("jdbc:postgresql");
        assertThat(body).doesNotContain("PostgreSQL");
        assertThat(body).doesNotContain("5432");
        assertThat(body).doesNotContain(".env");
        assertThat(body).doesNotContain("password");
    }

    @Test
    @DisplayName("4. /actuator/health/db — DB komponenti ýALŇYŞSA ýene maglumat ýок")
    void healthComponentPathLeaksNothingEither() throws Exception {
        // 🧡 Spring Boot bu ýoly «bileşik» görnüşde açýar
        //    (/actuator/health/{*path}). Ol ýа 404 berýär (аçyk
        //    edilmеýär), ýа-да diňe «status» berýär. Ики ýagdaýda-da
        //    «maglumat ýок» hökmany — шоңуң üçin икини һәм барлаярыs.
        var response = mockMvc.perform(get("/actuator/health/db"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isIn(200, 404);

        String body = response.getContentAsString();
        assertThat(body).doesNotContain("jdbc:postgresql");
        assertThat(body).doesNotContain("PostgreSQL");
        assertThat(body).doesNotContain("username");
        assertThat(body).doesNotContain("password");
    }

    // ==================================================================
    //  2) METRICS
    // ==================================================================

    @Test
    @DisplayName("5. /actuator/metrics -> 200, sanaw boş дәл")
    void metricsEndpointIsPublicAndNotEmpty() throws Exception {
        String body = mockMvc.perform(get(METRICS_PATH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode names = objectMapper.readTree(body).path("names");
        assertThat(names.isArray()).isTrue();
        assertThat(names.size())
                .as("actuator must expose at least the metrics it collects")
                .isGreaterThan(5);
    }

    @Test
    @DisplayName("6. http.server.requests metrigi bar (Spring Boot-ның оwn ады)")
    void httpServerRequestsMetricExists() throws Exception {
        String body = mockMvc.perform(get(METRICS_PATH + "/" + HTTP_REQUESTS_METRIC))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode metric = objectMapper.readTree(body);
        assertThat(metric.path("name").asText()).isEqualTo(HTTP_REQUESTS_METRIC);
        assertThat(metric.path("measurements").isArray()).isTrue();
    }

    @Test
    @DisplayName("7. http.server.requests — API sorgudan soň ARTÝAR")
    void httpRequestCounterGrowsAfterApiCalls() throws Exception {
        // 🔴 Бу — PHASE 10C-ның «metrics işlеýärmi?» diýän
        //    soragyna jogap. Metrika bolmagy ýeterlik дәл — ол AÝLANMALY.

        int before = countHttpRequests("/api/accounts/me");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/accounts/me")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk());
        }

        int after = countHttpRequests("/api/accounts/me");

        assertThat(after)
                .as("3 request yzarlanandan soň sanaw artmaly")
                .isEqualTo(before + 3);
    }

    /** @param uri şol endpoint üçin «http.server.requests»-iň sanawy. */
    private int countHttpRequests(String uri) throws Exception {
        String body = mockMvc.perform(get(METRICS_PATH + "/" + HTTP_REQUESTS_METRIC
                        + "?tag=uri:" + uri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode measurement : objectMapper.readTree(body).path("measurements")) {
            if ("COUNT".equals(measurement.path("statistic").asText())) {
                return (int) measurement.path("value").asDouble();
            }
        }
        throw new AssertionError("No COUNT measurement in " + body);
    }

    // ==================================================================
    //  3) ACTUATOR HOWPSUZLYGY
    // ==================================================================

    @Test
    @DisplayName("8. duýgur actuator endpointleri ANONYMOUS bolmaga AÇYK ДӘЛ")
    void sensitiveActuatorEndpointsAreNotPublic() throws Exception {
        for (String path : SENSITIVE_ACTUATOR_PATHS) {
            mockMvc.perform(get(path))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("%s must NOT be public (anonymous)", path)
                            .isNotEqualTo(200));
        }
    }

    @Test
    @DisplayName("9. duýgur actuator endpointleri ADMIN token bilen-de girMEÝӘР")
    void sensitiveActuatorEndpointsAreForbiddenEvenForAdmin() throws Exception {
        // 🧡 «hasRole("ADMIN")» ýeterlik дәл — eger şeýle ýazylanda,
        //    admin-e аçyk bolardy. Биз «denyAll» ulanýarys: ýagny
        //    bu endpointler HEMMEKI üçin ýапуқ.
        for (String path : SENSITIVE_ACTUATOR_PATHS) {
            int status = mockMvc.perform(get(path)
                            .header("Authorization", "Bearer " + adminToken))
                    .andReturn().getResponse().getStatus();

            assertThat(status)
                    .as("%s must NOT be reachable even with an ADMIN token", path)
                    .isNotEqualTo(200);
        }
    }

    @Test
    @DisplayName("10. аçyk bolmaly actuator endpointleri hemişe аçyk (regressiya)")
    void intendedActuatorEndpointsStayPublic() throws Exception {
        for (String path : PUBLIC_API_PATHS) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("11. actuator sanawy (/actuator) аçyk ДӘЛ")
    void actuatorRootIsNotPublic() throws Exception {
        // 🧡 «/actuator» — actuator-ның barlan doly sanawyny berýär.
        //    Биз ол ýoly аçmadyк: sanawy görmek üçin «sorag» bermek
        //    gerek, ýöne биз diňe ýazylan ýollary аçdyк.
        mockMvc.perform(get("/actuator"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("/actuator must not be public")
                        .isNotEqualTo(200));
    }

    // ==================================================================
    //  4) ESKI API — KORAGY UZGYRADYLMALY ДӘЛ
    // ==================================================================

    @Test
    @DisplayName("12. ескі endpointler tokensiz — ÝENE 401")
    void existingApiStillRequiresToken() throws Exception {
        for (String path : PROTECTED_API_PATHS) {
            mockMvc.perform(get(path))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("%s must stay protected", path)
                            .isEqualTo(401));
        }
    }

    @Test
    @DisplayName("13. nädogry token — ÝENE 401")
    void invalidTokenIsStillUnauthorized() throws Exception {
        for (String path : PROTECTED_API_PATHS) {
            mockMvc.perform(get(path).header("Authorization", "Bearer not-a-real-token"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("%s must reject an invalid token", path)
                            .isEqualTo(401));
        }
    }

    @Test
    @DisplayName("14. USER -> admin endpoint — 403")
    void regularUserIsStillForbiddenFromAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("15. ADMIN -> admin endpoint — 200 (korag işlеýär)")
    void adminStillReachesAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("16. own endpointler tokgendä ýaly (200) — actuator һич näme üýtgetmedi")
    void normalUserEndpointsStillWork() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ali@example.com"));

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("17. OpenAPI/Swagger ACTUATOR ÝOK, olaryň ýagdaýy ÜÝTGEMEDI")
    void openApiDocumentationIsUnaffected() throws Exception {
        mockMvc.perform(get(API_DOCS_PATH))
                .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        // 🧡 Actuator endpointleri OpenAPI resminamasyna
        //    goşylmaýар — olar «dokumentasiýa» дәл, «monitoring».
        String document = mockMvc.perform(get(API_DOCS_PATH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(document).doesNotContain("/actuator/health");
        assertThat(document).doesNotContain("/actuator/metrics");
    }

    // ==================================================================
    //  5) ÝALŇYŞ SYR СИЗМАСЫ
    // ==================================================================

    @Test
    @DisplayName("18. һақықы ýalňyşlar (пароль, JWT сыр) jogapda СИЗМАÝAR")
    void realSecretsNeverAppearInPublicResponses() throws Exception {
        // 🔴 БУ TEST «password» diýen СӨЗИ barlamaýар.
        //    «RegisterRequest» DTO-synda «password» meýdan bar —
        //    ol dokumanda ГӨРҮНМЕЛI (bu ýerde «ýalňyşlyk» ýok).
        //    Биз baryp bolmadyk zady barlaýarys: .env-däki АНИКИ baha.

        String dbPassword = environment.getProperty("spring.datasource.password");
        String jwtSecret = environment.getProperty("jwt.secret");

        assertThat(dbPassword)
                .as("test environment must resolve the real DB password from .env")
                .isNotBlank();
        assertThat(jwtSecret)
                .as("test environment must resolve the real JWT secret from .env")
                .isNotBlank();

        // 🔴 ACTUATOR jogaplary — bu ýerde KÄHLI sözler çykmaly дәл.
        //    Actuator бизге ýazmaýar, ol diňe sanaýar: hiç bir
        //    jogapda «Authorization», «password», «$2a$» ýaly söz
        //    bolmaly дәл.
        String[] actuatorBodies = {
                bodyOf(HEALTH_PATH),
                bodyOf(METRICS_PATH),
                bodyOf("/actuator/info"),
                bodyOf(METRICS_PATH + "/" + HTTP_REQUESTS_METRIC),
        };

        for (String body : actuatorBodies) {
            assertThat(body).doesNotContain(dbPassword);
            assertThat(body).doesNotContain(jwtSecret);
            assertThat(body).doesNotContain("Bearer eyJ");
            assertThat(body).doesNotContain("passwordHash");
            assertThat(body).doesNotContain("$2a$");
            assertThat(body).doesNotContain("Authorization");
            assertThat(body).doesNotContain("password");
        }

        // 🧡 OpenAPI resminamasy — bu ýerde АЙРАТЫН düşündiriş.
        //    «Authorization: Bearer <token>» SÖZI dokumentasiýada
        //    BAR: ol «мык» — OpenApiConfig.description() «Ар бир
        //    суроо Authorization: Bearer <token> header'ин талап
        //    кылат» diýip ÝAZÝAR. Бул ҳақықы токен ЕМЕС, ol
        //    форматıň DÜZGÜNI. Şonuň üçin «Authorization: Bearer»
        //    sözüni bu ýerde barlamaýarys — ýalňyş pozitiw.
        //    Биз barlaýan ýat zat — ANIKI bahalar we ANIKI tокен.
        String apiDocs = bodyOf(API_DOCS_PATH);

        assertThat(apiDocs).doesNotContain(dbPassword);
        assertThat(apiDocs).doesNotContain(jwtSecret);
        assertThat(apiDocs).doesNotContain("Bearer eyJ");
        assertThat(apiDocs).doesNotContain("passwordHash");
        assertThat(apiDocs).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("19. bizniň JWT tokenimiz we parolimiz — actuator jogaplarynda СИЗМАÝAR")
    void ownTokenAndPasswordDoNotLeakThroughActuator() throws Exception {
        String[] bodies = {
                bodyOf(HEALTH_PATH),
                bodyOf(METRICS_PATH + "/" + HTTP_REQUESTS_METRIC),
        };

        for (String body : bodies) {
            assertThat(body).doesNotContain(userToken);
            assertThat(body).doesNotContain(adminToken);
            assertThat(body).doesNotContain(PASSWORD);
        }
    }

    // ==================================================================
    //  KӨМЕКЧИ
    // ==================================================================

    private String bodyOf(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
