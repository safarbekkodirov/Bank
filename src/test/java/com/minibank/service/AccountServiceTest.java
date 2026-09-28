package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.account.AccountResponse;
import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.exception.AccountAlreadyExistsException;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.UserNotFoundException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.UserRepository;

/**
 * ЭСЕП ТЕСТИ (Service катмары) — чыныгы PostgreSQL менен.
 *
 * 🧠 Эмне чыныгы база? Биз бул жерде ЭКИ нерсени текшириш керек:
 *   1. Java Entity база менен таптак келеби (ddl-auto=validate)
 *   2. Эрежелер (UNIQUE(user_id), CHECK) чыныгы иштеп жатабы
 *   Экөөнү тексиргенде ЭЛЕ маалымат базасы керек.
 */
@SpringBootTest
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanTables() {
        // 🧡 accounts -> users тартибин сактоо КЕРЕК. Себеби:
        //    эгер users'ти башында өчүрсөк, accounts табына FK ката берет.
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User givenUser(String email) {
        return userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode("Password123!"), Role.USER));
    }

    @Test
    @DisplayName("1. Эсеп ийгиликтүү түзүлөт")
    void createsAccountSuccessfully() {
        User user = givenUser("account-1@example.com");

        AccountResponse response = accountService.createAccount(user.getId());

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(accountRepository.findById(response.id())).isPresent();
    }

    @Test
    @DisplayName("2. Баштапкы баланс = 0.0000 (4 каттуу тык формат)")
    void initialBalanceIsZeroWithScaleFour() {
        User user = givenUser("balance@example.com");

        AccountResponse response = accountService.createAccount(user.getId());

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        // 🧠 Scale = 4 -> JSON'да "0.0000" чыгат, "0" эмес
        assertThat(response.balance().scale()).isEqualTo(4);
        assertThat(response.balance()).isEqualTo(new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("3. Валюта = RUB")
    void currencyIsRub() {
        User user = givenUser("currency@example.com");

        AccountResponse response = accountService.createAccount(user.getId());

        assertThat(response.currency()).isEqualTo("RUB");
    }

    @Test
    @DisplayName("4. Эсеп номуру: 18 белги, цифрден башка жок, банк кодунен башталат")
    void generatesEighteenDigitAccountNumber() {
        User user = givenUser("number@example.com");

        String number = accountService.createAccount(user.getId()).accountNumber();

        assertThat(number).hasSize(18);
        assertThat(number).matches("\\d{18}");
        assertThat(number).startsWith("40817810");
    }

    @Test
    @DisplayName("5. Эсеп номурлары УНИКАЛДУУ")
    void accountNumbersAreUnique() {
        AccountResponse first = accountService.createAccount(
                givenUser("uniq-1@example.com").getId());
        AccountResponse second = accountService.createAccount(
                givenUser("uniq-2@example.com").getId());

        assertThat(first.accountNumber()).isNotEqualTo(second.accountNumber());
        assertThat(accountRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("6. Бир колдонуучуга экинчи эсеп ачылбайт -> 409")
    void rejectsSecondAccountForSameUser() {
        User user = givenUser("dup@example.com");
        accountService.createAccount(user.getId());

        assertThatThrownBy(() -> accountService.createAccount(user.getId()))
                .isInstanceOf(AccountAlreadyExistsException.class)
                .hasMessageContaining("already has a bank account");

        assertThat(accountRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("7. Колдонуучу жок -> UserNotFoundException")
    void throwsWhenUserNotFound() {
        assertThatThrownBy(() -> accountService.createAccount(999999L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("8. Эсеп жок -> AccountNotFoundException (404)")
    void throwsWhenAccountNotFound() {
        User user = givenUser("no-account@example.com");

        assertThatThrownBy(() -> accountService.getMyAccount(user.getId()))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("No bank account");
    }

    @Test
    @DisplayName("9. Эсеп ээси колдонуучуга туура (User байланышы)")
    void accountBelongsToCorrectUser() {
        User user = givenUser("owner@example.com");

        AccountResponse response = accountService.createAccount(user.getId());

        Account account = accountRepository.findById(response.id()).orElseThrow();
        assertThat(account.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("10. getMyAccount() -> эсепти кайтарады")
    void getMyAccountReturnsAccount() {
        User user = givenUser("mine@example.com");
        AccountResponse created = accountService.createAccount(user.getId());

        AccountResponse found = accountService.getMyAccount(user.getId());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.accountNumber()).isEqualTo(created.accountNumber());
    }

    @Test
    @DisplayName("11. Ар башка колдонуучу ӨЗ эсебин гана көрөт")
    void userSeesOnlyOwnAccount() {
        User userA = givenUser("user-a@example.com");
        User userB = givenUser("user-b@example.com");
        AccountResponse accountA = accountService.createAccount(userA.getId());
        accountService.createAccount(userB.getId());

        // A өз эсебин көрөт
        assertThat(accountService.getMyAccount(userA.getId()).accountNumber())
                .isEqualTo(accountA.accountNumber());

        // B өз эсебин көрөт — A эмес!
        assertThat(accountService.getMyAccount(userB.getId()).accountNumber())
                .isNotEqualTo(accountA.accountNumber());
    }

    @Test
    @DisplayName("12. Эсеп номур боюнча издөө иштейт (банкомат сценарийи)")
    void findByAccountNumberWorks() {
        User user = givenUser("find@example.com");
        AccountResponse created = accountService.createAccount(user.getId());

        Account found = accountRepository.findByAccountNumber(created.accountNumber())
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(created.id());
    }

    @Test
    @DisplayName("13. Жоопто колдонуучу жана пароль ЖОК (Entity ≠ DTO)")
    void responseHasNoUserOrPassword() {
        User user = givenUser("safe@example.com");
        AccountResponse response = accountService.createAccount(user.getId());

        assertThat(response.toString())
                .doesNotContain("password")
                .doesNotContain("passwordHash")
                .doesNotContain("user")
                .contains(response.accountNumber())
                .contains("RUB");
    }

    @Test
    @DisplayName("14. Эсепи жок колдонуучу -> findByUser бош, existsByUser = false")
    void noAccountMeansEmptyResult() {
        User user = givenUser("empty@example.com");

        assertThat(accountRepository.findByUserId(user.getId())).isEmpty();
        assertThat(accountRepository.existsByUserId(user.getId())).isFalse();
        // Генератордун узундугу чыныгы 18
        assertThat(AccountNumberGenerator.numberLength()).isEqualTo(18);
    }
}
