package com.minibank.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
