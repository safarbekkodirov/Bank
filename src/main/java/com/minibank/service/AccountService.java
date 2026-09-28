package com.minibank.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.account.AccountResponse;
import com.minibank.entity.Account;
import com.minibank.entity.User;
import com.minibank.exception.AccountAlreadyExistsException;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.UserNotFoundException;
import com.minibank.mapper.AccountMapper;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.UserRepository;

/**
 * ЭСЕП БИЗНЕС-ЛОГИКАСЫ.
 *
 * 🧡 Эң маанилүү чечим: ID БЕЗЕН ӨЗҮН КЕЛЕТ
 *   Биз методго `userId` ПАРАМЕТР КАБЫЛДАБАЙТ — колдонуучунун id'ни
 *   өзү ОЛУП алат. Себеби:
 *     - клиент өзүнөн id жашырууга аракет кылып, чоң сандарды
 *       жазып, башкалардын эсебин ачып көрө алар эле (IDOR)
 *     - биз эч качан «сен киммиң?» деген суроону чалбабыз —
 *       токенди чыпка окуйт (PHASE 3B'деги тартип)
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountMapper accountMapper;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          AccountNumberGenerator accountNumberGenerator,
                          AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountMapper = accountMapper;
    }

    /**
     * ЖАҢЫ ЭСЕП АЧУУ.
     *
     * Баскыч-баскыч:
     *   1. Колдонуучу барбы?
     *   2. Эсеби барбы? (бизнес-эреже: бир колдонуучуга бир эсеп)
     *   3. Номер түзүү (маалымат базасынын эсептөгүчүнөн)
     *   4. currency = RUB, balance = 0.0000
     *   5. Салуу
     *
     * 🛡️ ПАРАЛЛЕЛЛДҮЛҮК КООПСУЗДУК:
     *   2-кадамдын текшерүүсү АЛМАШТЫРУЛБАЙТ (TOCTOU эле).
     *   Чыныгы коргоо — маалымат базасындагы UNIQUE(user_id).
     *   Эгер экөө жыгылышса, DataIntegrityViolationException келет —
     *   биз аны GlobalExceptionHandlerде 409 менен кармап турабыз,
     *   колдонуучу да, маалымат базасы да бузулбайт.
     */
    @Transactional
    public AccountResponse createAccount(Long userId) {

        // (1) Колдонуучу барбы? (ЧЫНГЫ: JWT чыпка бул idни берип келген,
        //     бирок текшерүү көпкөндүрүүн үчүн коюлган)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User with id " + userId + " not found"));

        // (2) Эсеби барбы?
        if (accountRepository.existsByUserId(userId)) {
            throw new AccountAlreadyExistsException(userId);
        }

        // (3) Номер — эсептөгүчтөн. Бул эр жерде эмес, маалымат базасында
        //     кайталанып жаратылат, демек эч качан кайталанбайт.
        String accountNumber = accountNumberGenerator.generate();

        // (4) 🔴 balance = 0.0000.
        //     🧠 Эмне new BigDecimal("0.0000"), эмес BigDecimal.ZERO?
        //     scale коюлбай калса, JSON'да 0 чыгат, "0.0000" эмес.
        //     Биз 4 каттуу тык форматты ТАЛАП КЫЛЫП жазабыз.
        BigDecimal zeroBalance = BigDecimal.ZERO.setScale(Account.BALANCE_SCALE);

        Account account = new Account(user, accountNumber, Account.RUB, zeroBalance);

        // (5) Салуу
        Account saved = accountRepository.save(account);

        // (6) Entity -> DTO (колдонуучусуз, парольсyz)
        return accountMapper.toResponse(saved);
    }

    /**
     * МЕНИН ЭСЕБИМ (Authenticated колдонуучунун).
     *
     * 🔴 Энд `?userId=` ЖОК. Биз id'ни СЫРТТАН АЛМАЙБЫЗ —
     *    токенден алынган id'ни гана колдонобуз.
     */
    @Transactional(readOnly = true)
    public AccountResponse getMyAccount(Long userId) {
        return accountRepository.findByUserId(userId)
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No bank account found for the current user"));
    }
}
