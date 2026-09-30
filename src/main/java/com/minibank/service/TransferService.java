package com.minibank.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.transaction.TransferRequest;
import com.minibank.dto.transaction.TransferResponse;
import com.minibank.entity.Account;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.exception.InvalidAmountException;
import com.minibank.exception.InvalidDescriptionException;
import com.minibank.exception.SelfTransferException;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;

/**
 * 🔄 АКЧА КОТУРУУ (TRANSFER) — бир эсептен башка эсепке.
 *
 * 🧠 ЭМНЕ БУЛ MoneyService ИЧИНЕ ЭМЕС?
 *     PHASE 5те биз «AccountService = эсеп, MoneyService = акча»
 *     деп бөлгөнбүз. Transfer — «эки эсеп» менен иштеген операция,
 *     ал жалгыз эсепке тийбейт. Ошондуктан бул ӨЗ КЛАССЫ:
 *     бир файл = бир учуну сообт берет. MoneyService'ди
 *     ӨЗГӨРТПӨЙБҮЗ (анын deposit/withdraw аныктамалары туруктуу).
 *
 * 🔴 УШУЛ ФАЙЛДЫН ЖҮРГҮНҮ — ТАПШЫРМАНЫН 2-БӨЛҮМҮ:
 *     Transfer — эки таамал, бир иш:
 *         1. source балансы КЕМИРЕТ
 *         2. destination балансы КӨБӨТ
 *     «Баланс кетти, кийинкиси келе элек» деген абал — БУЗУК.
 *     Адам 300 жөбөрдү, эсебине 0 келди, кийин «кайда 300?» деп
 *     суроо пайда болот, жообу ЖОК. @Transactional «экөө бир иш»
 *     деп жар берет: экөө ийгилик, же экөөлөң жок.
 *
 * 🧠 @Transactional ЭМНЕ БУЛ ЖЕРДЕ (АЛГАЧКЫ) ТАПШЫРМАДА?
 *     Бул — «ҮСТҮНДӨГҮ» кептөөлөм. Spring прокси методу
 *     ЧАКЫРГАНДА транзакцияны АЧАД, аягында КАЙТАРАТ. Демек
 *     «кооптуу иш» болгон 4 кадам (эки lock, эки баланс, эки
 *     журнал) — БААРЫ БИР ТРАНЗАКЦИЯДА. Ката болсо — БААРЫ
 *     КАЙТЫЛАТ.
 */
@Service
public class TransferService {

    /** 🔒 Сумманын 4төн көп ондук бөлүгү болбош керек (MoneyService менен БИРДЕЙ). */
    private static final int MAX_SCALE = 4;

    /** description колонкасынын максимуму (DB: VARCHAR(500)). */
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * 💸➡️💰 АКЧА КОТУРУУ.
     *
     * 🔴 МААНИЛҮҮ ТАРТИБ (тапшырманын 14-бөлүмү):
     *     1. Валидация — БАЗАГА КАЙСЫ ЖАЗУУДАН БҮРҮН
     *     2. Source эсепти табуу (эси анык, эсеп ЖОК болсо — 404)
     *     3. 🔴 SELF-TRANSFER ТЕКШЕРҮҮСҮ — кулопкадан МААНЫН
     *     4. 🔒 ЭКИ КУЛОПКА — ID КИЧИНЕЛИК ТАРТИБИБЕН
     *     5. «Канаа кеби?» — БАЗАНЫН ЫЙЫК ДАГЫ
     *     6. source − amount, destination + amount
     *     7. TRANSFER_OUT + TRANSFER_IN жазуулары
     *     8. COMMIT (же экөө тең РОЛЛБЭК)
     *
     * @param userId  ТОКЕННЕН келген колдонуучу (URL'ден ЭМЕС!)
     * @param request көпүбү, кемүүбү, кийинки эсеп
     */
    @Transactional
    public TransferResponse transfer(Long userId, TransferRequest request) {

        // (1) 🔴 ВАЛИДАЦИЯ — базага тийгендин КАЙТАРЫН.
        //     Бул метод «көчүрмө» эмес, чыныгы чеги: аны чакырган
        //     (Controller, тест, кийинки чакыруучу) БААРЫ өттөн иретте
        //     «текшерүүлөр кийин» деп өтүнө алышат.
        BigDecimal amount = validateAmount(request.amount());
        String description = validateDescription(request.description());
        String toAccountNumber = request.toAccountNumber();

        // (2) 🔴 SOURCE ЭСЕПНИН ИДСИ — ТОКЕНДЕН. ЭЧ КАЧАН сүрөттөн ЭМЕС.
        //     🧡 БИЗ БҮТ ЭСЕПТИ ЕМЕС, ТЕК ID'НИ АЛАБЫЗ. Себеби
        //     эсепти кулопка САЛДАН ачып окуу — «persistence context'ке»
        //     ЭСКИ баланс менен кирип, кийин кулопкалуу кайра окуу
        //     СВЕЖИ БОЛБОЙ КАЛЫП, акча ЖОГОЛУП КЕТЭТ (чыныгы «lost
        //     update» катасы). ID ЭЧ КАЧАН ӨЗГӨРБӨЙТ — ошондуктан
        //     аны ачык окуу ТАПШЫРМАСЫЗ.
        Long sourceId = accountRepository.findIdByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No bank account found for the current user"));

        // (3) 🔴 DESTINATION ЭСЕПНИН ИДСИ — эсеп номуру боюнча.
        //     БАШКА КОЛДОНУЧУНУН ЭСЕБИ БОЛУШУ МҮМКҮН — бул адегенде
        //     нормалдуу (мысалы: дүкөнүмдөн алып, өз эсебимдеги
        //     ысымыкка салуу).
        Long destinationId = accountRepository.findIdByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Destination account not found: " + toAccountNumber));

        // (4) 🔴🔴 SELF-TRANSFER — КУЛОПКА АЛУУДАН МААНЫН!
        //     🧠 ID'лерге текшерүү — эсеп ЖЫЛЫ (объектге учура
        //     кетпей, «эс а» кэшке да кирбейт).
        //     🧡 «Бир эсепти эки жолу lock кылуу» — DEADLOCK'түн
        //     башындап коюучу ката (тапшырманын 18-бөлүмү). Биз
        //     аны адегенде ТОКТОТУП, жүгүртүүбүздөн мурда «токтот».
        if (sourceId.equals(destinationId)) {
            throw SelfTransferException.of(toAccountNumber);
        }

        // (5) 🔒🔒 ЭКИ КУЛОПКА — ID КИЧИНЕЛИК ТАРТИБИБЕН.
        //     (Бул — PHASE 6нын өзгөк каты. Толук талкуу — кийинки
        //     методтун комментаринде.)
        LockedAccounts locked = lockBothInAscendingIdOrder(sourceId, destinationId, toAccountNumber);
        Account lockedSource = locked.source();
        Account lockedDestination = locked.destination();

        // (6) «Канаа кеби?» — эң АКЫРЫ чеги.
        //     🧡 Салыштыруу compareTo менен (equals ЭМЕС): 1000.0000
        //     жана 1000 — эки чыныгы бирдей, бирок equals айырма
        //     чыгаруучуга 500 кеткендей көрсөтөт.
        if (lockedSource.getBalance().compareTo(amount) < 0) {
            // 🧡 409 -> GlobalExceptionHandler -> ApiError.
            //     БАЛАНС ТЕРС БОЛУП КАЛБАЙТ: биз текшерүүдөн
            //     ӨТПӨЙ САЛУУЧУГА ДА ЖЕТПЕЙБИЗ.
            throw InsufficientFundsException.of(lockedSource.getBalance(), amount);
        }

        // (7) БАЛАНСТАРДЫ ӨЗГӨРТҮҮ. Эсептөө — ЭСЕПТЕ (MoneyService
        //     менен бирдей credit/debit, бизнес-логика жашыруу).
        //     🧡 БУЗУК АБАЛДЫ КИЙИНКУЧУ: эгер биз «биринчи
        //     дебит, кийин кредит» деп жазып, арашына бир нерсе
        //     кошулса (exception, log) — @Transactional БААРЫН
        //     КАЙТАРАТ. Биз ишенбеген жерде ишенбей калбайбыз.
        BigDecimal fromBalance = lockedSource.debit(amount);
        BigDecimal toBalance = lockedDestination.credit(amount);

        // (8) 📖 ЭКИ ЖУРНАЛ ЖАЗУУСУ — бир иш, эки таамал.
        //     🧡 11-бөлүм: WITHDRAW/DEPOSIT ЭМЕС, так TRANSFER_OUT
        //     жана TRANSFER_IN. Себеби бул КАРЖЫ ЭМЕС — бул
        //     КОТУРУУ. Кийин «бул акча кайдан келди?» деп караганда,
        //     чыныгы жооп «башка эсептен» болушу керек.
        //     🧡 Сумма экөөсүндө ОҢ: 300.0000 жана 300.0000.
        //     Белги (минус) түрдүн маанисин берет.
        //     🧡 TRANSFER_OUT'тун idсин САКТАП АЛЫБЫЗ — жоопко көрсөтүү үчүн.
        //     «Identity» үлгүсүндө Hibernate INSERTти дароо жасайт,
        //     демек save() кайтарылган объектте id БОЛУП КАЛАТ.
        Transaction transferOut = transactionRepository.save(new Transaction(
                lockedSource,
                TransactionType.TRANSFER_OUT,
                amount,
                fromBalance,
                description));

        transactionRepository.save(new Transaction(
                lockedDestination,
                TransactionType.TRANSFER_IN,
                amount,
                toBalance,
                description));

        // (9) ЖООП — ЭЧ КАЧАН Entity ЕМЕС. Пароль, колдонуучу,
        //     эсептин ички idси чыга албайт.
        return new TransferResponse(
                transferOut.getId(),        // «бир иш» -> бир id
                lockedSource.getAccountNumber(),
                lockedDestination.getAccountNumber(),
                amount,
                fromBalance,
                toBalance,
                lockedSource.getCurrency());
    }

    /**
     * 🔒🔒 ЭКИ ЭСЕПТИ КУЛОПКА МЕНЕН АЛУУ — ID КИЧИНЕЛИК ТАРТИБИБЕН.
     *
     * 🧠 БҮГҮНГӨ ЧАЛКЫЧЫН КЕЗДЕШТИРЕЛИ (чыныгы ката, «deadlock»):
     *     Бизде A(id=10), B(id=20) бар. Эки суроо ОДНО убакта келди,
     *     КАРАМА-КАРШЫ багытта:
     *
     *       Суроо 1: A → B      Суроо 2: B → A
     *       ─────────────      ─────────────
     *       1. A кулопка алды   1. B кулопка алды
     *       2. B КҮТҮҤ ЖОК     2. A КҮТҮҤ ЖОК
     *              ↓                  ↓
     *       ... бири-бирин КҮТҮП КАЛДЫ ...
     *              ↓                  ↓
     *          💀 DEADLOCK 💀   (PostgreSQL биринчисин өлтүрөт:
     *                              "deadlock detected", SQLSTATE 40P01)
     *
     * 🧠 ЧЕЧИМ — «БИРДЕЙ ТАРТИП» (ordering):
     *     Эгер БАРЫКЫ эсепти АЛДА аларын ичин ИД ӨСҮҮ ТАРТИБИБЕН
     *     алсак, айкылып жашылган абал ЖАСАЛБАЙТ:
     *         A → B : 10 кичине -> 10 биринчи, 20 кийин
     *         B → A : 10 кичине -> 10 биринчи, 20 кийин
     *     🧡 Көрсөткөч: экөө суроо да 10ду АЛДАН, кийин 20ни алат.
     *     Экинчи суроо 10ду күткөн кезде (биринчи бүткөндөн кийин)
     *     ТАЗА окуйт — «corruption» болбосот.
     *     Бул — базаларда 40+ жылдан бери «DEADLOCK АЛДЫН АЛУУЧУ»
     *     деп аталган стандарттуу чоңек: бардык lock'тор бирдей
     *     тартипте болсо, цикл (deadlock) пайда БОЛБОЙТ.
     *
     * 🧡 ЭМНЕ ЧИНДА «ID» БОЮНЧА, «ЭСЕП НОМУРУ» БОЮНЧА ЭМЕС?
     *     Эсеп номуру — көрүнүш үчүн (18 цифра, эс анык тартип
     *     жок). ID — маалымат базасынын ЧЫНАГЫ ички номуру, ал
     *     МОНOТОН (бирге чоңураак эмес, бирок сарпсалгыс жана
     *     өзгөрбөс). Демек аны сорттоого ИШЕЙДИ, эсеп номуруна
     *     ишенбейбиз.
     *
     * 🔴 МЫКТЫҮНДӨҮЧҮ: эгер бир күн эсеп id'сине «жаңы эсеп
     *     кириктирилди» (мисалы, эскилерге 100 кошулду) — ТАРТИБ
     *     БУЗУЛМАЙТ. Себеби биз АБСОЛЮТ id эмес, «эки id'ни
     *     салыштырып, кичисин алып» иштеп жатабыз. Ким канча
     *     болгонун таанышыбыздын КЕРЕГИ ЖОК.
     *
     * 🧠 БИЗ `findByIdForUpdate` деген эле колдонобул — «transaction
     *     дээрлик чүйлөшүп, кийин унутуп» деген кыялдын КАЙТЫЛЫШЫ
     *     байланыштуу, бирок чыныгы PostgreSQL кулопкасы АЛДА да
     *     иштейт. PHASE 5теги бирдей.
     */
    private LockedAccounts lockBothInAscendingIdOrder(Long sourceId, Long destinationId,
                                                      String destinationNumber) {

        // 🧡 «Мен киммин?» деген суроого ЖООП КАЙТАРЫЛАТ — демек,
        //    эгер source.id < destination.id болсо, source БИРИНЧИ.
        //    🧠 compareTo (ЕМЕС < операторун): < жазылса Long объектиси
        //    «ичинен чыгат» (unbox) — иштейт, бирок биз PHASE 5те
        //    үйрөнгөндөй салыштырууну ЧЫНЫГЫ метод менен кылабыз.
        if (sourceId.compareTo(destinationId) < 0) {
            Account first = accountRepository.findByIdForUpdate(sourceId)
                    .orElseThrow(() -> new AccountNotFoundException(
                            "No bank account found for the current user"));
            Account second = accountRepository.findByIdForUpdate(destinationId)
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Destination account not found: " + destinationNumber));
            return new LockedAccounts(first, second);
        }

        // 🧡 Башка учур: destination.id < source.id -> БИРИНЧИ
        //    destination, кийин source. 🧠 Биз «транзакциянын
        //    багытын» ЕМЕС, «кулопка алуу тартибин» чалмаштырып
        //    жатабыз. Бизнес-логика (кимден кимге) ӨЗГӨРБӨЙТ —
        //    биз кийин да source'ту ДЕБИТ, destination'ты КРЕДИТ
        //    кылабыз.
        Account first = accountRepository.findByIdForUpdate(destinationId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Destination account not found: " + destinationNumber));
        Account second = accountRepository.findByIdForUpdate(sourceId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No bank account found for the current user"));
        return new LockedAccounts(second, first);   // 🔴 source БИРИНЧИ ҚАЙТАРЫЛАД
    }

    /**
     * 🧠 Кулопка алган ЭКИ эсеп — «кайсы кайсы» деген таза жооп.
     *     Биз чыныгы бизнес багытын (source/destination) САКТАЙБЫЗ,
     *     антпесе «кичине id = source» деп ойлоп, тескерисинче
     *     жазып, акчаны ТЕСКЕРИ жөнөтөр элек.
     *
     * @param source      кулопкалуу КАЙНАК эсеп (акча ЧЫГАРАТ)
     * @param destination кулопкалуу МАКАТ эсеп (акча АЛАТ)
     */
    private record LockedAccounts(Account source, Account destination) {
    }

    /**
     * Сумманын текшерүүлөрү — MoneyService менен БИРДЕЙ эреже.
     *
     * 🧡 ЭМНЕ КАЙТА ТЕКШЕРҮҮ?
     *     Controller'деги @Digits/@DecimalMin аларды текшерет, бирок
     *     бул метод — ЭЧ КАЧАН алардан өтпөйт. Тест, кийинки
     *     эндпоинт же аралык чакыруучу — булар аларды БИЗМИТИНЕ
     *     айлап келе алышат. Биз Service'ти «ички көчүрү» деп
     *     эсептебиз: кандай кирсе кирсин, эреже БИР.
     */
    private BigDecimal validateAmount(BigDecimal amount) {

        if (amount == null) {
            throw new InvalidAmountException("Amount must not be null");
        }

        // 🔴 0 ЖАНА ТЕРС САН БОЛБОЙТ. ЧЕГИНЕ ЖЕТПЕЙ ТЕКШЕРҮҮ:
        // жаңы эсептү колдонуучу 0 которууга аракет кылса, эсеп
        // төмөндөйт (кымын 2.00) — эсепке уруксат берилбеш.
        if (amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than 0");
        }

        // 🔴 4төн көп ондук бөлүк — 400. ТЕГЕРЕКТЕБЕЙБИЗ: колдонуучу
        // 100.12 жазып, тизим 100.1235 экен айтса — эсеп КАТА.
        if (amount.scale() > MAX_SCALE) {
            throw new InvalidAmountException(
                    "Amount must have at most " + MAX_SCALE + " decimal places");
        }

        return amount;
    }

    /**
     * description — кыйыкки. Жок болсо — null (DB колонкасы NULL
     *     кабылдайт). 500дөн узун болсо — 400.
     *
     * 🧡 «Жок» дегенди чыныгы ЖОК деп санашыбыз керек: эгер биз
     *     МАЖБУР бош салса, колдонуучу «жазылбай, кутул» деп
     *     журналда миллион бош «изо» жазуулар пайда болар эле.
     */
    private String validateDescription(String description) {

        if (description == null) {
            return null;
        }

        // 🧡 Узун «тас» — бош салбайбыз, 400 беребиз. «Сиз жазганды
        //     кыскарттым» деп айтуу — адилетсиз: эртең «эмне үчүн
        //     кыскартылды?» деп карап, колдонуучу өзүнө ишенбейт.
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidDescriptionException(
                    "Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }

        return description;
    }
}
