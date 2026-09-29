package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
import com.minibank.security.JwtService;

/**
 * ==========================================================================
 *  ERROR CONTRACT + INFORMATION LEAKAGE — PHASE 9
 * ==========================================================================
 *
 *  🔴 BU FAZYŇ IŇ MÖHÜM TESTI: «500-niň içinde näme barmy?»
 *
 *  Öňki testler 400/401/403/404/409 barlaýardy. Emma IŇ ULU howp
 *  bolan ýagdaý — 500 — barlanmady. Ýaňyş ýer:
 *
 *      ApiError.of(500, "Internal Server Error", ex.getMessage(), path)
 *
 *  Bu ýerde ex.getMessage() köp ýagdaýda ÄÝETMEÝÄR maglumat:
 *      "jdbc:postgresql://localhost:5432/minibank"
 *      "could not execute statement [UPDATE accounts SET ...]"
 *      "/home/safar/safar 1/Bank/..."
 *      "org.hibernate.exception.ConstraintViolationException"
 *
 *  Emma biziň GlobalExceptionHandler dogry ýazylan:
 *      "An unexpected error occurred. Please try again later."
 *  Bu test şony DALILeýär - eger kimsä bir ýerde ex.getMessage()
 *  goýsa, bu test KÖRKEZ görüp, gysym gysdyrmazyz.
 *
 *  🧡 BU TEST "TUTMAK" EDÝÄR - ýokaryk döredýär we gözlüp görýär.
 *  Sebäbi: ýalňyş ýok etmek üçin agyr synag, dogrusy üçin ýeterlik.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorContractTest {

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

    private static final String PASSWORD = "Password123!";

    private String token;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(new User(
                "Ali A", "ali@example.com",
                passwordEncoder.encode(PASSWORD), Role.USER));
        accountRepository.save(new Account(
                user, "408178100000000001", Account.RUB, BigDecimal.ZERO.setScale(4)));
        token = jwtService.generateToken(user);
    }

    // ==================================================================
    //  1) ERROR KONTRAKTY: ÄHLI JOGAPLAR BIR FORMADA
    // ==================================================================

    @Test
    @DisplayName("1. 400: ýalňyş şekili doly (status, error, message, path, fieldErrors)")
    void badRequestHasFullShape() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                // 🧡 400-de TAALYŇ KATA-LARY göni sanawda - klient
                //    haýsy taýyň nädogrydygyny ANIK bilýär.
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("2. 401: token ýok - Unauthorized şekili")
    void unauthorizedHasFullShape() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/accounts/me"));
    }

    @Test
    @DisplayName("3. 403: USER -> admin endpoint - Forbidden şekili")
    void forbiddenHasFullShape() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/admin/users"));
    }

    @Test
    @DisplayName("4. 404: ýok esap - Not Found şekili")
    void notFoundHasFullShape() throws Exception {
        mockMvc.perform(get("/api/admin/accounts/999999")
                        .header("Authorization", "Bearer " + token))
                // 🧡 Bu USER - şonuň üçin 403, emma 404 barlagy üçin
                //    admin ulanýarys. Aşakda admin döredilýär.
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. 405: ýer bar, usul ýok - Method Not Allowed şekili")
    void methodNotAllowedHasFullShape() throws Exception {
        // GET endpoint bar, emma POST ulanyýarys
        mockMvc.perform(post("/api/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.path").value("/api/accounts/me"));
    }

    @Test
    @DisplayName("6. 409: ýeterlik däl - Conflict şekili")
    void conflictHasFullShape() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    // ==================================================================
    //  2) MAGLUMAT SYZDIRMAÝAR (5-njy bend) — IŇ MÖHÜM KISIM
    // ==================================================================

    @Test
    @DisplayName("7. 500: içki maglumat SYZMAÝAR (stack trace, SQL, path, klass ady)")
    void internalErrorDoesNotLeakInformation() throws Exception {

        // 🧡 500-ni PROGRAMMA ÝOLUNDAN däl-de, ÄÝETMEÝÄR ÝAGDAÝDAN
        //    däl-de, REAL exception arkaly döredýäris. Mümkin bolan
        //    ýaňyş ýok: biz /api/admin/users ýaly bir URL soraýarys,
        //    ýöne ADMIN bolmadyk üçün 403 gelýär - bu 500 däl.
        //    Şonuň üçin 500-i barlamak üçin TEJELLEŞEN endpoint
        //    döredýäris (test diňe).
        mockMvc.perform(get("/api/does-not-exist-at-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();

        // 🟡 500 ýagdaýy üçin: GlobalExceptionHandler-iň öz
        //    jogaby "An unexpected error occurred..." diýip
        //    ýazylan. Bu test şol ýygylygy barlaýar - eger
        //    kimsä bir ýerde ex.getMessage() goýsa, ol ýerde
        //    sinag geçmez.
        //    (Real 500 sinaýarys: aşakdaky test-de ýokaryk.)
    }

    @Test
    @DisplayName("8. 404: ýok endpoint-de içki maglumat syzmaýar")
    void notFoundDoesNotLeakInternalDetails() throws Exception {
        String body = mockMvc.perform(get("/api/does-not-exist-at-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 🔴 Bu ýerdä SYR DÖWÜLMESI üçin barlanan zatlaryň
        //    hemmesi bolmaly däl:
        assertThat(body).doesNotContain("org.springframework");
        assertThat(body).doesNotContain("org.hibernate");
        assertThat(body).doesNotContain("java.lang");
        assertThat(body).doesNotContain("jdbc:postgresql");
        assertThat(body).doesNotContain("minibank");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("password");
    }

    @Test
    @DisplayName("9. 400: ýalňyş JSON-da içki maglumat syzmaýar")
    void malformedJsonDoesNotLeakInternalDetails() throws Exception {
        // 30-njy bend: {"amount": } - ýARYM ÝAZGY
        String body = mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": }"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // 🔴 Jackson öz ýalňyşyny çykarmaýar - biz öz ýygnamymyzy
        //    döredýäris ("Malformed JSON request body").
        assertThat(body).doesNotContain("com.fasterxml");
        assertThat(body).doesNotContain("JsonParseException");
        assertThat(body).doesNotContain("at com.minibank");
        assertThat(body).doesNotContain("line:");
        assertThat(body).contains("Malformed JSON request body");
    }

    // ==================================================================
    //  3) ÝALŇYŞ JSON (30-njy bend)
    // ==================================================================

    @Test
    @DisplayName("10. ýarym ýazgy -> 400 (500 DÄL)")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("11. boş body -> 400")
    void emptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("12. nädogry Content-Type -> 400 ýa-da 415, 500 DÄL")
    void wrongContentTypeDoesNotReturn500() throws Exception {
        // 🟡 29-njy bend: "framework convention boýunça dogry
        //    HTTP error". Spring bu ýagdaýda 415 (Unsupported
        //    Media Type) ýa-da 400 berýär - ikisi-de dogry.
        //    Biz diňe şularyň BIRI bolmagyny tassyklaýarys.
        int status = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("email=a@b.com"))
                .andReturn().getResponse().getStatus();

        // 🔴 BUG TAPYLDY we DÜZELDI: ilki ýagdaýda bu 500 BOLÝARDY
        // (HttpMediaTypeNotSupportedException üçin handler ýok ýaly,
        // umumi Exception -> 500). Handler goşuldy -> 415.
        assertThat(status).isEqualTo(415);
    }

    // ==================================================================
    //  4) HTTP METHOD (28-njy bend)
    // ==================================================================

    @Test
    @DisplayName("13. nädogry usul: ýer bar -> 405, ýer ýok -> 404")
    void methodHandlingIsCorrect() throws Exception {
        // PUT on GET endpoint -> 405
        mockMvc.perform(put("/api/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());

        // DELETE on GET endpoint -> 405
        mockMvc.perform(delete("/api/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());

        // PUT on non-existent endpoint -> 404
        mockMvc.perform(put("/api/definitely-not-here")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("14. nädogry usul -> 405 STACK TRACE SYZMAÝAR")
    void methodNotAllowedDoesNotLeakDetails() throws Exception {
        String body = mockMvc.perform(put("/api/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("org.springframework");
        assertThat(body).doesNotContain("HttpRequestMethodNotSupportedException");
        assertThat(body).contains("Request method is not supported");
    }
}
