package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.config.OpenApiConfig;

/**
 * ==========================================================================
 *  OPENAPI / SWAGGER DOCUMENTATION — PHASE 10A
 * ==========================================================================
 *
 *  🧠 БУЛ ТЕСТ ЭМНЕ КЕРЕК?
 *
 *  springdoc — ҮЧҮНЧҮ ТАРАП КИТЕПКАНАСЫ. Ошондуктан биздин
 *  код өзгөргөндө эле эмес, АЛ ар кандай өзгөрсө да «эмнеси»
 *  үнсүлдөйт.
 *
 *  Кийинки бир күн:
 *      • springdoc жаңы версияга которулса → /v3/api-docs 500 берсе,
 *      • биз Controller'ды `@Operation` бөтүн өчүрсөк,
 *      • DTO'га `@Schema(description=...)` кетсе,
 *      • SecurityConfig'те `/v3/api-docs/**` ачылмаса (унут калса)
 *  ДАЙЫН КАЛЫП КОЛДОНМО ӨЧ ҮЧҮН. Бул тест аны СУРОМУ
 *  БЕЛГИЛЕЙДИ.
 *
 *  🧡 «ЖАЗЫУ ТЕСТИ» ДЕМЕК ЭМЕСТИР: биз кээ бир анык сөз жазып
 *  коюбуз («Mini Bank API») — ал эле кээде өзгөрүшү МҮМКҮН.
 *  Бирок «сөз болсо» деп текшерүү — документ жөнүндө эмес,
 *  КОД жөнүндө сыноо: суроо чыныгы 200 кайтарабы, чыныгы
 *  bean'ден келгенби деп.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    private static final String API_DOCS_PATH = "/v3/api-docs";
    private static final String SWAGGER_UI_PATH = "/swagger-ui/index.html";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================================================================
    //  1) СҮРӨТ ТОЛУҢ БОЛУБЫ?
    // ==================================================================

    @Test
    @DisplayName("1. /v3/api-docs -> 200 (токенсиз — документация ачык)")
    void apiDocsIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get(API_DOCS_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("2. /v3/api-docs -> JSON БОШ ЭМЕС, OpenAPI 3 сүрөтү")
    void apiDocsReturnsNonEmptyOpenApiDocument() throws Exception {

        String body = mockMvc.perform(get(API_DOCS_PATH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank();

        JsonNode document = objectMapper.readTree(body);

        // 🧡 OpenAPI 3.0 сүрөтү — «swagger: 2.0» ЭМЕС (эски формат).
        assertThat(document.path("openapi").asText()).startsWith("3.");
        // 🔴 Эң маанилүү талаа: paths бөлүмү БОШ БОЛБОШУ керек.
        //    Эгер бир күнү бардык Controller `@Operation` бөтүн
        //    өчүрүлсө, бул жерде КЫЗЫЛ болуп чыгат.
        assertThat(document.path("paths").isMissingNode()).isFalse();
        assertThat(document.path("paths").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("3. info: title «Mini Bank API», version «1.0.0»")
    void apiMetadataIsCorrect() throws Exception {
        JsonNode document = readApiDocs();

        assertThat(document.at("/info/title").asText())
                .isEqualTo("Mini Bank API");
        assertThat(document.at("/info/version").asText())
                .isEqualTo("1.0.0");
        // 🧡 Сүрөттөмө БОШ болбош керек — «бул эмне?» суроосуна
        //    жооп берилиши керек.
        assertThat(document.at("/info/description").asText()).isNotBlank();
    }

    // ==================================================================
    //  2) КООПСУЗДУК СХЕМАСЫ (JWT Bearer)
    // ==================================================================

    @Test
    @DisplayName("4. bearerAuth: HTTP / bearer / JWT схемасы бар")
    void bearerJwtSecuritySchemeIsDocumented() throws Exception {
        JsonNode document = readApiDocs();

        JsonNode scheme = document.at("/components/securitySchemes/"
                + OpenApiConfig.SECURITY_SCHEME_NAME);

        // 🛡️ Бул жерде биз реалдуу коопсуздук ЭМЕС, анын СИПАТАМЫН
        //    текшеребиз. Чыныгы текшеруү JwtAuthenticationFilter'де.
        assertThat(scheme.isMissingNode()).isFalse();
        assertThat(scheme.path("type").asText()).isEqualToIgnoringCase("http");
        assertThat(scheme.path("scheme").asText()).isEqualToIgnoringCase("bearer");
        assertThat(scheme.path("bearerFormat").asText())
                .isEqualToIgnoringCase("JWT");
    }

    @Test
    @DisplayName("5. корголгон endpoint'лер bearerAuth талап кылат")
    void protectedOperationsDeclareSecurityRequirement() throws Exception {
        JsonNode document = readApiDocs();

        // 🧡 «Королгон» деген эмне? Эндпоинт @SecurityRequirement
        //    алып, OpenAPI'де `security: [{bearerAuth: []}]` болгон.
        assertThat(document.at("/paths/~1api~1accounts~1me/get/security").isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1users~1me/get/security").isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1admin~1users/get/security").isMissingNode())
                .isFalse();

        // 🟡 АЧЫК эндпоинттер (register/login) токен ТАЛАП КЫЛМАЙДЫ:
        //     алар токен БЕРЭТ. Документацияда да «талап кылбайт»
        //     деш көрүнүшү керек.
        assertThat(document.at("/paths/~1api~1auth~1login/post/security").size())
                .as("login must NOT require a token - it ISSUES one")
                .isZero();
        assertThat(document.at("/paths/~1api~1auth~1register/post/security").size())
                .as("register must NOT require a token")
                .isZero();
    }

    // ==================================================================
    //  3) БААРЫ ENDPOINT'ТЕР СҮРӨТТЕ БАРЫ
    // ==================================================================

    @Test
    @DisplayName("6. Auth + User endpoint'тери сүрөттө бар")
    void authAndUserEndpointsAreDocumented() throws Exception {
        JsonNode document = readApiDocs();

        assertPostExists(document, "/api/auth/register");
        assertPostExists(document, "/api/auth/login");
        assertGetExists(document, "/api/users/me");
    }

    @Test
    @DisplayName("7. Account endpoint'тери сүрөттө баары")
    void accountEndpointsAreDocumented() throws Exception {
        JsonNode document = readApiDocs();

        assertPostExists(document, "/api/accounts");
        assertGetExists(document, "/api/accounts/me");
        assertPostExists(document, "/api/accounts/me/deposit");
        assertPostExists(document, "/api/accounts/me/withdraw");
        assertPostExists(document, "/api/accounts/me/transfer");
        assertGetExists(document, "/api/accounts/me/transactions");
    }

    @Test
    @DisplayName("8. Admin endpoint'тери сүрөттө баары")
    void adminEndpointsAreDocumented() throws Exception {
        JsonNode document = readApiDocs();

        assertGetExists(document, "/api/admin/users");
        assertGetExists(document, "/api/admin/users/{id}");
        assertGetExists(document, "/api/admin/accounts");
        assertGetExists(document, "/api/admin/accounts/{id}");
        assertGetExists(document, "/api/admin/accounts/{accountId}/transactions");
    }

    // ==================================================================
    //  4) DTO СХЕМАЛАРЫ ЖАНА ТААЛАРДЫН СҮРӨТТӨМӨСҮ
    // ==================================================================

    @Test
    @DisplayName("9. негизги DTO схемалары бар")
    void dtoSchemasAreDocumented() throws Exception {
        JsonNode schemas = readApiDocs().path("components").path("schemas");

        // 🧡 Биз тиктеген DTO'лорубуз. Аттары чыныгы Java класстын
        //    аты менен БИРДИК болушу керек.
        assertThat(schemas.has("RegisterRequest")).isTrue();
        assertThat(schemas.has("LoginRequest")).isTrue();
        assertThat(schemas.has("AuthResponse")).isTrue();
        assertThat(schemas.has("AccountResponse")).isTrue();
        assertThat(schemas.has("MoneyOperationRequest")).isTrue();
        assertThat(schemas.has("MoneyOperationResponse")).isTrue();
        assertThat(schemas.has("TransferRequest")).isTrue();
        assertThat(schemas.has("TransferResponse")).isTrue();
        assertThat(schemas.has("TransactionResponse")).isTrue();
        assertThat(schemas.has("AdminUserResponse")).isTrue();
        assertThat(schemas.has("AdminAccountResponse")).isTrue();
        assertThat(schemas.has("ApiError")).isTrue();
        assertThat(schemas.has("PageResponseTransactionResponse")).isTrue();
        assertThat(schemas.has("PageResponseAdminUserResponse")).isTrue();
        assertThat(schemas.has("PageResponseAdminAccountResponse")).isTrue();

        // 🧡 PageResponse ГЕНЕРИК (PageResponse<T>) — ошондуктан
        //    springdoc ар бир колдонулган ТИП ҮЧҮН өз схемасын
        //    түзөт: PageResponseTransactionResponse,
        //    PageResponseAdminUserResponse, ...
        //    🧡 БУЛ ЧЫНЫГЫ OpenAPI стандарты: generic схеманы
        //    «толук» кылуу үчүн ар бир конкреттүү тип
        //    көрсөтүлөт. Ошондуктан `content.items` — конкреттүү
        //    типке $REF менен көрсөтүлөт.
        //    🔴 ЭГЕР мындай көрсөтүлбөсө, `content: {}` (бос объект)
        //    болуп чыкмак эле — клиент эмне экенин билбей калмак эле.
        assertThat(schemas.at(
                "/PageResponseTransactionResponse/properties/content/items/$ref")
                .asText()).endsWith("TransactionResponse");
    }

    @Test
    @DisplayName("10. ApiError: чыныгы таалары (status/message/timestamp/path)")
    void apiErrorSchemaUsesRealFieldNames() throws Exception {
        JsonNode apiError = schema("ApiError");

        // 🔴 АТТАРЫ КОДДОН АЛЫНДЫ: GlobalExceptionHandler.java да
        //    ApiError.of(status, error, message, path) деп жазат.
        //    Эгер биз «errorCode» деп ойлоп жазсак — чыныгы
        //    контрактты БУЗУП, бардык 305 тест уналып калар эле.
        assertThat(apiError.path("properties").has("status")).isTrue();
        assertThat(apiError.path("properties").has("message")).isTrue();
        assertThat(apiError.path("properties").has("timestamp")).isTrue();
        assertThat(apiError.path("properties").has("path")).isTrue();
        assertThat(apiError.path("properties").has("error")).isTrue();
        assertThat(apiError.path("properties").has("fieldErrors")).isTrue();

        // 🧡 Полялы каталардын тизими да сүрөттө болушу керек.
        assertThat(apiError.path("properties").at("/fieldErrors/items/$ref").asText())
                .endsWith("FieldViolation");
    }

    @Test
    @DisplayName("11. PageResponse: чыныгы 7 таасы жана чектелген pagination")
    void pageResponseSchemaMatchesRealContract() throws Exception {
        // 🧡 Ар бир колдонулган тип үчүн өз схемасы бар
        //    (generic). Биз биринчисин текшеребиз — таалары
        //    бардыкында БИРДЕШ.
        JsonNode properties = schema("PageResponseTransactionResponse").path("properties");

        // 🧡 Аттары PageResponse.java даги record кантип — ошол
        //    тартипте. Биз аларды «өзгөртүү» деп үлгөлөш МҮМКҮН
        //    ЭМЕС (клиент аларга көнүшкөн).
        assertThat(properties.has("content")).isTrue();
        assertThat(properties.has("page")).isTrue();
        assertThat(properties.has("size")).isTrue();
        assertThat(properties.has("totalElements")).isTrue();
        assertThat(properties.has("totalPages")).isTrue();
        assertThat(properties.has("first")).isTrue();
        assertThat(properties.has("last")).isTrue();

        // 🧡 Чектелген көлөм — чыныгы транзакцияда MAX_SIZE = 100.
        assertThat(properties.at("/size/maximum").asInt()).isEqualTo(100);
        assertThat(properties.at("/size/default").asInt()).isEqualTo(20);
        assertThat(properties.at("/page/default").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("12. валютация: 18 цифра, пароль 8..72, сүрөттөмөлөр")
    void validationConstraintsAreVisible() throws Exception {
        JsonNode transfer = schema("TransferRequest");
        JsonNode register = schema("RegisterRequest");

        // 🧡 Эндпоинт өзгөрүп, чектелүүн калтарып жатабы?
        //    18 цифра — TransferRequest'теги @Pattern("\\d{18}")'ден.
        assertThat(transfer.at("/properties/toAccountNumber/maxLength").asInt())
                .isEqualTo(18);
        assertThat(transfer.at("/properties/toAccountNumber/pattern").asText())
                .isNotBlank();

        // 🧡 8..72 — RegisterRequest'теги @Size(min = 8, max = 72)'ден.
        assertThat(register.at("/properties/password/minLength").asInt()).isEqualTo(8);
        assertThat(register.at("/properties/password/maxLength").asInt()).isEqualTo(72);

        // 🧡 Таанын сүрөттөмөсү — «эмне күтүлөт?» деген суроого жооп.
        assertThat(register.at("/properties/email/description").asText())
                .isNotBlank();
    }

    @Test
    @DisplayName("13. үлгүлөр (examples) сүрөттө бар")
    void examplesArePresent() throws Exception {
        JsonNode schemas = readApiDocs().path("components").path("schemas");

        // 🧡 Register — анын мисалы RegisterRequest схемасында.
        //    🧡 СЕРЕБИЯ: JSON объекти катарында (`toString()`), сап
        //    катарында эмес. Springdoc JSON мисалын объектке айлант.
        assertThat(schemas.at("/RegisterRequest/example").toString())
                .contains("fullName", "email", "password");
        assertThat(schemas.at("/LoginRequest/example").toString())
                .contains("email", "password");
        assertThat(schemas.at("/MoneyOperationRequest/example").toString())
                .contains("amount");
        assertThat(schemas.at("/TransferRequest/example").toString())
                .contains("toAccountNumber");

        // 🧡 Эсеп номурунун мисалы чыныгы ВАЛИДАЦИЯГА дал келет:
        //    TransferRequest.toAccountNumber — так 18 цифра.
        String accountExample = schemas
                .at("/TransferRequest/properties/toAccountNumber/example").asText();
        assertThat(accountExample).hasSize(18).containsOnlyDigits();
    }

    @Test
    @DisplayName("14. HTTP коддор: чыныгы жооптор гана документтелген")
    void responseCodesAreDocumented() throws Exception {
        JsonNode document = readApiDocs();

        // 🧡 Register: 201 (жаратылды), 400 (валидация), 409 (катталган).
        JsonNode register = document.at("/paths/~1api~1auth~1register/post/responses");
        assertThat(register.has("201")).isTrue();
        assertThat(register.has("400")).isTrue();
        assertThat(register.has("409")).isTrue();

        // 🧡 Login: 200 (токен), 401 (туура эмес), 400 (валидация).
        JsonNode login = document.at("/paths/~1api~1auth~1login/post/responses");
        assertThat(login.has("200")).isTrue();
        assertThat(login.has("400")).isTrue();
        assertThat(login.has("401")).isTrue();

        // 🧡 Админ: 403 (USER) жана 401 (токенсиз) КӨРСӨТҮЛҮҨ.
        JsonNode adminUsers = document.at("/paths/~1api~1admin~1users/get/responses");
        assertThat(adminUsers.has("401")).isTrue();
        assertThat(adminUsers.has("403")).isTrue();
    }

    // ==================================================================
    //  5) SWAGGER UI
    // ==================================================================

    @Test
    @DisplayName("15. /swagger-ui/index.html -> 200 (токенсиз)")
    void swaggerUiIsAccessible() throws Exception {
        mockMvc.perform(get(SWAGGER_UI_PATH))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("16. /swagger-ui.html -> башка баракка баштайт")
    void swaggerUiRedirectsToConfigUrl() throws Exception {
        // 🔴 Эгер Swagger UI'ге токен талаш кылган болсо, «Authorize»
        //    иштемейт. Документация ТОКЕНСИЗ ачылышы керек.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    // ==================================================================
    //  6) КООПСУЗДУК ӨЗГӨРГӨН ЖОБУ
    // ==================================================================

    @Test
    @DisplayName("17. документация ачык болгону менен API жабык")
    void documentationIsOpenButApiStaysProtected() throws Exception {
        // 🧡 Эң маанилүү тест. springdoc кошулганда чын эле коргоо
        //    «мык» болуп калбайбы деп текшеребиз.

        // (a) /v3/api-docs — токенсиз ачык (документация).
        mockMvc.perform(get(API_DOCS_PATH))
                .andExpect(status().isOk());

        // (b) Биздин API — токенсиз ЖАБЫК. Swagger кошулганда
        //     бул 401 болууга ТАЛЫК болгон.
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("18. Swagger саясаты /api/** коргоосуна ТӨКӨН ЧЫКПАЙТ")
    void swaggerPolicyDoesNotLeakIntoApiPaths() throws Exception {
        // 🔴 БУЛ ТЕСТ «кел кириш» сыноосу. Бир адам мына ошондой
        //    жазып, чокум «/api/**» ичине кошсо — эгер биз аны
        //    байкабасак, бардык API ачылып калмак эле.
        //    Биз ачык жолдор ТОЛУК /v3/ жана /swagger-ui/ гана.
        mockMvc.perform(get("/api/does-not-exist-at-all"))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    //  ЖАРДАМЧЫЛАР
    // ==================================================================

    private JsonNode readApiDocs() throws Exception {
        String body = mockMvc.perform(get(API_DOCS_PATH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode schema(String name) {
        try {
            JsonNode schema = readApiDocs().at("/components/schemas/" + name);
            assertThat(schema.isMissingNode())
                    .as("Schema «%s» must exist in the OpenAPI document", name)
                    .isFalse();
            return schema;
        } catch (Exception ex) {
            throw new AssertionError("Could not read OpenAPI document", ex);
        }
    }

    private void assertPostExists(JsonNode document, String path) {
        assertThat(document.at("/paths" + toJsonPointer(path) + "/post").isMissingNode())
                .as("POST %s must be documented", path)
                .isFalse();
    }

    private void assertGetExists(JsonNode document, String path) {
        assertThat(document.at("/paths" + toJsonPointer(path) + "/get").isMissingNode())
                .as("GET %s must be documented", path)
                .isFalse();
    }

    /**
     * «/api/accounts/me» → «/~1api~1accounts~1me»
     *
     * 🧡 ЭМНЕ КИЙИНКИ ГАЙДА?
     *     JSON Pointer стандартында «/» — ачык тал бош белгисин
     *     бүзгөнү үчүн «~1» менен алмашылат. Биз бул тестте
     *     ат талааларын колдонсо, биздин эндпоинт жолундағы «/»
     *     башка которулмак эле — сыноо ЖАЛГАН өтөр эле.
     */
    private String toJsonPointer(String path) {
        return "/~1" + path.substring(1).replace("/", "~1");
    }
}