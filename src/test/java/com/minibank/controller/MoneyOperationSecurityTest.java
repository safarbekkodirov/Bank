package com.minibank.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtService;

/**
 * 🛡️ АКЧА ОПЕРАЦИЯЛАРЫНЫН КООПСУЗДУК ТЕСТИ (тапшырма 26-29).
 *
 * 🧡 БИЗ БУЛ ЖЕРДЕ ЭЧ КИМДИН АКЧАСЫНА ТЕГЕ БОЛБОЙТ — ДЕП ТЕКШЕРЕБИЗ.
 *     Бул эндпоинттер «бир адамдын 700 рублинди алуу» кылыкча —
 *     ошондуктан аларга колдонуучунун ID'нин өзгөрүшү = колдонуучунун
 *     акчасынын өзгөрүшү.
 *
 * 🧡 ЭН МААНИЛҮҮСҮ: IDOR.
 *     IDOR = «өзгөрүүчүн колдонуучу чын эле ээси болобос, дагы
 *     бирөөнүнүн маалыматына кол кийизүү» деген сыяктуу колдонуу.
 *     Биз аны үч жол менен каршылык кылабыз:
 *       1. СҮРӨТТӨ id ЖОК (эндпоинт «/me» гана)
 *       2. СҮРӨТТӨН ?userId= ЖОК (параметр угулбайт)
 *       3. БИЗДИН КОДО idни СҮРӨТТӨН ЭМЕС, ТОКЕННЕН алат
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MoneyOperationSecurityTest {

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

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User givenUserWithAccount(String email) throws Exception {
        User user = userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode(PASSWORD), Role.USER));
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isCreated());
        return user;
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private BigDecimal balanceOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    /** A'нын эсебине 1000 салып, B'нин эсебин 500 салбай калбайбыз. */
    private void seedBalance(User user, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + "}"))
                .andExpect(status().isOk());
    }
    // ==================== 26. IDOR: A башканын эсебине КИРЕ АЛБАЙТ ====================

    @Test
    @DisplayName("26. 🛡️ A башка колдонуучунун эсебинен АКЧА ЧЫГАРА АЛБАЙТ")
    void userACannotWithdrawFromUserB() throws Exception {
        User userA = givenUserWithAccount("idor-withdraw-a@example.com");
        User userB = givenUserWithAccount("idor-withdraw-b@example.com");
        seedBalance(userA, "1000.0000");
        seedBalance(userB, "1000.0000");

        // 🔴 A «мен Bнын эсебинен кеткиргим келет» деп аракет кылат.
        //    Чыныгы жооп: Aнын ӨЗ эсебинен 300 кетет. Bнын эсеби
        //    КАЛЫП ТУРАТ — бир тыйын да кетпегендей.
        mockMvc.perform(post("/api/accounts/me/withdraw")
                        .header("Authorization", "Bearer " + tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 300.0000, \"userId\": " + userB.getId() + "}"))
                .andExpect(status().isOk());

        // 🧡 Акча АЛДЫНДА БӨЛҮНДӨ КЕТТИ — Aдын эсебинен.
        assertThat(balanceOf(userA)).isEqualByComparingTo("700.0000");

        // 🔴 B ТАЗА ҮЧҮН. Эгер B = 700.0000 болсо, бул тест өтмөйт.
        assertThat(balanceOf(userB)).isEqualByComparingTo("1000.0000");
    }

    // ==================== 27. IDOR: A башканын эсебине САЛА АЛБАЙТ ====================

    @Test
    @DisplayName("27. 🛡️ A башка колдонуучунун эсебине АКЧА САЛА АЛБАЙТ")
    void userACannotDepositToUserB() throws Exception {
        User userA = givenUserWithAccount("idor-deposit-a@example.com");
        User userB = givenUserWithAccount("idor-deposit-b@example.com");
        seedBalance(userA, "1000.0000");
        seedBalance(userB, "1000.0000");

        // 🔴 «Bнын эсебине сал» — Aдын токени менен.
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 300.0000, \"userId\": " + userB.getId() + "}"))
                .andExpect(status().isOk());

        // 🧡 Акча АНЫН ӨЗ ЭСЕБИНЕ кошулду.
        assertThat(balanceOf(userA)).isEqualByComparingTo("1300.0000");

        // 🔴 B ТАЗА ҮЧҮН — «кассага акча сал» үчүн эсеп ТААНДЫК болсо,
        //    чыгаруучу кассага ИСЕМИНЕ жазып, чын эле 300 коштура алат эле.
        assertThat(balanceOf(userB)).isEqualByComparingTo("1000.0000");
    }

    // ==================== 28. ?userId= ПАРАМЕТР КУЧСҮЗ ====================

    @Test
    @DisplayName("28. 🛡️ ?userId= параметри чыныгы колдонуучуну АШЫП КЕТПЕЙТ")
    void queryParamUserIdDoesNotOverrideAuthenticatedUser() throws Exception {
        User userA = givenUserWithAccount("param-a@example.com");
        User userB = givenUserWithAccount("param-b@example.com");
        seedBalance(userA, "1000.0000");
        seedBalance(userB, "1000.0000");

        // 🔴 Эң ЖАШЫРЫН ТАРТИК ТАҮКӨН: «?userId=» — эндпоинт
        //    ПАРАМЕТР КАБЫЛДАБАЙТ. Чыгаруучу Aдын атынан жазып,
        //    Bнын эсебине кирүүгө аракет кылат. Жооп — параметр
        //    КАРАЛБАЙТ, «/me» = «сен киммин?» деп окуйт.
        mockMvc.perform(post("/api/accounts/me/deposit?userId=" + userB.getId())
                        .header("Authorization", "Bearer " + tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.0000}"))
                .andExpect(status().isOk());

        assertThat(balanceOf(userA)).isEqualByComparingTo("1100.0000");
        assertThat(balanceOf(userB)).isEqualByComparingTo("1000.0000");
    }

    // ==================== 29. accountId ПАРАМЕТР КУЧСҮЗ ====================

    @Test
    @DisplayName("29. 🛡️ accountId параметри башка эсепке ЖЕТКИЗБЕЙТ")
    void accountIdParameterDoesNotGrantAccessToAnotherAccount() throws Exception {
        User userA = givenUserWithAccount("acct-a@example.com");
        User userB = givenUserWithAccount("acct-b@example.com");
        seedBalance(userA, "1000.0000");
        seedBalance(userB, "1000.0000");

        Long accountIdB = accountRepository.findByUserId(userB.getId())
                .orElseThrow().getId();

        // (a) 🔴 СҮРӨТКӨ id ЖАЗЫП, БАШКА ЭСЕПКЕ ӨТҮҮГӨ АРАКЕТ.
        //     Мындай эндпоинт БИЗДЕ ЖОК (/me гана) -> 404.
        mockMvc.perform(post("/api/accounts/" + accountIdB + "/deposit")
                        .header("Authorization", "Bearer " + tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.0000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // (b) 🔴 СҮРОТТҮН ӨЗҮНДЕ id талаасын кошсо — эч кандай таамал
        //     эмес: эндпоинт аны ОКУМБАЙТ, анын өз эсебине иштейт.
        //     (Jackson чүйлүш талаларын СИЛКИМЕЙТ — демек 400 да
        //     чыкпайт, жооп 200, акча АНЫН ӨЗ эсебине кетет.)
        mockMvc.perform(post("/api/accounts/me/deposit")
                        .header("Authorization", "Bearer " + tokenFor(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.0000, \"accountId\": " + accountIdB + "}"))
                .andExpect(status().isOk());

        // 🧡 Акча АГА ӨЗ ЭСЕБИНЕ кошулду, Bнын эсеби өЗГӨРҮЛБӨДҮ.
        assertThat(balanceOf(userA)).isEqualByComparingTo("1100.0000");
        assertThat(balanceOf(userB)).isEqualByComparingTo("1000.0000");
    }
}