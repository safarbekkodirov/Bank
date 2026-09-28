package com.minibank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
 * АКЧА ОПЕРАЦИЯЛАРЫНЫН HTTP ТЕСТИ — чыныгы REST API аркылуу.
 *
 * 🧡 БИЗ БУЛ ЖЕРДЕ ТАПШЫРМАНЫН 20-БӨЛҮМҮН ТЕКШЕРЕБИЗ:
 *     400 — сумма жок / 0 / терс / 4төн көп каттуу / бузулган JSON
 *     401 — токен жок, токен бузулган
 *     404 — эсеп жок
 *     409 — акча жетишпейт
 *   жана ApiError ФОРМАТЫ (stack trace чыкпайды).
 *
 * 🧠 Эмне MockMvc, эмес чыныгы сервер? MockMvc — Spring'дин
 *     «чыныгы HTTP стекин» тест ичинде күйгүзөт: JSON сериализация,
 *     @Valid валидация, Security чыпкалары, GlobalExceptionHandler —
 *     БААСЫ чыныгыдай иштейт. Жалгыз ТИЗИМ (порт) көрсөтүлбөйт.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MoneyOperationApiTest {

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

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User givenUser(String email) {
        return userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode(PASSWORD), Role.USER));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private String body(String amount, String description) {
        return """
                {"amount": %s%s}
                """.formatted(amount,
                description == null ? "" : ", \"description\": \"" + description + "\"");
    }

    /** Эсеп ачып, аны токен менен жасалган колдонуучунун эсеби экенин ыкташат. */
    private User givenUserWithAccount(String email) throws Exception {
        User user = givenUser(email);
        String token = tokenFor(user);
        mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        return user;
    }

    private BigDecimal balanceOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    // ==================== 200 OK ====================

    @Test
    @DisplayName("1. POST /api/accounts/me/deposit -> 200 OK")
    void depositReturns200() throws Exception {
        User user = givenUserWithAccount("deposit@example.com");

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("500.0000", "Cash deposit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").isString())
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.amount").value(500.0000))
                .andExpect(jsonPath("$.balance").value(500.0000))
                .andExpect(jsonPath("$.transactionId").isNumber());

        assertThat(balanceOf(user)).isEqualByComparingTo("500.0000");
    }

    @Test
    @DisplayName("2. POST /api/accounts/me/withdraw -> 200 OK")
    void withdrawReturns200() throws Exception {
        User user = givenUserWithAccount("withdraw@example.com");
        String token = tokenFor(user);
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1000.0000", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("300.0000", "Cash withdrawal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(300.0000))
                .andExpect(jsonPath("$.balance").value(700.0000));

        assertThat(balanceOf(user)).isEqualByComparingTo("700.0000");
    }

    @Test
    @DisplayName("3. description КЫЙИККИ (сенсиз да иштейт)")
    void depositWithoutDescriptionWorks() throws Exception {
        User user = givenUserWithAccount("optional@example.com");

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.0000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10.0000));
    }

    @Test
    @DisplayName("4. 🧡 4 каттуу жазуу JSON'да САКТАЛЫП, 4 каттуу ОКУЛАД")
    void fourDecimalScaleIsPreservedInJson() throws Exception {
        User user = givenUserWithAccount("scale-json@example.com");

        String response = mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100.5000", null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 🧡 Банк экранда «100» ЭМЕС, «100.5000» көрсөтөт. Биз
        //    масштабты жоголтпойбуз — ошондуктан JSON'да да 4 белги.
        assertThat(response).contains("100.5000").contains("\"balance\"");
    }

    // ==================== 401 UNAUTHORIZED ====================

    @Test
    @DisplayName("5. 🔴 Deposit токенсиз -> 401")
    void depositWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("6. 🔴 Withdraw токенсиз -> 401")
    void withdrawWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("7. 🔴 БУЗУЛГАН токен -> 401 (экөө эндпоинт те)")
    void invalidTokenIs401() throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer tampered.token.here")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", null)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer tampered.token.here")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. 🔴 МӨӨРҮТҮ ӨТКӨН токен -> 401")
    void expiredTokenIs401() throws Exception {
        User user = givenUser("expired@example.com");

        // 🧡 Чыныгы 15 мүнүт күтүүгө убакыт жок — ошондуктан биз
        //    JJWT менен ӨТКӨН убакытту токенди КОЛ МЕНЕ курабыз
        //    (мурунку PHASE 3B ошол эле ыйламал кылган).
        //    🧡 Сырды биз ЖАШЫРБАЙБИЗ: колдонуучунун токенин
        //    ушул сыр менен гана чыныгы колдонмо чече алат. Биз
        //    сырды унутсак, «башка ким чыгара алабы?» деп күмөндөм.
        String expired = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(secretBytes()))
                .compact();

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", null)))
                .andExpect(status().isUnauthorized());
    }

    private byte[] secretBytes() {
        return jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== 400 BAD REQUEST ====================

    @Test
    @DisplayName("9. 🔴 Сумма ЖОК -> 400")
    void missingAmountIs400() throws Exception {
        User user = givenUserWithAccount("missing@example.com");

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"no amount here\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
    }

    @Test
    @DisplayName("10. 🔴 Сумма = 0 -> 400 (эки эндпоинт те)")
    void zeroAmountIs400() throws Exception {
        User user = givenUserWithAccount("zero@example.com");
        String token = tokenFor(user);

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("0", null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("0.0000", null)))
                .andExpect(status().isBadRequest());

        assertThat(balanceOf(user)).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("11. 🔴 Сумма ТЕРС -> 400 (эки эндпоинт те)")
    void negativeAmountIs400() throws Exception {
        User user = givenUserWithAccount("negative@example.com");
        String token = tokenFor(user);

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("-100", null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("-100", null)))
                .andExpect(status().isBadRequest());

        assertThat(balanceOf(user)).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("12. 🔴 5 ондук бөлүк (100.12345) -> 400. ТЕГЕРЕКТЕБЕЙБИЗ")
    void tooManyDecimalsIs400() throws Exception {
        User user = givenUserWithAccount("decimals@example.com");
        String token = tokenFor(user);

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100.12345", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value(containsString("4 decimal places")));

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100.12345", null)))
                .andExpect(status().isBadRequest());

        // 🔴 БАЛАНС 100.1235 КЫЛЫП ТЕГЕРЕКТЕГЕН ЭМЕС
        assertThat(balanceOf(user)).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("13. 🔴 БУЗУЛГАН JSON -> 400 (500 ЭМЕС)")
    void malformedJsonIs400() throws Exception {
        User user = givenUserWithAccount("malformed@example.com");

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100, "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("14. 🔴 Сумма ТЕКСТ (\"abc\") -> 400 (Jackson катасы)")
    void nonNumericAmountIs400() throws Exception {
        User user = givenUserWithAccount("text@example.com");

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": \"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== 404 / 409 ====================

    @Test
    @DisplayName("15. 404 Эсеп ЖОК -> 404 (эки эндпоинт те)")
    void noAccountIs404() throws Exception {
        User user = givenUser("no-account@example.com");
        String token = tokenFor(user);

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/accounts/me/deposit"));

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("16. 🔴 409 Калгандан КӨП -> 409 InsufficientFunds")
    void insufficientFundsIs409() throws Exception {
        User user = givenUserWithAccount("insufficient@example.com");
        String token = tokenFor(user);
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("650.0000", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1000.0000", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value(containsString("Insufficient funds")))
                .andExpect(jsonPath("$.path").value("/api/accounts/me/withdraw"));

        // 🔴 БАЛАНС ОЗГӨРБӨДҮ: күткөн 650.0000, чыныгы 650.0000
        assertThat(balanceOf(user)).isEqualByComparingTo("650.0000");
    }

    @Test
    @DisplayName("17. 🧡 БАЛАНСИНЧА чыгаруу -> 200, баланс 0.0000")
    void withdrawingExactBalanceOverApiLeavesZero() throws Exception {
        User user = givenUserWithAccount("exact@example.com");
        String token = tokenFor(user);
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1000.0000", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1000.0000", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0000));

        // 🧡 Кийин 1 тыйын да чыгара албайт — эсеп БОШ, 409
        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("0.0001", null)))
                .andExpect(status().isConflict());
    }

    // ==================== КООПСУЗДУК (жаоопто) ====================

    @Test
    @DisplayName("18. 🛡️ Жоопто пароль/колдонуучу ЖОК (Entity ≠ DTO)")
    void responseHasNoSensitiveData() throws Exception {
        User user = givenUserWithAccount("safe@example.com");
        String token = tokenFor(user);

        String depositBody = mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1000.0000", "seed")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String withdrawBody = mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100.0000", null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String json : new String[]{depositBody, withdrawBody}) {
            assertThat(json)
                    .doesNotContain("password")
                    .doesNotContain("passwordHash")
                    .doesNotContain("$2a$")
                    .doesNotContain("fullName")
                    .doesNotContain("\"user\"")
                    .doesNotContain("email");
        }
        assertThat(withdrawBody).contains("accountNumber").contains("balance");
    }

    @Test
    @DisplayName("19. 🛡️ Ката жоопторунда stack trace ЖОК")
    void errorResponsesDoNotLeakStackTrace() throws Exception {
        User user = givenUser("clean-error@example.com");

        String errorBody = mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100", null)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 🧡 Миролуу каталар: "at com.minibank...", "Exception",
        //    "jdbc:postgresql" — булардын БААРЫ ИЧКИ МААЛЫМАТ.
        assertThat(errorBody)
                .doesNotContain("at com.minibank")
                .doesNotContain("Exception")
                .doesNotContain("jdbc:")
                .doesNotContain("org.springframework")
                .contains("\"path\"");
    }
}