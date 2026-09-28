package com.minibank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.repository.UserRepository;

/**
 * КАТТАЛУУ ТЕСТИ (HTTP катмары).
 *
 * 🧠 Бул колдонмонуну чыныгы СЕРВЕРге (@SpringBootTest) көтөрөт жана
 * чыныгы HTTP сүрөт жөнөтөт (MockMvc аркылуу, порт ачпайт):
 *     JSON -> @Valid -> Controller -> Service -> Repository -> PostgreSQL
 *            <- JSON жооп <-        <- UserResponse <-      User
 *
 * 🧠 ЭМНЕ МЫНА УШУЛ Тест?
 *   "@Email" анотациясы Service'те ЭМЕС, Controller'дин КИРИШИНДЕ
 *   иштейт. Ошондуктан репозиторийди "көзөмөлкө" (mock) кылган тест
 *   бул жагымды ТЕКШЕРЕЛ ЭМЕС. Бизге чыныгы колдонмо керек.
 *
 * 🧡 @Transactional = тест бүткөндөн кийин РОЛЛБЭК -> база таза.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerRegistrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    /**
     * 🧠 Тест БАЗА ТАЗА деп өз ишин катуулбаш керек.
     *   Бул @Transactional тест — тазалоо бүткөндөн кийин РОЛЛБЭК болуп,
     *   сен колдонуучуң базада КАЛЫП КЕЛЕТ. Демек чыныгы маалымат ушул
     *   сыноодон убакыт өчүрүлбөйт.
     */
    @BeforeEach
    void clearUsersTable() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. POST /api/auth/register -> 201 Created")
    void registerReturns201Created() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Алиев Али",
                                  "email": "controller@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fullName").value("Алиев Али"))
                .andExpect(jsonPath("$.email").value("controller@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("2. Email кичи тамгага көчөрүлөт (HTTP аркылуу)")
    void lowercasesEmailOverHttp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Алиев Али",
                                  "email": "  HTTP.Case@Example.COM ",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("http.case@example.com"));
    }

    @Test
    @DisplayName("3. Ответте пароль жана пароль хэши ЖОК")
    void responseNeverContainsPassword() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Коопсуздук",
                                  "email": "no-leak@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("Password123!")
                .doesNotContain("$2a$")
                .doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("4. Кеткен email -> 409 CONFLICT")
    void duplicateEmailReturns409Conflict() throws Exception {
        String body = """
                {
                  "fullName": "Биринчи",
                  "email": "conflict@example.com",
                  "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("conflict@example.com")))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
    @Test
    @DisplayName("5. Бош fullName -> 400 BAD REQUEST")
    void blankFullNameReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "   ",
                                  "email": "blank-name@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fullName"));
    }

    @Test
    @DisplayName("6. Туура эмес email -> 400 BAD REQUEST")
    void invalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Тест",
                                  "email": "this-is-not-an-email",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("7. Кыска пароль -> 400 BAD REQUEST")
    void shortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Тест",
                                  "email": "short-pass@example.com",
                                  "password": "Ab1!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value(containsString("between 8 and 72")));
    }

    @Test
    @DisplayName("8. Бир нече ката бир убакта -> бардыгы fieldErrors ичине")
    void reportsAllValidationErrorsAtOnce() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "bad-email",
                                  "password": "x"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)));
    }

    @Test
    @DisplayName("9. Ката жоопторунда STACK TRACE ЖОК")
    void errorResponseContainsNoStackTrace() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "bad",
                                  "password": "x"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("java.lang")
                .doesNotContain("Exception")
                .doesNotContain("at com.minibank")
                .doesNotContain("org.springframework");
    }

    @Test
    @DisplayName("10. БУЗУЛГАН JSON -> 400 (маалымат талганабы)")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ бҮГҮН ҮЧҮН: JSON эмес"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
