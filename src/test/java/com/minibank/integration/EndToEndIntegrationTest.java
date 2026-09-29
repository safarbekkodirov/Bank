package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtService;

/**
 * ==========================================================================
 *  END-TO-END INTEGRATION — PHASE 9 (33-njy bend)
 * ==========================================================================
 *
 *  🔴 BU TESTIŇ TÄZEŞI:
 *     Öňki testler her birini AÝRATYN barlapdyr: "deposit işleýärmi",
 *     "transfer işleýärmi", "admin görýärmi". Emma olar biri-birinden
 *     KÖZ KÖRMEÝÄRDI.
 *
 *     Bu test 20 ädimi BIR TOPARDA geçirýär - ol biziň bankymyzyň
 *     ÄHLI özara baglanyşygyny göreýär. Eger bir ýerde ýalňyş bolsa
 *     (mysal üçin, admin görkeziji ýalňyş esaby görkeýän bolsa) -
 *     bu test ŞOL ýalňyşy tutýar, ýöne haýsy testiň faýly bolan
 *     ýok - sebäbi olar arasynda DÜZGÜN BAR.
 *
 *     Şeýle hem: summa yzarlanýar. 1000 + 500 - 200 - 50 = 1250
 *     A-da, B-de 250. Eger bir ýerde bug bolsa, SANY DÜZGÜN BOZULÝAR -
 *     ýaňyş kod görünýär ("men näme edenim?"), ýa-da ýalňyş ýere
 *     geçirilýär.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EndToEndIntegrationTest {

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

    @PersistenceContext
    private EntityManager entityManager;

    private static final String PASSWORD = "Password123!";
    private static final String JSON = "application/json";

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("FULL FLOW: 20 ädim - register -> esap -> login -> akça -> admin -> RBAC")
    void completeEndToEndFlow() throws Exception {

        // ---------- ADIM 1-2: REGISTER ----------
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"Ali A\",\"email\":\"ali@bank.test\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"Bekir B\",\"email\":\"bekir@bank.test\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        // ---------- ADIM 3-4: CREATE ACCOUNTS ----------
        mockMvc.perform(post("/api/accounts").contentType(JSON))
                .andExpect(status().isUnauthorized());   // 21: token gerek

        // Login (5-6) - ARKALYNDAN esap açmaly bolmaz, token gerek
        String tokenA = login("ali@bank.test");
        String tokenB = login("bekir@bank.test");

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(0.0000));
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isCreated());

        String numberA = numberOf("ali@bank.test");
        String numberB = numberOf("bekir@bank.test");
        assertThat(numberA).isNotEqualTo(numberB);

        // ---------- ADIM 7-8: DEPOSITS ----------
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(JSON)
                        .content("{\"amount\":1000,\"description\":\"A salary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.0000));

        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(JSON)
                        .content("{\"amount\":500,\"description\":\"B salary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.0000));

        // ---------- ADIM 9: TRANSFER A -> B (200) ----------
        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(JSON)
                        .content("{\"toAccountNumber\":\"" + numberB + "\","
                                + "\"amount\":200,\"description\":\"for B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromBalance").value(800.0000))
                .andExpect(jsonPath("$.toBalance").value(700.0000));

        // ---------- ADIM 10-11: BALANCES ----------
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.balance").value(800.0000));
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.balance").value(700.0000));

        // ---------- ADIM 12-13: HISTORIES ----------
        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.totalElements").value(2))
                // newest first
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"));

        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"));

        // ---------- ADIM 14-15: ADMIN ----------
        // Registration ALWAYS creates USER (mass assignment protection).
        // PHASE 8 has NO role-mutation API -> admin is created by DBA
        // (same as production: nobody can self-promote).
        User a = userRepository.findByEmail("ali@bank.test").orElseThrow();
        userRepository.save(new User("Admin Boss", "admin@bank.test",
                passwordEncoder.encode(PASSWORD), Role.ADMIN));
        String tokenAdmin = login("admin@bank.test");

        // ---------- ADIM 16-18: ADMIN VIEWS ----------
        mockMvc.perform(get("/api/admin/users?size=10")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        Long accountIdA = accountRepository.findByUserId(a.getId())
                .orElseThrow().getId();

        mockMvc.perform(get("/api/admin/accounts?size=10")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].userEmail").exists());

        mockMvc.perform(get("/api/admin/accounts/" + accountIdA + "/transactions")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // ---------- ADIM 19: USER -> ADMIN ENDPOINT = 403 ----------
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/accounts/" + accountIdA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/accounts/" + accountIdA + "/transactions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        // ---------- ADIM 20: NO TOKEN = 401 ----------
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/accounts/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/accounts/me/transactions"))
                .andExpect(status().isUnauthorized());

        // ---------- SANY DÜZGÜNI ----------
        // A: 1000 - 200 = 800 (2 ýazgy)
        // B: 500 + 200 = 700 (2 ýazgy)
        // Jemi akcha yzarlanýar: 800 + 700 = 1500 = 1000 + 500
        Account finalA = accountRepository.findById(accountIdA).orElseThrow();
        BigDecimal totalA = finalA.getBalance();
        BigDecimal totalB = accountRepository
                .findByUserId(userRepository.findByEmail("bekir@bank.test")
                        .orElseThrow().getId())
                .orElseThrow().getBalance();

        assertThat(totalA).isEqualByComparingTo("800");
        assertThat(totalB).isEqualByComparingTo("700");
        // 🧡 Akcha ÝITMEDI, DÜŞMEDI - 1500 (transfer bir ýere geçirdi)
        assertThat(totalA.add(totalB)).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("FAILED FLOW: transfer näbelli esaba -> 404, balanslar ÜÝTGEÝÄR")
    void failedTransferDoesNotChangeBalances() throws Exception {
        // 🛡️ Atomicity: näbelli destination -> hiç bir ýazuw bolmaly.
        String tokenA = registerAndLogin("x@bank.test");
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());
        deposit(tokenA, "1000");

        mockMvc.perform(post("/api/accounts/me/transfer")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(JSON)
                        .content("{\"toAccountNumber\":\"000000000000000000\",\"amount\":500}"))
                .andExpect(status().isNotFound());

        // 🧡 Balans doly ÜÝTGEMEDI - 1000
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.balance").value(1000.0000));

        // 🧡 Žurnalda diňe bir ýazgy (DEPOSIT) - transfer ýazgy ÝOK
        mockMvc.perform(get("/api/accounts/me/transactions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"));
    }

    // ---------- KÖMEKÇILER ----------

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"fullName\":\"U\",\"email\":\"" + email + "\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + email + "\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
    }

    private void deposit(String token, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"amount\": " + amount + "}"))
                .andExpect(status().isOk());
    }

    private String numberOf(String email) throws Exception {
        return accountRepository.findByUserId(
                        userRepository.findByEmail(email).orElseThrow().getId())
                .orElseThrow().getAccountNumber();
    }
}
