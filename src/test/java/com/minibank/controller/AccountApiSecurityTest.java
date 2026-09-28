package com.minibank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.minibank.repository.AccountRepository;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtService;

/**
 * ЭСЕП API + КООПСУЗДУК ТЕСТИ (чыныгы HTTP аркылуу).
 *
 * 🧡 Бул сыноо эки колдонуучуну САЛЫШТЫРАТ — «колдонуучу A, колдонуучу B»
 *   деген эң маанилүү коопсуздук суроосуна жооп издөөт.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PASSWORD = "Password123!";

    @BeforeEach
    void cleanTables() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User givenUser(String email) {
        return userRepository.save(new User(
                "Колдонуучу " + email, email, passwordEncoder.encode(PASSWORD), Role.USER));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private String createAccountAndGetNumber(String token) throws Exception {
        String body = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        try {
            return objectMapper.readTree(body).get("accountNumber").asText();
        } catch (JsonProcessingException e) {
            throw new AssertionError("JSON окулмады: " + body, e);
        }
    }

    @Test
    @DisplayName("1. POST /api/accounts (туура JWT) -> 201 Created")
    void createAccountReturns201() throws Exception {
        User user = givenUser("create@example.com");

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.accountNumber").isString())
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.balance").value(0.0000))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("2. GET /api/accounts/me (туура JWT) -> 200")
    void getMyAccountReturns200() throws Exception {
        User user = givenUser("me@example.com");
        String number = createAccountAndGetNumber(tokenFor(user));

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(number))
                .andExpect(jsonPath("$.currency").value("RUB"));
    }

    @Test
    @DisplayName("3. ТОКЕН ЖОК -> 401 (эки эндпоинт те)")
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. БУЗУЛГАН токен -> 401")
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer tampered.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. Эсеп ЖОК -> 404 Not Found")
    void noAccountReturns404() throws Exception {
        User user = givenUser("no-acc@example.com");

        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/accounts/me"));
    }

    @Test
    @DisplayName("6. Экинчи эсеп ачуу -> 409 Conflict")
    void secondAccountReturns409() throws Exception {
        User user = givenUser("twice@example.com");
        String token = tokenFor(user);
        createAccountAndGetNumber(token);

        mockMvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        containsString("already has a bank account")));
    }

    @Test
    @DisplayName("7. Жоопто пароль/колдонуучу ЖОК")
    void responseHasNoSensitiveData() throws Exception {
        User user = givenUser("safe@example.com");
        String token = tokenFor(user);

        String createBody = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String meBody = mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String body : new String[]{createBody, meBody}) {
            assertThat(body)
                    .doesNotContain("password")
                    .doesNotContain("passwordHash")
                    .doesNotContain("$2a$")
                    .doesNotContain("\"user\"")
                    .doesNotContain("fullName");
        }
        assertThat(meBody).contains("accountNumber").contains("currency").contains("balance");
    }

    @Test
    @DisplayName("8. 🛡️ КОЛДОНУЧУ A, колдонуучу B'нин эсебин КӨРӨ АЛБАЙТ")
    void userACannotSeeUserBAccount() throws Exception {
        User userA = givenUser("alice@example.com");
        User userB = givenUser("bob@example.com");
        String tokenA = tokenFor(userA);
        String tokenB = tokenFor(userB);

        String numberA = createAccountAndGetNumber(tokenA);
        String numberB = createAccountAndGetNumber(tokenB);
        assertThat(numberA).isNotEqualTo(numberB);

        // A өз эсебин алат
        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(numberA));

        // B ӨЗ эсебин алат — A эмес!
        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(numberB))
                .andExpect(jsonPath("$.accountNumber").value(not(numberA)));
    }

    @Test
    @DisplayName("9. 🛡️ URL'ден чыныгы эсеп id'син алууга БОЛОБОЙТ (IDOR)")
    void noEndpointLeaksAccountById() throws Exception {
        User userA = givenUser("idor-a@example.com");
        User userB = givenUser("idor-b@example.com");
        String tokenA = tokenFor(userA);
        String tokenB = tokenFor(userB);
        createAccountAndGetNumber(tokenA);
        createAccountAndGetNumber(tokenB);

        Long accountIdA = accountRepository.findByUserId(userA.getId())
                .orElseThrow().getId();

        // 🔴 Бизде /api/accounts/{id} ЭНДПОИНТИ ЖОК.
        // 🧠 Токен ТУУРА болгондуктан коопсуздук өтөт, кийин Spring
        //    «мындай барак жок» деп ыйлайт -> 404 (500 ЭМЕС).
        //    Эң маанилүүсү: чыгаруучу B, A'нын эсебинин маалыматын
        //    АЛАЛБАЙТ — ал тек 404 алат, эсептин маалыматын ЭМЕС.
        mockMvc.perform(get("/api/accounts/" + accountIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 🧠 ?userId= параметри ИШТЕМЕЙТ: чыныгы эсеп ТОКЕНДИКИ
        //    колдонуучуга карата, сүрөттөн келген параметрге эмес.
        String numberB = accountRepository.findByUserId(userB.getId())
                .orElseThrow().getAccountNumber();

        mockMvc.perform(get("/api/accounts/me?userId=" + userA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(numberB))
                .andExpect(jsonPath("$.accountNumber").value(not(
                        accountRepository.findByUserId(userA.getId())
                                .orElseThrow().getAccountNumber())));
    }

    @Test
    @DisplayName("10. Токендиги колдонуучу өчүрүлсө -> 401 (базада жок)")
    void tokenWithDeletedUserIsRejected() throws Exception {
        User user = givenUser("ghost@example.com");
        String token = tokenFor(user);
        createAccountAndGetNumber(token);

        // 🧠 АЛДЫН ЭСЕПТИ ӨЧҮРӨМҮЗ, АНТО ӨЗҮНЧӮ ЧЫГАСА!
        //    Себеби: fk_accounts_user -> ON DELETE RESTRICT, демек
        //    эсеби бар колдонуучуну өчүрүүгө БОЛБОЙТ (маалымат базасы
        //    каргайт). Биз «таза өчүрүлгөн колдонуучу» абалын
        //    жаратабыз: эсеп -> колдонуучу тартибинде өчүрөбүз.
        accountRepository.deleteAll();
        accountRepository.flush();
        userRepository.delete(user);
        userRepository.flush();

        // Фильтр маалымат базасынан колдонуучуну табабыз -> таанылбайт
        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
