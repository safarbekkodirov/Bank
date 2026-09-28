package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.transaction.TransferRequest;
import com.minibank.dto.transaction.TransferResponse;
import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.exception.InvalidAmountException;
import com.minibank.exception.SelfTransferException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

/**
 * 🔄 TRANSFER ТЕСТИ (тапшырманын 23-бөлүмү, 1-16-пункт).
 *
 * 🧠 БИЗ ЧЫНЫГЫ PostgreSQL МЕНЕН ТЕКШЕРЕБИЗ — эс а ханс. Себеби:
 *     1. Java Entity база менен таптак келеби (ddl-auto=validate)
 *     2. Маалымат базасындагы CHECK эрежелери ЧЫНЫГЫ иштеп жатабы
 *     3. Кулупка (FOR UPDATE) чыныгы PostgreSQL'де иштеп жатабы
 *        — эс а болсо, биз «биз кулопка алдык» деп ЫЙЛАП, чыныгыда
 *        коргоо БОЛБОГОН абалды өтүрүп өткөн болобуз.
 *
 * 🧡 @Transactional ТЕСТТЕ = «тест бүткөндө РОЛЛБЭК» -> база таза.
 *     Бирок @BeforeEach deleteAll() да керек: тест бири-бирин
 *     көрбөшү үчүн (колдонуучулар таанылбасын).
 */
@SpringBootTest
@Transactional
class TransferServiceTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private MoneyService moneyService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanTables() {
        // 🧡 ТАРТИБ МААНИЛҮҮ: transactions -> accounts -> users (FK RESTRICT).
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Эсеп ачып, аны берилген баланс менен толгон кылат. */
    private User givenUserWithBalance(String email, String balance) {
        User user = userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode("Password123!"), Role.USER));
        accountService.createAccount(user.getId());
        moneyService.deposit(user.getId(), new BigDecimal(balance), "seed");
        return user;
    }

    /**
     * 🧡 Эсеп АЧЫЛГАН, бирок БОШ (0.0000) колдонуучу.
     *
     * 🟡 «Көчүрүү» жазып, анын суммасын 0 берүү МҮМКҮН ЭМЕС:
     *     amount > 0 болушу керек (PHASE 5теги эреже). Бул ТУУРА
     *     иштеши — биз «0 салууга болобу?» деп 6 жыл мурун чечкен.
     *     Демек «бос эсеп» керек болгондо, аны АЧЫП, көчүрүү
     *     КЫЛБАЙ калдырыбыз.
     */
    private User givenUserWithEmptyAccount(String email) {
        User user = userRepository.save(new User(
                "Тест " + email, email, passwordEncoder.encode("Password123!"), Role.USER));
        accountService.createAccount(user.getId());
        return user;
    }

    private User givenUserWithoutAccount(String email) {
        return userRepository.save(new User(
                "Эсепсіз " + email, email,
                passwordEncoder.encode("Password123!"), Role.USER));
    }

    private BigDecimal balanceOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    private Long accountIdOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getId();
    }

    private String accountNumberOf(User user) {
        return accountRepository.findByUserId(user.getId()).orElseThrow().getAccountNumber();
    }

    private TransferRequest request(String toAccountNumber, String amount, String description) {
        return new TransferRequest(toAccountNumber, new BigDecimal(amount), description);
    }

    private List<Transaction> ledgerOf(User user) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountIdOf(user));
    }

    // ==================== 1-8: ИЙГИЛИК ====================

    @Test
    @DisplayName("1. 🔄 Transfer ийгиликтүү (жаооп толук)")
    void transferSucceeds() {
        User a = givenUserWithBalance("t-ok-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-ok-b@example.com", "500.0000");

        TransferResponse response = transferService.transfer(a.getId(),
                request(accountNumberOf(b), "300.0000", "Transfer to another account"));

        // 🧡 Тапшырманын 1-мисалы: A=1000, B=500, 300 котору -> 700 / 800
        assertThat(response.fromBalance()).isEqualByComparingTo("700.0000");
        assertThat(response.toBalance()).isEqualByComparingTo("800.0000");
        assertThat(response.amount()).isEqualByComparingTo("300.0000");
        assertThat(response.fromAccountNumber()).isEqualTo(accountNumberOf(a));
        assertThat(response.toAccountNumber()).isEqualTo(accountNumberOf(b));
        assertThat(response.currency()).isEqualTo("RUB");
        assertThat(response.transactionId()).isNotNull();
    }

    @Test
    @DisplayName("2. 💸 Source баланс КЕМИРЕТ (1000 → 700)")
    void sourceBalanceDecreases() {
        User a = givenUserWithBalance("t-src-a@example.com", "1000.0000");
        User b = givenUserWithEmptyAccount("t-src-b@example.com");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "250.0000", null));

        assertThat(balanceOf(a)).isEqualByComparingTo("750.0000");
    }

    @Test
    @DisplayName("3. 💰 Destination баланс КӨБӨТ (500 → 800)")
    void destinationBalanceIncreases() {
        User a = givenUserWithBalance("t-dst-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-dst-b@example.com", "500.0000");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "300.0000", null));

        assertThat(balanceOf(b)).isEqualByComparingTo("800.0000");
    }

    @Test
    @DisplayName("4. 📖 Source'та TRANSFER_OUT жазуусу түзүлөт")
    void sourceCreatesTransferOut() {
        User a = givenUserWithBalance("t-out-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-out-b@example.com", "500.0000");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "300.0000", null));

        List<Transaction> ledger = ledgerOf(a);
        // 🧡 1 «көчүрүү» (DEPOSIT 1000) + 1 TRANSFER_OUT
        assertThat(ledger).hasSize(2);

        Transaction latest = ledger.get(0);
        assertThat(latest.getType()).isEqualTo(TransactionType.TRANSFER_OUT);

        // 🔴 11-бөлүм: WITHDRAW ЭМЕС, TRANSFER_OUT.
        assertThat(latest.getType()).isNotEqualTo(TransactionType.WITHDRAW);
    }

    @Test
    @DisplayName("5. 📖 Destination'та TRANSFER_IN жазуусу түзүлөт")
    void destinationCreatesTransferIn() {
        User a = givenUserWithBalance("t-in-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-in-b@example.com", "500.0000");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "300.0000", null));

        List<Transaction> ledger = ledgerOf(b);
        // 🧡 1 «көчүрүү» (DEPOSIT 500) + 1 TRANSFER_IN
        assertThat(ledger).hasSize(2);

        Transaction latest = ledger.get(0);
        assertThat(latest.getType()).isEqualTo(TransactionType.TRANSFER_IN);

        // 🔴 11-бөлүм: DEPOSIT ЭМЕС, TRANSFER_IN.
        assertThat(latest.getType()).isNotEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    @DisplayName("6. 🔴 Сумма экөө жазууда да ПОЗИТИВДУУ (+300, -300 ЭМЕС)")
    void transferAmountIsPositiveInBothRecords() {
        User a = givenUserWithBalance("t-pos-a@example.com", "1000.0000");
        User b = givenUserWithEmptyAccount("t-pos-b@example.com");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "300.0000", null));

        BigDecimal outAmount = ledgerOf(a).get(0).getAmount();
        BigDecimal inAmount = ledgerOf(b).get(0).getAmount();

        assertThat(outAmount).isEqualByComparingTo("300.0000");
        assertThat(inAmount).isEqualByComparingTo("300.0000");
        assertThat(outAmount.signum()).isPositive();
        assertThat(inAmount.signum()).isPositive();
    }

    @Test
    @DisplayName("7. 📖 balanceAfter экөөсүндө да туура (700 жана 800)")
    void balanceAfterIsCorrectInBothRecords() {
        User a = givenUserWithBalance("t-after-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-after-b@example.com", "500.0000");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "300.0000", null));

        // 🧡 14-бөлүм: balanceAfter = ОПЕРАЦИЯДАН КИЙИНКИ абал.
        assertThat(ledgerOf(a).get(0).getBalanceAfter()).isEqualByComparingTo("700.0000");
        assertThat(ledgerOf(b).get(0).getBalanceAfter()).isEqualByComparingTo("800.0000");
    }

    @Test
    @DisplayName("8. 📝 Description экөө жазууда да сакталат")
    void descriptionIsSavedInBothRecords() {
        User a = givenUserWithBalance("t-desc-a@example.com", "1000.0000");
        User b = givenUserWithEmptyAccount("t-desc-b@example.com");

        transferService.transfer(a.getId(),
                request(accountNumberOf(b), "300.0000", "Rent payment"));

        assertThat(ledgerOf(a).get(0).getDescription()).isEqualTo("Rent payment");
        assertThat(ledgerOf(b).get(0).getDescription()).isEqualTo("Rent payment");
    }

    // ==================== 9-11: 404 / 409 ====================

    @Test
    @DisplayName("9. 🔴 Destination эсеп ЖОК -> AccountNotFoundException (404)")
    void destinationNotFoundThrows() {
        User a = givenUserWithBalance("t-nodst@example.com", "1000.0000");

        assertThatThrownBy(() -> transferService.transfer(a.getId(),
                request("408178100000099999", "100.0000", null)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Destination account not found");

        // 🔴 БАЛАНС ӨЗГӨРБӨДҮ (эсеп табылбады -> иш башталбады).
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");
        // 🧡 Журналда жаңы жазуу ЖОК.
        assertThat(ledgerOf(a)).hasSize(1);
    }

    @Test
    @DisplayName("10. 🔴 Source эсеп ЖОК -> AccountNotFoundException (404)")
    void sourceAccountNotFoundThrows() {
        User a = givenUserWithoutAccount("t-nosrc@example.com");
        User b = givenUserWithBalance("t-nosrc-b@example.com", "500.0000");

        assertThatThrownBy(() -> transferService.transfer(a.getId(),
                request(accountNumberOf(b), "100.0000", null)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("No bank account");

        // 🔴 B ТАЗА ҮЧҮН — чыгаруучу эсеби жок экенин билсе да,
        //    анын акчасына КАСЫ ТИЙБЕЙТ.
        assertThat(balanceOf(b)).isEqualByComparingTo("500.0000");
    }

    @Test
    @DisplayName("11. 🔴 Акча ЖЕТПЕЙТ -> 409, экөө баланс ӨЗГӨРБӨЙТ")
    void insufficientFundsLeavesBothBalancesUntouched() {
        User a = givenUserWithBalance("t-insuf-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-insuf-b@example.com", "500.0000");

        assertThatThrownBy(() -> transferService.transfer(a.getId(),
                request(accountNumberOf(b), "1200.0000", "too much")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        // 🔴 ЭКӨӨ БАЛАНС ТАЗА ҮЧҮН (9-бөлүм: A да, B да).
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");
        assertThat(balanceOf(b)).isEqualByComparingTo("500.0000");

        // 🧡 ЖУРНАЛДА ЭЧ НЕРСЕ ЖОК — transfer өтпөгөн.
        assertThat(ledgerOf(a)).hasSize(1);
        assertThat(ledgerOf(b)).hasSize(1);
    }

    // ==================== 12-13: ЧЕГИНЕ ЖЕТКЕН ====================

    @Test
    @DisplayName("12. 🧡 БАЛАНСИНЧА котору -> A = 0, B = бары + сумма")
    void exactBalanceTransferSucceeds() {
        User a = givenUserWithBalance("t-exact-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-exact-b@example.com", "500.0000");

        TransferResponse response = transferService.transfer(a.getId(),
                request(accountNumberOf(b), "1000.0000", "all of it"));

        // 🔴 10-бөлүм: 1000 = 1000 -> УРУКСАТ. A = 0, B = 500+1000
        assertThat(response.fromBalance()).isEqualByComparingTo("0.0000");
        assertThat(response.toBalance()).isEqualByComparingTo("1500.0000");

        assertThat(balanceOf(a)).isEqualByComparingTo("0.0000");
        assertThat(balanceOf(b)).isEqualByComparingTo("1500.0000");

        // 🧡 ФОРМАТ: «0» ЭМЕС, «0.0000» болууга тийиш.
        assertThat(balanceOf(a).scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("13. 🔴 SELF-TRANSFER (А → А) -> 409, эч нерсэ ӨЗГӨРБӨЙТ")
    void selfTransferIsRejected() {
        User a = givenUserWithBalance("t-self@example.com", "1000.0000");

        assertThatThrownBy(() -> transferService.transfer(a.getId(),
                request(accountNumberOf(a), "300.0000", null)))
                .isInstanceOf(SelfTransferException.class)
                .hasMessageContaining("same account");

        // 🔴 БАЛАНС ӨЗГӨРБӨДҮ — «дебитлеп, кредитлеп, кайтарып»
        //    айлап өтүүнүн КЕРЕГИ ЖОК (эсептеги саны туура болуу
        //    менен, журналда эки «изо» жазуу пайда болмокчу).
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");

        // 🧡 ЖУРНАЛДА ЖАҢЫ ЖАЗУУ ЖОК.
        assertThat(ledgerOf(a)).hasSize(1);
    }

    // ==================== 14-16: СУММАНЫН ВАЛИДАЦИЯСЫ ====================

    @Test
    @DisplayName("14. 🔴 Сумма = 0 -> InvalidAmountException (400)")
    void zeroAmountIsRejected() {
        User a = givenUserWithBalance("t-zero-a@example.com", "1000.0000");
        User b = givenUserWithEmptyAccount("t-zero-b@example.com");

        assertThatThrownBy(() -> transferService.transfer(a.getId(),
                request(accountNumberOf(b), "0", null)))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than 0");

        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("15. 🔴 Сумма < 0 -> InvalidAmountException (400)")
    void negativeAmountIsRejected() {
        User a = givenUserWithBalance("t-neg-a@example.com", "1000.0000");
        User b = givenUserWithEmptyAccount("t-neg-b@example.com");

        assertThatThrownBy(() -> transferService.transfer(a.getId(),
                request(accountNumberOf(b), "-100", null)))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than 0");

        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("16. 🔴 4төн көп ондук бөлүк -> 400. ТЕГЕРЕКТЕБЕЙБИЗ")
    void tooManyDecimalsAreRejected() {
        User a = givenUserWithBalance("t-scale-a@example.com", "1000.0000");
        User b = givenUserWithEmptyAccount("t-scale-b@example.com");

        assertThatThrownBy(() -> transferService.transfer(a.getId(),
                request(accountNumberOf(b), "100.12345", null)))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("4 decimal places");

        assertThat(balanceOf(a)).isEqualByComparingTo("1000.0000");
    }

    // ==================== 17+: САКООТТУК ИНВАРИАНТ ====================

    @Test
    @DisplayName("17. 🛡️ ЖАЛПЫ СУММА САКТАЛАТ (20-бөлүм): A + B = 1500 күчүнде")
    void totalSumIsConserved() {
        User a = givenUserWithBalance("t-total-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-total-b@example.com", "500.0000");

        BigDecimal before = balanceOf(a).add(balanceOf(b));
        assertThat(before).isEqualByComparingTo("1500.0000");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "300.0000", null));

        BigDecimal after = balanceOf(a).add(balanceOf(b));
        // 🔴 АКЧА «ТҮЗҮЛБӨЙТ», «ЖОГОЛБОЙТ» — бир эсептен башка
        //    эсепке КАЙТЫП (которуу — бул жаңы акча эмес).
        assertThat(after).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("18. 🧡 БИР transfer = так ЭКИ жазуу (OUT + IN), башка эмес")
    void oneTransferCreatesExactlyTwoRecords() {
        User a = givenUserWithBalance("t-count-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-count-b@example.com", "500.0000");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "300.0000", null));

        // 🧡 «көчүрүү» жазууларын санаудан ЧЫГАРЫП, тек transfer'тики:
        List<Transaction> outRecords = ledgerOf(a).stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER_OUT).toList();
        List<Transaction> inRecords = ledgerOf(b).stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER_IN).toList();

        // 🔴 «14-бөлүм»: бир ийгилик = бир OUT + бир IN.
        assertThat(outRecords).hasSize(1);
        assertThat(inRecords).hasSize(1);
    }

    @Test
    @DisplayName("19. 🧡 Эки transfer' бири-бирине өткөн ирет (маалымат базасындагы ID тартиби)")
    void transfersInBothDirectionsUseTheSameLockOrder() {
        User a = givenUserWithBalance("t-both-a@example.com", "1000.0000");
        User b = givenUserWithBalance("t-both-b@example.com", "500.0000");

        // A -> B, кийин B -> A. 🧡 Экөө тең ийгилик болушу керек:
        // кулопка тартиби БИРДЕЙ (ID ascending) болгондуктан
        // биринин күткөнүнөн чыга албайт.
        transferService.transfer(a.getId(), request(accountNumberOf(b), "300.0000", "A->B"));
        transferService.transfer(b.getId(), request(accountNumberOf(a), "200.0000", "B->A"));

        assertThat(balanceOf(a)).isEqualByComparingTo("900.0000");   // 1000-300+200
        assertThat(balanceOf(b)).isEqualByComparingTo("600.0000");   // 500+300-200
    }

    @Test
    @DisplayName("20. 🛡️ ӨЗ ЭСЕБИНЕ description бербесе -> null (бош «изо» жазуу жок)")
    void missingDescriptionIsStoredAsNull() {
        User a = givenUserWithBalance("t-nodesc-a@example.com", "1000.0000");
        User b = givenUserWithEmptyAccount("t-nodesc-b@example.com");

        transferService.transfer(a.getId(), request(accountNumberOf(b), "100.0000", null));

        assertThat(ledgerOf(a).get(0).getDescription()).isNull();
        assertThat(ledgerOf(b).get(0).getDescription()).isNull();
    }
}
