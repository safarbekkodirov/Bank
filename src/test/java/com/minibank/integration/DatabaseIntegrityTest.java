package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.minibank.entity.Account;
import com.minibank.entity.Role;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.entity.User;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

/**
 * DATABASE INTEGRITY — PHASE 9 (24-njy bend).
 *
 *  🔴 BU NÄME ÜÇIN AYRY SYNAK SINAGY (äňki sinakdan tapawutly)?
 *
 *  Öňki testleriň hemmesi @Transactional: olaryň ýazgylary test
 *  gutaranda AWTOMATIKI ýaýulýar (rollback). Şonuň üçin olar
 *  özara degmeýärler.
 *
 *  Emma bu testde biz ýok etme SINAGY edýäris: "ýazgysy bolan
 *  esap ýa-da user ýok edilip bilermi?" - bu soraga jogap
 *  Transaction IÇINDE bolup bilmez:
 *
 *      deleteById() -> SELECT -> remove(entity) -> flush()
 *
 *  Transaction içinde flush() ýalňyş ýüze çyksa, Hibernate
 *  sessiýany "zäherli" diýip belläp, ŞOL TESTIň ähli ýazgylaryny
 *  otomatiki rollback edýär - ýagny biziň testiň bir ýarymy
 *  ýitýär we beýleki testleriň ýagdaýyna garyşýar.
 *
 *  Şonuň üçin bu test @Transactional DÄL: ýazgylary çig
 *  commit edilýär, FK syzdyryjy HAKYKY ýüze çykýar, soňra
 *  @AfterEach-de arassa edýäris (36-njy bend - test isolation).
 *
 *  🔴 BUSINESS YOK: bu test ýok etme endpointini barlamaýar
 *  (ol ýok - 28-njy bend). Bu test DIÑE bazanyň soraglama
 *  gözenegi bolan FK düzgünini barlaýar.
 */
@SpringBootTest
class DatabaseIntegrityTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Password123!";

    @Test
    @DisplayName("DB INTEGRITY: ýazgysy bolan esep/user ÝOK EDILMEÝÄR (FK RESTRICT)")
    void databaseIntegrityProtectsLedger() {
        // 🔴 24-njy BEND: users -> accounts -> transactions FK zynjyry
        //    ON DELETE RESTRICT. CASCADE bolsaydym - bir ýazgyny
        //    ýok etsek, esap we user hem ýoýulýardy = akçanyň
        //    barlagy ýitýärdi. Şonuň üçin RESTRICT gerek.
        //
        //    🟡 BU TEST @Transactional DÄL-DIGINE ÄÝETMEÝÄR:
        //    Sebäbi - testiň özi transaction-da ýarym ýagdaý döredip
        //    (ýok etme synagyndan soň rollback edip) tötänleýin
        //    ýalňyşlyk ýok etse, MEÝILNAMANYŇ BEËLEŞMEZLIJI (36)
        //    bozulýardy. Bu ýerde biz ýalňyşlygy IdENTITYGeneration
        //    meýilnamasyz (save() netjesini ulanyp) geçirýäris we
        //    soňra ýok etme - HEÇ BIR ÝALŇYŞLYK ÝOKARYK
        //    ýazylmaýar. Test isolation - transaction ýok.
        //
        //    🧡 BELLIK: bu test ýeke-täzä maglumat döredýär we
        //    öz ýazgyny arassalaýar (aşaky finally).

        // 1) Maglumat taýýarla
        User user = userRepository.save(new User(
                "FK Test", "fk" + System.nanoTime() + "@bank.test",
                passwordEncoder.encode(PASSWORD), Role.USER));
        Account account = accountRepository.save(new Account(
                user, "4081781000" + String.format("%08d",
                        Math.abs(System.nanoTime() % 100000000)),
                Account.RUB, BigDecimal.ZERO.setScale(4)));
        transactionRepository.save(new com.minibank.entity.Transaction(
                account,
                com.minibank.entity.TransactionType.DEPOSIT,
                new BigDecimal("100"),
                new BigDecimal("100"),
                "fk seed"));
        transactionRepository.flush();

        // 2) ID-leri baza alan (obýekten däl - ID hiç haçan üýtgemeýär).
        Long accountId = accountIdOf(user.getId());
        Long userId = user.getId();

        assertThat(transactionRepository.count()).isGreaterThanOrEqualTo(1);

        // 3) Ýazgysy bolan esebi ýok etmäge synag
        assertDeletionBlocked(
                () -> {
                    accountRepository.deleteById(accountId);
                    accountRepository.flush();
                },
                "account");

        // 4) Ýazgysy bolan user-i ýok etmäge synag
        assertDeletionBlocked(
                () -> {
                    userRepository.deleteById(userId);
                    userRepository.flush();
                },
                "user");

    }

    @AfterEach
    void cleanUp() {
        // 🔴 ARASSA: her test soňunda DÜZGÜNLI arassalaýarys.
        //    Tertip hökmany: transactions -> accounts -> users.
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void assertDeletionBlocked(Runnable action, String what) {
        try {
            action.run();
            org.assertj.core.api.Assertions.fail(
                    "BUG: " + what + " with dependent data was deleted - FK broken!");
        } catch (org.springframework.dao.DataIntegrityViolationException expected) {
            // ✅ GARAŞYLANÝAR: bazanyň özi goradýar (RESTRICT)
            assertThat(expected).isNotNull();
        }
    }

    private Long accountIdOf(Long userId) {
        return accountRepository.findByUserId(userId).orElseThrow().getId();
    }

}
