package com.minibank.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface AccountRepository extends JpaRepository<Account, Long> {

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
}
