package com.minibank.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 🔄 TRANSFER API ТЕСТИ (тапшырманын 24-бөлүмү, 17-25-пункт).
 *
 * 🧡 MockMvc — Spring'дин «чыныгы HTTP стекин» тест ичинде күйгүзөт:
 *     JSON сериализация, @Valid валидация, Security чыпкалары,
 *     GlobalExceptionHandler — БААСЫ чыныгыдай иштейт.
 *     Жалгыз ТИЗИМ (порт) күрсөтүлбөйт.
 *
 * 🧡 БУЛ ЖЕРДЕ ТАПШЫРМАНЫН 22-БӨЛҮМҮ ТЕКШЕРИЛЕТ:
 *     400 / 401 / 404 / 409 — жана ApiError ФОРМАТЫ (stack trace чыкпайды).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferApiTest {

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

    private User givenUserWithAccount(String email) throws Exception {
        User user = givenUser(email);
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isCreated());
        return user;
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private String accountNumberOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getAccountNumber();
    }

    private BigDecimal balanceOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    /** Эсепке акча салуу (аркылуу API — чыныгы кодду сыноо). */
    private void deposit(User user, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + ", \"description\": \"seed\"}"))
                .andExpect(status().isOk());
    }

    private String body(String toAccountNumber, String amount, String description) {
        return """
                {"toAccountNumber": "%s", "amount": %s%s}
                """.formatted(toAccountNumber, amount,
                description == null ? "" : ", \"description\": \"" + description + "\"");
    }

    // ==================== 17-22: 200 OK ====================

    @Test
    @DisplayName("17. 🔄 Чыныгы JWT -> 200 OK (эки эсеп жаңы абалы)")
    void transferReturns200() throws Exception {
        User a = givenUserWithAccount("api-ok-a@example.com");
        User b = givenUserWithAccount("api-ok-b@example.com");
        deposit(a, "1000.0000");
        deposit(b, "500.0000");

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "300.0000", "Transfer to another account")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAccountNumber").value(accountNumberOf(a)))
                .andExpect(jsonPath("$.toAccountNumber").value(accountNumberOf(b)))
                .andExpect(jsonPath("$.amount").value(300.0000))
                .andExpect(jsonPath("$.fromBalance").value(700.0000))
                .andExpect(jsonPath("$.toBalance").value(800.0000))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.transactionId").isNumber());

        assertThat(balanceOf(a)).isEqualByComparingTo("700.0000");
        assertThat(balanceOf(b)).isEqualByComparingTo("800.0000");
    }

    @Test
    @DisplayName("18. 🔴 Transfer токенсиз -> 401")
    void transferWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("408178100000000001", "100", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("19. 🔴 БУЗУЛГАН токен -> 401")
    void transferWithBrokenTokenIs401() throws Exception {
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer broken.token.value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("408178100000000001", "100", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("20. 🔄 User A -> User B: экөө эсеп тең өзгөрөт")
    void userATransfersToUserB() throws Exception {
        User a = givenUserWithAccount("api-ab-a@example.com");
        User b = givenUserWithAccount("api-ab-b@example.com");
        deposit(a, "1000.0000");
        deposit(b, "500.0000");

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "300.0000", null)))
                .andExpect(status().isOk());

        // 🧡 КАЙНАК (A) — азайды, МАКАТ (B) — көбөйт.
        assertThat(balanceOf(a)).isEqualByComparingTo("700.0000");
        assertThat(balanceOf(b)).isEqualByComparingTo("800.0000");
    }

    @Test
    @DisplayName("21. 🛡️ A башка SOURCE эсепти ТИЙМЕЙТ (IDOR)")
    void userACannotChooseAnotherSourceAccount() throws Exception {
        User a = givenUserWithAccount("api-idor-a@example.com");
        User b = givenUserWithAccount("api-idor-b@example.com");
        User c = givenUserWithAccount("api-idor-c@example.com");
        deposit(a, "1000.0000");
        deposit(b, "1000.0000");

        Long accountIdB = accountRepository.findByUserId(b.getId()).orElseThrow().getId();

        // 🔴 Чыгаруучу БАШКАЛАРДЫН АКЧАСЫН АЛГЫСЫ КЕЛЕТ: «мен Bнин
        //    эсебинен чыгарып, Cнин эсебине сал» деп аракет кылат.
        //    Ал үчүн сүрөттө чүйлүш id'лерди жазат. БИЗ аларды
        //    КАБЫЛ АЛБАЙБЫЗ — source ТОКЕНДЕН (Aнын эсеби).
        //    Демек натыйжа: акча A -> C кетет, B КАСЫ ТИЙБЕЙТ.
        String malicious = """
                {"toAccountNumber": "%s", "amount": 100.0000, "accountId": %d, "userId": %d, "fromAccountId": %d}
                """.formatted(accountNumberOf(c), accountIdB, b.getId(), accountIdB);

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malicious))
                .andExpect(status().isOk());

        // 🔴 Акча Aнын ӨЗ эсебинен кетти (1000-100=900), Cге жетти.
        assertThat(balanceOf(a)).isEqualByComparingTo("900.0000");
        assertThat(balanceOf(c)).isEqualByComparingTo("100.0000");

        // 🔴 B ТАЗА ҮЧҮН — чүйлүш id'си аны КАЙЧЫЛАП ЖАЙЫН
        //    БАЙБЫТПАГАНЫН көрсөтүү.
        assertThat(balanceOf(b)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("22. 🔄 Destination эсеп номуру БОЮНЧА табылат (ички id ЭМЕС)")
    void destinationIsFoundByAccountNumber() throws Exception {
        User a = givenUserWithAccount("api-num-a@example.com");
        User b = givenUserWithAccount("api-num-b@example.com");
        deposit(a, "1000.0000");

        // 🧡 Биз эсеп ИДСИ МЕНЕН ЭМЕС, НОМУРУ МЕНЕН иштейбиз —
        //    бул колдонуучу үчүн жарымда колдонулуучу нерсе.
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "250.0000", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toAccountNumber").value(accountNumberOf(b)));

        assertThat(balanceOf(b)).isEqualByComparingTo("250.0000");
    }

    // ==================== 23-25: 404 / 409 ====================

    @Test
    @DisplayName("23. 🔴 Destination ЖОК -> 404, экөө баланс ӨЗГӨРБӨЙТ")
    void missingDestinationReturns404() throws Exception {
        User a = givenUserWithAccount("api-404-a@example.com");
        deposit(a, "1000.0000");

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("408178100000099999", "100.0000", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 🔴 БАЛАНС ӨЗГӨРБӨДҮ.
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("24. 🔴 Акча ЖЕТПЕЙТ -> 409 InsufficientFunds")
    void insufficientFundsReturns409() throws Exception {
        User a = givenUserWithAccount("api-409-a@example.com");
        User b = givenUserWithAccount("api-409-b@example.com");
        deposit(a, "1000.0000");
        deposit(b, "500.0000");

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "1000.0000", null)))
                .andExpect(status().isOk());   // 1000 = 1000 -> уруксат

        // 🧡 Эми 0.0001 калды -> 409
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "0.0001", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Insufficient funds")));

        assertThat(balanceOf(a)).isEqualByComparingTo("0.0000");
        assertThat(balanceOf(b)).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("25. 🔴 SELF-TRANSFER (А → А) -> 409, баланс ӨЗГӨРБӨЙТ")
    void selfTransferReturns409() throws Exception {
        User a = givenUserWithAccount("api-self-a@example.com");
        deposit(a, "1000.0000");

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(a), "300.0000", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("same account")));

        // 🔴 БАЛАНС ӨЗГӨРБӨДҮ.
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");
    }

    // ==================== ВАЛИДАЦИЯ (400) ====================

    @Test
    @DisplayName("26. 🔴 Сумма = 0 / < 0 / 4төн көп каттуу -> 400")
    void invalidAmountsReturn400() throws Exception {
        User a = givenUserWithAccount("api-val-a@example.com");
        User b = givenUserWithAccount("api-val-b@example.com");
        deposit(a, "1000.0000");
        String token = tokenFor(a);

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "0", null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "-100", null)))
                .andExpect(status().isBadRequest());

        // 🧡 5 ондук бөлүк — 400. ТЕГЕРЕКТЕБЕЙБИЗ.
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "100.12345", null)))
                .andExpect(status().isBadRequest());

        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("27. 🔴 toAccountNumber жок / бузулган -> 400")
    void invalidDestinationReturns400() throws Exception {
        User a = givenUserWithAccount("api-dst-a@example.com");
        deposit(a, "1000.0000");
        String token = tokenFor(a);

        // (a) таалаа жок
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.0000}"))
                .andExpect(status().isBadRequest());

        // (b) номур 18 цифра ЭМЕС
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123", "100.0000", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("28. 🔴 БУЗУЛГАН JSON -> 400 (500 ЭМЕС)")
    void malformedJsonReturns400() throws Exception {
        User a = givenUserWithAccount("api-json-a@example.com");

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest());
    }

    // ==================== КООПСУЗДУК / ТАЗА ЖООП ====================

    @Test
    @DisplayName("29. 🛡️ Жоопто пароль/колдонуучу/ички id ЖОК (Entity ≠ DTO)")
    void responseHasNoSensitiveData() throws Exception {
        User a = givenUserWithAccount("api-safe-a@example.com");
        User b = givenUserWithAccount("api-safe-b@example.com");
        deposit(a, "1000.0000");

        String response = mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "100.0000", "ok")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response)
                .doesNotContain("password")
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$")
                .doesNotContain("email")
                .doesNotContain("fullName")
                .contains("fromBalance")
                .contains("toBalance");
    }

    @Test
    @DisplayName("30. 🛡️ Ката жоопторунда stack trace ЖОК")
    void errorResponsesDoNotLeakStackTrace() throws Exception {
        User a = givenUserWithAccount("api-stack-a@example.com");
        deposit(a, "10.0000");

        String errorBody = mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("408178100000099999", "100.0000", null)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(errorBody)
                .doesNotContain("at com.minibank")
                .doesNotContain("Exception")
                .doesNotContain("jdbc:")
                .doesNotContain("org.springframework")
                .contains("\"path\"");
    }

    @Test
    @DisplayName("31. 🛡️ ?userId= / ?accountId= параметрлери ТАПШЫРМАГАН")
    void queryParametersCannotOverrideSource() throws Exception {
        User a = givenUserWithAccount("api-q-a@example.com");
        User b = givenUserWithAccount("api-q-b@example.com");
        deposit(a, "1000.0000");
        deposit(b, "1000.0000");

        Long accountIdB = accountRepository.findByUserId(b.getId()).orElseThrow().getId();

        // 🔴 Сүрөткө чүйлүш параметрлери: эндпоинт аларды ОКУМАЙТ.
        mockMvc.perform(post("/api/accounts/me/transfer?userId=" + b.getId()
                        + "&accountId=" + accountIdB + "&fromAccountId=" + accountIdB)
                        .header("Authorization", "Bearer " + tokenFor(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(accountNumberOf(b), "100.0000", null)))
                .andExpect(status().isOk());

        // 🔴 Акча АНЫН ӨЗ эсебинен кетти. B — ТАЗА ҮЧҮН.
        assertThat(balanceOf(a)).isEqualByComparingTo("900.0000");
        assertThat(balanceOf(b)).isEqualByComparingTo("1100.0000");
    }
}
