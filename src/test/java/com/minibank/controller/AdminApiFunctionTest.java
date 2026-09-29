package com.minibank.controller;

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

import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtService;

/**
 * ADMIN FUNKSIONAL SINOZLARY — PHASE 8.
 *
 *  Adaky testler KIM GIRÄP BILÄR diýe barlapdyr. Bu testler
 *  "ADMIN girende näme görýär" diýe barlaydi: sanaw, gözleg,
 *  sahypa, süzgüç, 404, N+1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminApiFunctionTest {

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

    private String adminToken;

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User("Admin Boss", "admin@example.com",
                passwordEncoder.encode(PASSWORD), Role.ADMIN));
        adminToken = jwtService.generateToken(
                userRepository.findByEmail("admin@example.com").orElseThrow());
    }

    private User givenUser(String name, String email) {
        return userRepository.save(new User(
                name, email, passwordEncoder.encode(PASSWORD), Role.USER));
    }

    private Account givenAccount(User user) {
        return accountRepository.save(new Account(
                user, "40817810" + String.format("%010d", user.getId()),
                Account.RUB, BigDecimal.ZERO.setScale(4)));
    }

    private void deposit(User user, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + jwtService.generateToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + "}"))
                .andExpect(status().isOk());
    }

    // ===================== USERS =====================

    @Test
    @DisplayName("1. admin user list - ähli maglumatlar bar (updatedAt hem)")
    void adminSeesAllUsers() throws Exception {
        givenUser("Ali A", "ali@example.com");
        givenUser("Bekir B", "bekir@example.com");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))   // admin + 2
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].fullName").exists())
                .andExpect(jsonPath("$.content[0].email").exists())
                .andExpect(jsonPath("$.content[0].role").exists())
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].updatedAt").exists());
    }

    @Test
    @DisplayName("2. user pagination: 25 kişi, size=10 -> 10/10/5")
    void userPagination() throws Exception {
        for (int i = 0; i < 25; i++) {
            givenUser("User " + i, "user" + i + "@example.com");
        }

        mockMvc.perform(get("/api/admin/users?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(26))   // 25 + admin
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/admin/users?page=2&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content.length()").value(6))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("3. search by fullName (harpuly EMES - case-insensitive)")
    void searchByName() throws Exception {
        givenUser("Sapar Jumabaev", "sapar@example.com");
        givenUser("Ali A", "ali@example.com");

        // 🔴 "sapar" - kiçi harp bilen, ýöne at "Sapar" bilen.
        mockMvc.perform(get("/api/admin/users?search=sapar")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("sapar@example.com"));
    }

    @Test
    @DisplayName("4. search by email (case-insensitive)")
    void searchByEmail() throws Exception {
        givenUser("Ali A", "ali@example.com");
        givenUser("Bekir B", "bekir@example.com");

        mockMvc.perform(get("/api/admin/users?search=BEKIR")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("bekir@example.com"));
    }

    @Test
    @DisplayName("5. search ýoksa - ähli kollonuwçular")
    void noSearchReturnsAll() throws Exception {
        givenUser("Ali A", "ali@example.com");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("6. user by id - 200, fullName we email dogry")
    void userById() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");

        mockMvc.perform(get("/api/admin/users/" + user.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.fullName").value("Ali A"))
                .andExpect(jsonPath("$.email").value("ali@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("7. ýok kollonuwçu -> 404")
    void nonexistentUserReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/users/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("8. user pagination validation: size=101 -> 400")
    void userPaginationValidation() throws Exception {
        mockMvc.perform(get("/api/admin/users?size=101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // ===================== ACCOUNTS =====================

    @Test
    @DisplayName("9. admin account list - user maglumaty BILEN (N+1 ýok)")
    void adminAccountListIncludesUser() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        givenAccount(user);

        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accountNumber").exists())
                .andExpect(jsonPath("$.content[0].userId").value(user.getId()))
                .andExpect(jsonPath("$.content[0].userEmail").value("ali@example.com"))
                .andExpect(jsonPath("$.content[0].userFullName").value("Ali A"))
                .andExpect(jsonPath("$.content[0].currency").value("RUB"))
                .andExpect(jsonPath("$.content[0].balance").exists());
    }

    @Test
    @DisplayName("10. account pagination")
    void accountPagination() throws Exception {
        for (int i = 0; i < 12; i++) {
            givenAccount(givenUser("User " + i, "u" + i + "@example.com"));
        }

        mockMvc.perform(get("/api/admin/accounts?page=0&size=5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    @DisplayName("11. account filter by userId")
    void accountFilterByUserId() throws Exception {
        User a = givenUser("Ali A", "ali@example.com");
        User b = givenUser("Bekir B", "bekir@example.com");
        givenAccount(a);
        givenAccount(b);

        mockMvc.perform(get("/api/admin/accounts?userId=" + a.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userEmail").value("ali@example.com"));
    }

    @Test
    @DisplayName("12. account by id - 200, ýok bolmasa 404")
    void accountByIdAnd404() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);

        mockMvc.perform(get("/api/admin/accounts/" + account.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()))
                .andExpect(jsonPath("$.userEmail").value("ali@example.com"));

        mockMvc.perform(get("/api/admin/accounts/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ===================== TRANSACTIONS =====================

    @Test
    @DisplayName("13. admin transaction history - iň täze BIRINJI")
    void adminTransactionHistoryNewestFirst() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);
        deposit(user, "100");
        deposit(user, "200");

        mockMvc.perform(get("/api/admin/accounts/" + account.getId() + "/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(200.0000))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(300.0000))
                .andExpect(jsonPath("$.content[1].amount").value(100.0000));
    }

    @Test
    @DisplayName("14. transaction pagination")
    void adminTransactionPagination() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);
        for (int i = 0; i < 12; i++) {
            deposit(user, "10");
        }

        mockMvc.perform(get("/api/admin/accounts/" + account.getId()
                        + "/transactions?page=0&size=5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    @DisplayName("15. transaction type filter")
    void adminTransactionTypeFilter() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);
        deposit(user, "100");

        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + jwtService.generateToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 50}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/accounts/" + account.getId()
                        + "/transactions?type=WITHDRAW")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAW"));
    }

    @Test
    @DisplayName("16. nädogry type -> 400")
    void adminInvalidTypeReturns400() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);

        mockMvc.perform(get("/api/admin/accounts/" + account.getId()
                        + "/transactions?type=INVALID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("17. date filter (from/to)")
    void adminTransactionDateFilter() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);
        deposit(user, "100");

        // Geljek wagtdan - hiç bir zady gabat gelmeýär
        mockMvc.perform(get("/api/admin/accounts/" + account.getId()
                        + "/transactions?from=2030-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Geçmiş wagtdan - hemmesi
        mockMvc.perform(get("/api/admin/accounts/" + account.getId()
                        + "/transactions?from=2020-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("18. amount filter (min/max)")
    void adminTransactionAmountFilter() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);
        deposit(user, "50");
        deposit(user, "500");
        deposit(user, "5000");

        mockMvc.perform(get("/api/admin/accounts/" + account.getId()
                        + "/transactions?minAmount=100&maxAmount=1000")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(500.0000));
    }

    @Test
    @DisplayName("19. ýok esabyň taryhy -> 404 (200 we boş däl)")
    void transactionsOfNonexistentAccountReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/accounts/999999/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("20. transaction size=101 -> 400")
    void adminTransactionSizeValidation() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);

        mockMvc.perform(get("/api/admin/accounts/" + account.getId()
                        + "/transactions?size=101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("21. from > to -> 400")
    void adminFromAfterToReturns400() throws Exception {
        User user = givenUser("Ali A", "ali@example.com");
        Account account = givenAccount(user);

        mockMvc.perform(get("/api/admin/accounts/" + account.getId()
                        + "/transactions?from=2030-01-01T00:00:00Z&to=2020-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }
}
