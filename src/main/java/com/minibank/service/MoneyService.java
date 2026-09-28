package com.minibank.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.transaction.MoneyOperationResponse;
import com.minibank.entity.Account;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.exception.InvalidAmountException;
import com.minibank.exception.InvalidDescriptionException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;

/**
 * АКЧА ОПЕРАЦИЯЛАРЫ — DEPOSIT (салуу) жана WITHDRAW (чыгаруу).
 *
 * 🧠 ЭМНЕ БУЛ АВТОМАТТЫК ФАЙЛДА, AccountService ИЧИНДЕ ЭМЕС?
 *     Сайт өскөндө бир класска эле жашоо жоно бөтөт: жаңы
 *     20 методу, аралаш, «кайсысы эсеп ачат, кайсысы акча
 *     чыгарат» деп үстүнөн ойлоп бөгөн кыйын. Ар бир файл
 *     БИР УЧУН СООБОТ БЕРДИ. Ошондуктан: AccountService = ЭСЕП,
 *     MoneyService = АКЧА. Экөө бири-бирин колдонушат, бирок
 *     ажырап турат.
 *
 * 🔴 ЭЧ КАЧАН КЫЗЫЛ ТАЛАБА ТАПШЫРМАДА ЖАЗЫЛГАН:
 *     1. Колдонуучунун ID'ни сүрөттөн (URL) ЭМЕС, ТОКЕННЕН алабыз.
 *     2. Эсептин объектисин сүрөттөн (URL) ЭМЕС, өзү табабыз.
 *     3. Balance-ды «сен жаздың деп ишенбейт» — ӨЗҮ ЭСЕПТЕН
 *        ОКУП, АНДАН КЕЙИН эсептейбиз.
 *
 * 🧠 @Transactional — ЭМНЕ УЧУН?
 *     Балдардын эки таамал чыныгы кылышы керек:
 *         1. эсептин балансы өзгөрөт
 *         2. журналда жазуу пайда болот
 *     «Баланс өзгөрүлдү, журнал жазылбай калды» деген абал — БУЗУК:
 *     адам 300 көрөт, эми «кайда 700 кетти?» деп суроо пайда
 *     болот, эми жооп ЖОК. Демек экөө же экөөлөң ийгилик, же
 *     экөөлөң жок. @Transactional «экөө бир иш» деп жар берет:
 *     экинчиси ката болсо — экинчиси да жок болуп, БИРИНЧИСИ да
 *     КАЙТАРЫЛАТ (rollback).
 */
@Service
public class MoneyService {

    /**
     * 🔒 Сумманын 4төн көп ондук бөлүгү болбош керек.
     *
     * 🧠 БИЗ ТЕГЕРЕКТЕБЕЙБИЗ. Эгер 100.12345 келсе, биз аны
     *     «100.1235» кылып жөнөтпөйбүз: колдонуучу 100.12 жазып,
     *     тизим 100.12 БОЛБОС 100.1235 экенсин айтса — эсеп
     *     ката эсептелген болот. Тапшырмада «тегеректеп жибербе»
     *     деп айтылган. Демек: ачык 400.
     */
    private static final int MAX_SCALE = 4;

    /** description колонкасынын максималдуу узундугу (DB: VARCHAR(500)). */
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public MoneyService(AccountRepository accountRepository,
                        TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * 💰 АКЧА САЛУУ.
     *
     * @param userId      ТОКЕННЕН келген колдонуучу (URL'ден ЭМЕС)
     * @param amount      канча салуу
     * @param description кошумча маалымат, болбосо null
     * @return операциядан кийинки абал
     */
    @Transactional
    public MoneyOperationResponse deposit(Long userId, BigDecimal amount, String description) {
        return performOperation(userId, amount, description, TransactionType.DEPOSIT);
    }

    /**
     * 💸 АКЧА ЧЫГАРУУ.
     *
     * 🔴 Бул жерде эң КАМКОРЛУК орун — «калган акча жетиштиби?».
     *     Ал текшерүүсүз, чыныгы банк системасында «катта» деп
     *     коюлган эмес: эгер ашыкча чыгаруунун коду жазылса,
     *     колдонуучунун акчасын уурдап, эсепке оң жагымдуу
     *     (overdraft) кошо алат. Биз ушул мүмкүнчүлүктү
     *     АЧЫК ТУТУП ЖАБЫП, чоң катадан кейин кечиктирилбейт.
     */
    @Transactional
    public MoneyOperationResponse withdraw(Long userId, BigDecimal amount, String description) {
        return performOperation(userId, amount, description, TransactionType.WITHDRAW);
    }

    /**
     * 🧠 ЭКИ МЕТОДДУН БИРДЕЙ КЫЛГАН ИШИ.
     *
     * 🧡 БИЗ ЭКИ МЕТОД ЖАЗЫП, КОПШОК КОЧУРУБУЗ ДЕБЕЙБИЗ, эмес —
     *     мына бул жерге келдик. Себеби: «салуу» жана «чыгаруу»
     *     БИР ЭЛЕ ИШ — айырмачылыгы (көпүбү +, көпүбү −) жана
     *     «калган жетиштиби?» деген бир гана суроо. Мына ошол
     *     жалгыз жеринги айырмачылыкты `applyBalanceChange` ичине
     *     салдык.
     *
     * 🔴 ТАРТИБ ТАЙЫНДАЛЫШЫ МААНИЛҮҮ:
     *     1. Валидация — БАЗАГА КАЙСЫ ЖАЗУУДАН БҮРҮН
     *     2. Эсепти КУЛОПКАСЫМЕН АЛУУ (PESSIMISTIC_WRITE)
     *     3. «Канаа кеби?» — БАЗАНЫН ЫЙЫК ДАГЫ
     *     4. Балансты өзгөртүү
     *     5. Журналга жазуу
     *     Башка тартипте (ээле, эсеп жокту өтүнүп эле) — эсепти
     *     кулопка менен алып, «колдонуучунун эсеби жок» деп ИШ
     *     БОШОТУП, колдонуучуну бекер кулуп коюу — бул жамандан.
     */
    private MoneyOperationResponse performOperation(Long userId,
                                                    BigDecimal amount,
                                                    String description,
                                                    TransactionType type) {

        // (1) 🔴 БАЗАГА ТИЙГЭНДИН КАЙТАРЫН ВАЛИДАЦИЯ.
        //     Себеби: бул метод — көчүрмө эмес, чыныгы чеги.
        //     Аны чакыргандар (Controller, тест, кийинки Transfer)
        //     БАРЫ өттөн иретте «текшерүүлөр кийин» деп өтүнө алышат.
        BigDecimal checkedAmount = validateAmount(amount);
        String checkedDescription = validateDescription(description);

        // (2) 🔒 ЭСЕПТИ КУЛОПКА МЕНЕН АЛУУ.
        //     «FOR UPDATE» = «мен бул жазууну оңдоп жатам, кимде
        //     башка күтсүн» деп PostgreSQL'ге билдирүү.
        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No bank account found for the current user"));

        // (3-4) БАЛАНСТЫ ӨЗГӨРТҮҮ (арифметика эсепте, эсептин өзүндө)
        BigDecimal balanceAfter = applyBalanceChange(account, checkedAmount, type);

        // (5) 📖 ЖУРНАЛГА ЖАЗУУ.
        //     🧡 КИЙИНКИ СЕЗИМ: `save()` чыныгы INSERTти ДАРОО
        //     жасайт (IDENTITY -> id керек, ошондуктан Hibernate
        //     күтпейт). Демек журналдын idси анын колуна ТАПТЫ.
        //     Биз @Transactional ичинде экен — эгерде мыкейып,
        //     id чыкпай, колго кол тийбей калмакчы эле.
        Transaction transaction = transactionRepository.save(new Transaction(
                account,
                type,
                checkedAmount,
                balanceAfter,     // КЕЙИНКИ абал, ЭСЕПКИСИ эмес
                checkedDescription
        ));

        // (6) ЖООП. 🧡 Entity ЭМЕС, DTO — колдонуучунун объектиси,
        //     пароль жана башкалардын маалыматы бул жерден
        //     ЧЫГАЛЫБЫ МУМКҮН ЭМЕС.
        return new MoneyOperationResponse(
                account.getAccountNumber(),
                account.getCurrency(),
                checkedAmount,
                balanceAfter,
                transaction.getId()
        );
    }

    /**
     * Единственная арифметика: «+» же «−» — жана «канаа кеби?».
     *
     * 🧠 БИЗ КАЙТА ТАКШЕРБЕЙБИЗ деп ойгон элем, бирок кайта
     *     текшерүү БУГА ТАПШЫРМАДА КААРЫ «БИЗДЕЧИ» деп аталган.
     *     Чыныгы себеби: эгер бирок бир күн AccountService'ке
     *     жаңы «списание» (комиссия) методу кошулса, ал ушул
     *     жерди унутуп, өткөн текшерүүсүз өзгөртүү жасап,
     *     эсепти терс кылып коюшун мүмкүн. Бул жердеги
     *     текшерүү — ОШОЛ КАТАГА БАЙЧЫН ТАЗА ПЛАТФОРМА.
     */
    private BigDecimal applyBalanceChange(Account account,
                                          BigDecimal amount,
                                          TransactionType type) {

        if (type == TransactionType.DEPOSIT) {
            // 🧠 БАЛАНС БЕТАРБАЙТ: эсеп + 500.0000 -> 1500.0000
            return account.credit(amount);
        }

        // WITHDRAW: «калган >= суралган?» деген бир гана суроо.
        // 🧡 Салыштырууну compareTo менен жасабыз (equals ЭМЕС!) —
        //     себеби 1000.0000 vs 1000 эки чыныгы бирдей, бирок
        //     equals ар башкасына «бар экенсиң?» деп суроо берип,
        //     БҮГҮН шамалдагы айырма чыгаруучуга 500 кеткендей
        //     көрсөтөт. Сандарды салыштыруунун туура жолу — compareTo.
        if (account.getBalance().compareTo(amount) < 0) {
            // 🧡 Бул 409 -> GlobalExceptionHandler -> ApiError.
            //     БАЛАНС ТЕРС БОЛУП КАЛБАЙТ: биз текшерүүдөн
            //     ӨТПӨЙ САЛУУЧУГА ДА ЖЕТПЕЙБИЗ.
            throw InsufficientFundsException.of(account.getBalance(), amount);
        }

        // 🧠 БАЛАНС БЕТАРБАЙТ: эсеп − 300.0000 -> 1200.0000
        return account.debit(amount);
    }

    /**
     * Сумманын текшерүүлөрү — катмар №1 (Java, колдонуучуга көрүнүктүү).
     *
     * 🧡 ЭМНЕ БУЛ МЕТОД, @NotNull/@DecimalMin/@Digits КАНДАЛЫМЫН
     *     КАЙТА ТЕКШЕРҮҮ?
     *     Себеби: алар Controller катматында иштейт (JSON тура).
     *     Бул метод — ЭЧ КАЧАН алардан өтпөйт. Тест, кийинки
     *     эндпоинт (Transfer) же аралык чакыруучу — булар аларды
     *     БИЗМИТИНЕ айлап келе алышат. Биз Service'ти «Ички
     *     көчүрү» деп эсептебиз: кандай кирсе кирсин, эреже БИР.
     *
     * @throws InvalidAmountException — null, <= 0, 4төн көп каттуулук
     */
    private BigDecimal validateAmount(BigDecimal amount) {

        // (a) Сумма жокпу? (JSON'да "amount" таалуу болбосо)
        if (amount == null) {
            throw new InvalidAmountException("Amount must not be null");
        }

        // (b) 🔴 0 ЖАНА ТЕРС САН БОЛБОЙТ.
        //     🧡 ЧЕГИНЕ ЖЕТПЕЙ ТЕКШЕРОТ: Жаңы эсеп ачылган
        //     колдонуучу дароо 0 чыгарууга аракет кылса, эсеп
        //     төмөндөйт (кымын 2.00) — эсепке уруксат берилбеш.
        if (amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than 0");
        }

        // (c) 🔴 4төн көп ондук бөлүк — 400. ТЕГЕРЕКТЕБЕЙБИЗ.
        //     🧠 Маани: чыныгы акча — 100.1235 коп. Эгер биз
        //     «100.12345 -> 100.1235» кылып жөнөтсөк, эсептин
        //     чыныгы суммасы жалган болду. Тартып салуунун
        //     ордуна — ачык «сен 4 каттуу кыл» деген ката.
        if (amount.scale() > MAX_SCALE) {
            throw new InvalidAmountException(
                    "Amount must have at most " + MAX_SCALE + " decimal places");
        }

        return amount;
    }

    /**
     * description — кыйыкки. Жок болсо — null (DB колонкасы NULL
     * кабылдайт). 500дөн узун болсо — 400.
     *
     * 🧡 БИЗ ТАЗАЛОБУ ЧЫПКАДА ЭЛЕ КЫЛЫП, БУРҮН ТАГЫН
     *     КЫЛБАЙБЫЗ (MoneyOperationRequest'те trim кылынган).
     *     Бирок бул метод — Service'тин көчүрмө, ал Controller'ди
     *     айлап өтүп, тесттен «   » келсе деп текшерсе болот.
     */
    private String validateDescription(String description) {

        if (description == null) {
            // 🔴 МЫНДА КРИТИК: «Эгер жок болсо null болушу МУМКҮН».
            //     Эгер биз МАЖБУР бош салсак, колдонуучу «Cash
            //     deposit» жазбай, кутулап жүрсө, журналда бир
            //     хиллион бош «изо» жазылары. Жок дегенди чыныгы
            //     ЖОК деп санаш керек.
            return null;
        }

        // 🧡 Узун «тас» — бош салбайбыз, 400 беребиз (кечиктирбейт).
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidDescriptionException(
                    "Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }

        return description;
    }
}