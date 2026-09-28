package com.minibank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtProperties;
import com.minibank.security.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * ЛОГИН + JWT + КООПСУЗДУК ТЕСТИ (сиз берген 22 талап).
 *
 * 🧡 Тест `@Transactional` + `@BeforeEach deleteAll()` = тест базаны
 *    көрбөйт (колдонуучу таанылбас үчүн) жана бүткөндөн кийин РОЛЛБЭК
 *    иштейт (база таза калат).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthLoginJwtTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EMAIL = "login-test@example.com";
    private static final String PASSWORD = "Password123!";

    /** Ар бир тесттин АЛДЫНДА тазалоо (тест көрбөсүн үчүн). */
    @BeforeEach
    void clearUsersTable() {
        userRepository.deleteAll();
    }

    /**
     * Тестке колдонуучу жасап, паролдүү чыныгы энкодер менен хэштейм.
     *
     * 🧠 Эмне ЧЫНЫгы энкодер, кол менен жазылган хэш эмес?
     *     Хэштин "коолгон" болгондуктан, эгер сыпарыбызда туура эмес
     *     жазып койсок, сыноо ПАРОЛЬДУН ЭМЕС, ХЭШТИН катасын
     *     текшерген болмок. Чыныгы энкодер аркылуу = эч ката жок.
     */
    private User givenRegisteredUser() {
        String hash = passwordEncoder.encode(PASSWORD);
        return userRepository.save(new User("Алиев Али", EMAIL, hash, Role.USER));
    }

    /** "Password123!" -> BCrypt хэши (бирок биз РОЛЬ КОЙБАЙБИЗ, тест керек эмес) */
    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private String loginAndGetToken() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /**
     * JSON'дан таанын маанисин алуу.
     *
     * 🧠 БУЛ ЖЕРДЕ МИНИ ОҢДӨТҮ БОЛДУ — сабак!
     *     Адегенде биз JSONды КОЛ МЕНЕ ОКУП жаздык:
     *         substring(valueStart, end)     ← кавычкалар да кошо кетти
     *     Натыйжа:
     *         expected: "Bearer"  but was: ""Bearer"
     *     Демек токен `Bearer "eyJ..."` болуп, сервер 401 берди.
     *
     * 🏆 ТУРАБЫ: JSONды ОЗҮНЧӢ ОКУП ТҮШҮНБӨЙҢӨЗ! Jackson деген
     *     китепкана МЫКТЫҮН жана аны 200 сат сыноо жазгандан
     *     көп иштейт. Биз кол менен жазуунун ордуна аны колдонобуз.
     *     (Муну кийинки сыноолордо да унутпайбыз.)
     */
    private String extractJsonField(String json, String field) {
        try {
            return objectMapper.readTree(json).get(field).asText();
        } catch (JsonProcessingException e) {
            throw new AssertionError("JSON окулмады: " + json, e);
        }
    }

    // ==================== ЛОГИН (талап 1-10) ====================

    @Test
    @DisplayName("1. Туура email + пароль -> 200 OK")
    void loginWithCorrectCredentialsReturns200() throws Exception {
        givenRegisteredUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("2. Туура email, БАШКА пароль -> 401")
    void loginWithWrongPasswordReturns401() throws Exception {
        givenRegisteredUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "WrongPassword123"}
                                """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("3. БАРЫЛГАСЫН email -> 401")
    void loginWithUnknownEmailReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. БАШКА ПАРОЛЬ менен БАРЫЛГАСЫН email -> БИРДЕЙ ЖООП (user enumeration коргоосу)")
    void wrongPasswordAndUnknownEmailGiveIdenticalResponse() throws Exception {
        givenRegisteredUser();

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "WrongPassword123"}
                                """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // 🧠 Эки жооптун ТЕКСТИ (timestamp/email эмес, колдонуучу жагын) бирдей:
        String wrongMessage = extractJsonField(wrongPassword, "message");
        String unknownMessage = extractJsonField(unknownEmail, "message");

        assertThat(wrongMessage)
                .isEqualTo(unknownMessage)
                .isEqualTo("Invalid email or password");
        // "email жок" деген сөз ЖОК
        assertThat(unknownMessage).doesNotContain("not found").doesNotContain("no such");
    }

    @Test
    @DisplayName("5. Email чоң тамга менен -> иштейт (case-insensitive логин)")
    void loginIsCaseInsensitiveForEmail() throws Exception {
        givenRegisteredUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "  LOGIN-TEST@EXAMPLE.COM  ", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(EMAIL));
    }

    @Test
    @DisplayName("6-7. Жоопто пароль ЖАШАШЫ МҮМКҮН ЭМЕС (пароль да, хэш да)")
    void loginResponseNeverContainsPasswordOrHash() throws Exception {
        givenRegisteredUser();
        String body = loginAndGetToken();

        assertThat(body)
                .doesNotContain(PASSWORD)
                .doesNotContain("password")
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$");
    }

    @Test
    @DisplayName("8-10. tokenType=Bearer, expiresIn=900, токен 3 бөлүктөн турат")
    void loginResponseContainsProperTokenMetadata() throws Exception {
        givenRegisteredUser();
        String body = loginAndGetToken();

        assertThat(extractJsonField(body, "tokenType")).isEqualTo("Bearer");
        assertThat(Long.parseLong(extractJsonField(body, "expiresIn"))).isEqualTo(900L);

        String token = extractJsonField(body, "accessToken");
        // JWT = header.payload.signature -> үч бөлүк, эки чекит
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(token).isNotBlank();
    }

    // ==================== JWT (талап 11-17) ====================

    @Test
    @DisplayName("11. ТУУРА токен менен /api/users/me -> 200")
    void validTokenCanAccessMe() throws Exception {
        givenRegisteredUser();
        String body = loginAndGetToken();
        String token = extractJsonField(body, "accessToken");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("12. ТОКЕН ЖОК -> 401 (JSON, HTML эмес)")
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/users/me"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("13. БУЗУЛГАН токен -> 401")
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("14. ӨТКӨН МӨӨРҮТТҮ токен -> 401")
    void expiredTokenReturns401() throws Exception {
        User user = givenRegisteredUser();

        // 🧡 Мөөрүтү ӨТКӨН токенди КОЛ МЕНЕ КУРУБУЗ. Чыныгы 15 минут
        //    күтүп турууга убакыт жок, ошондуктан биз ушул сыныкчы
        //    файлды мурда колдонууга бөтбөй, ушул жерде курабыз.
        String expiredToken = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", "USER")
                .issuer("minibank")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))  // 1 мүн өткөн
                .signWith(Keys.hmacShaKeyFor(secretBytes()))
                .compact();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("15. ТАМГА ТОПТОЛГОН токен -> 401 (HMAC кол тамга текшерүүсү)")
    void tamperedTokenReturns401() throws Exception {
        givenRegisteredUser();
        String token = extractJsonField(loginAndGetToken(), "accessToken");

        // 🧠 ТАМГА ТОПТОШ = кол тамга бөлүгүн өзгөртүү
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".AAAAdefinitely-not-the-right-signature";

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("16. Токендиги id маалымат базасында ЖОК -> таанылбайт -> 401")
    void tokenWithNonExistentUserIsNotAuthenticated() throws Exception {
        givenRegisteredUser();

        // 🧠 Биздин сыр менен кырланган токен, бирок маалымат базасында
        //    999999 деген колдонуучу ЖОК. Бул чыныгы мисал: база тазаланса
        //    же колдонуучу өчүрүлсө, эски токенге киргиле алмашы керек.
        String ghostToken = Jwts.builder()
                .subject("999999")
                .claim("role", "ADMIN")
                .issuer("minibank")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(secretBytes()))
                .compact();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + ghostToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("17. USER -> ROLE_USER (Spring Security укук түзүлөт)")
    void userRoleBecomesRoleUserAuthority() throws Exception {
        User user = givenRegisteredUser();
        String token = extractJsonField(loginAndGetToken(), "accessToken");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        // Токендеги claim туура көрүнөт
        Long userId = jwtService.parseAndGetUserId(token);
        assertThat(userId).isEqualTo(user.getId());
    }

    // ==================== КООПСУЗДУК (талап 18-22) ====================

    @Test
    @DisplayName("18. /api/auth/register АЧЫК — токенсиз иштейт")
    void registerIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ачык", "email": "public@example.com",
                                 "password": "Password123!"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("19. /api/auth/login АЧЫК — токенсиз иштейт")
    void loginIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "password": "Whatever123"}
                                """))
                .andExpect(status().isUnauthorized());   // 401 = катталууга укук БАР
    }

    @Test
    @DisplayName("20. /api/users/me КОРГООЛУУ — токенсиз 401")
    void meIsProtected() throws Exception {
        // "Bearer" бүтүн эмес, "Basic xxx" деп жөнөтсөк да 401 керек
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Basic YWxpcDp3aWxkYWQ="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("21. ТОКЕНДҮ ПАРОЛЬ ХЭШИ ЖОК (пароль сырт чыкпайт)")
    void jwtContainsNoPasswordHash() throws Exception {
        givenRegisteredUser();
        String body = loginAndGetToken();
        String token = extractJsonField(body, "accessToken");

        // Борбор бөлүктү (payload) окуп, анын ичиндегиге карайбыз
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));

        assertThat(payload)
                .doesNotContain("password")
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$")
                .doesNotContain(EMAIL)      // 🔴 email да күйгөн жок
                .contains("\"sub\"")        // ✅ id бар
                .contains("\"role\"")       // ✅ роль бар
                .contains("\"exp\"");       // ✅ мөөрүт бар
    }

    @Test
    @DisplayName("22. JWT СЫРЫ ЖООПТОРДУН БИРЧАСЫНДА ЖОК")
    void jwtSecretNeverAppearsInResponses() throws Exception {
        givenRegisteredUser();
        String body = loginAndGetToken();

        String secret = new String(secretBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(secret);

        String meBody = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + extractJsonField(body, "accessToken")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(meBody).doesNotContain(secret);
    }

    /** Тест үчүн: бир эле сыр менен токен куруу (чыныгы JwtService сырын колдоном). */
    private byte[] secretBytes() {
        return jwtProperties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
