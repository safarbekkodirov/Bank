package com.minibank.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.minibank.entity.Account;

/**
 * Account Entity менен accounts таблицасын байланыштырган класс.
 *
 * 🧠 Метод аттарынын КАДАРЫ маани берет — Spring Data аны окуйт:
 *     findByAccountNumber  -> WHERE account_number = ?
 *     findByUserId         -> JOIN users ... WHERE users.id = ?
 *     existsByUserId       -> бар экенин текшерүү
 *     countByUserId        -> саноо
 *   Сен "эмнени табабыз?" деп сөйлөйсөң — SQL САРТТАН ЖАЗЫЛБАЙТ.
 */
@Repository
public interface AccountRepository
        extends JpaRepository<Account, Long>,
                JpaSpecificationExecutor<Account> {

    /** Эсеп номуру боюнча (банкоматтан издөө сыяктуу). */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Колдонмонунун эсеби.
     *
     * 🧠 `User` — ОТНОШЕНИЕ (ManyToOne). Ошондуктан биз жазып
     *   жүргөнү `findByUserId` деген сөз чыныгында "users.id деген
     *   талаа аркылуу" деп окулуп жатат. Бул иштейт.
     */
    Optional<Account> findByUserId(Long userId);

    /** Ушул колдонуучунун эсеби барбы? (жаңы эсеп ачуудан мурун) */
    boolean existsByUserId(Long userId);

    /**
     * 🔢 ЭСЕП НОМУРУНУН САНЫН АЛУУ — маалымат базасынын эсептөгүчүнөн.
     *
     * 🧡 БУЛ nativeQuery. Себеби: `nextval('...')` PostgreSQL функциясы,
     *    JPA аны «таалардын» бир бөлүгү катарын түшүнбейт — бизге
     *    PostgreSQL'дин өз функциясын колдонош керек.
     *
     * 🛡️ ПАРАЛЛЕЛЛДҮЛҮК КООПСУЗДУК:
     *    PostgreSQL эсептөгүчү бир эле санды эки суроого
     *    КАЧЫП БЕРБЕЙТ — сервер ичинде блок менен коргонот.
     *    Демек эки адам бир убакта эсеп ачса, эсеп номурлары БИРДЕЙ БОЛБАЙТ.
     *    Бизге retry керек ЭМЕС.
     */
    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    Long nextAccountNumberSequence();

    /**
     * 🔒 КОЛДОНУУЧУНУН ЭСЕБИН «КУЛОПКА» МЕНЕН АЛУУ (PESSIMISTIC WRITE).
     *
     * 🧠 БУЛ — PHASE 5тин ЭҢ МААНИЛҮҮ ТЕКШЕРҮҮСҮ. Окуу үчүн эмес,
     *    ЖАЗУУ ҮЧҮН окуйбуз.
     *
     *    Проблема (эки чыныгы суроо):
     *        баланс = 1000, эки адам ОДНО убакта withdraw 700 сурашты
     *        1-адам окуду: 1000. 1000 - 700 = 300 -> жазды
     *        2-адам да ОШОЛ убакта окуду: 1000. 1000 - 700 = 300 -> жазды
     *        Жыйынтык: эки ийгилик, бирок баланс = 300 (эмес!)
     *        Ал эми экинчи күткөнүбүз: 1000-700=300, кийин 700 калбайт ->
     *        409. Демек БИР ийгилик + БИР 409.
     *
     *    🧠 Чечүүсү кандай?
     *        `@Lock(PESSIMISTIC_WRITE)` -> Hibernate SQL'ге
     *        `FOR UPDATE` кошот. PostgreSQL'де бул ЖАЗУУЧУ
     *        КУЛОПКА: экинчи суроо БИРИНЧИСИ БҮТКӨНЧӨ ТОКТОЙТ
     *        (кечигип). Бүткөндөн кийин АЛ ТАЗА ОКУЙТ — 300 көрөт,
     *        демек 700 > 300 -> InsufficientFundsException.
     *        Демек биз эки 300-тү жазпайбыз: бир 300, бир 409.
     *
     *    🧠 Пессимист деген эмне? («ишенимсиз»)
     *        Бирок биз «мен чыныгы эсепти карайм, башка кимде
     *        өзгөрүш жок» деп айтабыз — ДАРСТЫК, кийинкиге
     *        колдонбоочу. Бул КООПСУЗДУК, эмес ишеним.
     *
     *    🛡️ ТОМУНКУ КАТМАРЛАР (бирөө иштебесе, башкасы сактайт):
     *        1. Бул кулопка (FOR UPDATE)
     *        2. Java'дагы «калган >= суралган» текшерүүсү
     *        3. DB: CHECK (balance >= 0)  — V1__init.sql
     *        Үчөө биргеле «эсеп һеч качан терс болбошун» кепитдейт.
     *
     * 🧠 ЭМНЕ @Query ЖАЗЫЛДУ, АТТЫ ГАНА ТАЯТАЛБЫ?
     *     Себеби БУЛ — чыныгы ката (биринчи аракет кылганда):
     *     Spring Data методдун АТЫН ОКУП, бүтүүн «property жолу»
     *     деп карайт. Демек `ForUpdate` деген сөзү «Account.user.id
     *     ForUpdate» деп ОКУЛУП, «forUpdate» касиети ЖОК» деп
     *     ыйлап, колдонмону ИШТЕТПЕЙ ТОКТОТТУ:
     *         No property 'forUpdate' found for type 'Long'
     *     Демек @Lock ЖАЛГЫЗ өзүнчө жетишсиз — анын калдына МЫКТЫҮ
     *     JPQL жазуу керек. @Lock «FOR UPDATE» кошоёт, @Query
     *     «КАЙСИ ЖАЗУУ» деп айтат. БИРГЕ — толук суроо.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.user.id = :userId")
    Optional<Account> findByUserIdForUpdate(@Param("userId") Long userId);

    /**
    /**
     * 🔒 ЭСЕПТИ ИДСИ БОЮНЧА «КУЛОПКА» МЕНЕН АЛУУ (PESSIMISTIC WRITE).
     *
     * 🧠 БУЛ PHASE 6 (TRANSFER) ҮЧҮН КЕРЕК. Себеби transfer бир
     *    иште ЭКИ эсеп ӨЗГӨРӨТ (source −, destination +). Демек экөөсүн
     *    тең жазуу кулопкасынын астына алыш керек.
     *
     * 🧠 ЭМНЕ `findById` ЭМЕС, `@Query` КАЖАРЫ?
     *     Spring Data «forUpdate» деген сөзү «Account.forUpdate»
     *     деп окуйт -> «мындай касиети жок» деп ыйлап, өзү иштебей
     *     калат (PHASE 5те ушул ката кеткен). Демек @Lock «FOR UPDATE»
     *     кошоёт, @Query «КАЙСЫ ЖАЗУУ» деп айтат. БИРГЕ — толук суроо.
     *
     * 🛡️ КУЧТҮҮ ЖАГДАЙ: башка колдонуучунун эсебине кирип, аны
     *     «жазып чыгаруу» (transaction кошкон) эмес — «окуу» үчүн
     *     гана lock алабыз. Демек чыгаруучу чүйлүш кийин
     *     transaction'ды кайра-кайра чыгарып, эсепти ТАЗА ТУТПАЙ,
     *     бирок «кайтарып берүү» аракетин көрсө, биз аны
     *     САНАБАЙБЫЗ. Биз санаган жана жазган — өз эсебибиз.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    /**
     * 🆕 PHASE 6: колдонуучунун эсебинин ИЧКИ НОМУРУ (эсепсиз окуу).
     *
     * 🧠 ЭМНЕ БҮТ ЭСЕПТИ ЭМЕС, АНДА ТЕК ID НИ АЛЫШ КЕРЕК?
     *     Себеби — ЭҮЧҮНЧҮ ОКУУ КАТАСЫ (чыныгы «lost update»):
     *         Эгер биз эсепти АЧЫК (кулопкасыз) суроо менен
     *         окууп, аны колдонуучунун «ассоциация кэшине»
     *         (persistence context) САЛСАК — ал жерде баланс
     *         ЭСКИ күйдө турат. Кийин `FOR UPDATE` кулопка менен
     *         кайта окуганда, эсеп ОШО эски абалын колдонуп
     *         кала берет. Натыйжа: биз 1000 окуп, 50 кеткен
     *         дейт, эгер башка бирөө дагы 50 кеткен болсо —
     *         ЖАЗЫП, КАЙРА 950 ЖАЗАБЫЗ. Акча ЖОГОЛУП КАЛАД.
     *         (Биздин тест бул ктаны 20 желе менен «устап» чыкты:
     *         20 жазуу бар, бирок баланс 100 га гана жылды.)
     *
     * 🧡 ЧЕЧИМ: кулопка АЛДАН биз эсептин ӨЗҮН ОКУБАЙБЫЗ —
     *     ТЕК АНЫН ИДСИН алабыз. ID ЭЧ КАЧАН ӨЗГӨРБӨЙТ, ошондуктан
     *     «эски» болуп калуу мүмкүн эмес. ID билгенден кийин,
     *     эсепти ТАЗА кулопка менен (`findByIdForUpdate`) алып,
     *     балансы АР ДАЙЫМ СВЕЖИЙ болот.
     *
     * 🧠 `@Query` «select a.id» — JPQL дэги «бөгүт» (projection):
     *     эсептин объектиси ЖАРАТЫЛБАЙТ, Java га узун сан (Long)
     *     гана кетет. Демек эс а «эски» абал кэшке кирбейт.
     */
    @Query("select a.id from Account a where a.user.id = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);

    /** 🆕 PHASE 6: эсеп номуру боюнча ИЧКИ НОМУРУ (эсепсиз окуу). */
    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    /*
     * ==========================================================================
     *  PHASE 8 - ADMIN: эсеп тизбеси + колдонуучу маалыматы БИР СОРОГДА
     * ==========================================================================
     *
     *  🟡 БУ НЭМЕ ҮЧҮН? (N+1 маселеси - эң маанилүү оптимизация)
     *
     *     Account.user = @ManyToOne(fetch = LAZY). Эгер биз гана
     *     findAll(pageable) деп эсептерди алса, жана кийин мапперде
     *     ар бир эсеп үчүн account.getUser().getEmail() чакырсак,
     *     Hibernate ар бирине АЙРЫ сорог жөнөтөт:
     *
     *         SELECT * FROM accounts LIMIT 20     -- 1 сорог
     *         SELECT * FROM users WHERE id=1      -- 2-чи сорог
     *         SELECT * FROM users WHERE id=2      -- 3-чү сорог
     *         ... (20 эсеп = 20 сорог)
     *
     *     Бул «N+1» маселеси: 1 + N сорог. 1000 эсеп болсо -
     *     1001 сорог! Жеңил компьютерде бир секундка чыгып кетет.
     *
     *  ✅ ЧЕЧИМ: JOIN FETCH. «эсеп менен бирге анын ээсин
     *     да БИР сорогда алып кел» деп жазабыз:
     *
     *         SELECT a.*, u.* FROM accounts a
     *         JOIN users u ON u.id = a.user_id
     *         ORDER BY ... LIMIT 20 OFFSET 0
     *
     *     Натыйжа: 1 сорог - 20 эсеп, 20 колдонуучу менен.
     *     Бул «LEFT JOIN FETCH» - бирок INNER JOIN сыяктуу үчүн жетиштүү.
     *
     *  🧡 Pageable менен эмне үчүн эстетүү келет?
     *     PostgreSQL эсептөө үчүн бардык жазууларды чогултат,
     *     кийин алардын 20 син кесип алат - демек JOIN FETCH
     *     текширилген көп жазуу алып келет. Бул 20 жеңил
     *     эсептөө. Бирок көңүл бургула: эгер «pageable» болбосо,
     *     бул эстетүү ЖОК.
     *
     *  @param pageable ADMIN тизме үчүн бет + createdAt DESC, id DESC
     */
    @Query(value = "select a from Account a join fetch a.user",
            countQuery = "select count(a) from Account a")
    Page<Account> findAllWithUser(Pageable pageable);

    /** PHASE 8: айрым эсеп + ээси (admin /accounts/{id} үчүн). */
    @Query("select a from Account a join fetch a.user where a.id = :id")
    Optional<Account> findByIdWithUser(@Param("id") Long id);
}
