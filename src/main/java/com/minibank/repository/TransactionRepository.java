package com.minibank.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.minibank.entity.Transaction;

/**
 * Transaction Entity менен transactions таблицасын байланыштырган класс.
 *
 * 🧠 Бул репозиторий «ЖУРНАЛ» - аны окуу үчүн гана. Биз бул жерде
 *     эсептин балансын ӨЧҮРМӨЙҮЗ (баланс = accounts.balance,
 *     эсептин өзүндө). Журнал - көрсөткөн, эсеп - чыныгы.
 *
 * 🟡 PHASE 5те биз «тарых» эндпоинтин ЖАЗБАЙБЫЗ (тапшырманын
 *     чеги) - бирок репозиторий методун АЛДЫНАН даярдап коёбуз.
 *     Кийин (PHASE 7+) бул жерде көрүнүш керек болгондо,
 *     Controller жазып, ушул методду чакырса болот - логика
 *     кайта жазылбайт.
 *
 * 🟢 PHASE 7: transaction history үчүн JpaSpecificationExecutor.
 *
 * 🧠 ЭМНЕ Specification, ЭМЕС ДҮЗГҮНДҮ МЕТОД?
 *     Дүзгүндү метод (findByAccountId...) гана БИР сорог үчүн жакшы.
 *     Бирок биз 4 сан ÄЛЧИЭК фильтр соройбуз:
 *         type, from/to (убакыт), minAmount/maxAmount.
 *     Ар бирин кошсо - 2^4 = 16 метод. Бул ЖАЛГАН: 16 методун
 *     ар бири өз-өзүнче сыналышы керек, жана 16-инчи ката
 *     чыкканда ал тазаланат. Specification болсо БИР функция:
 *     «ушул шартты кош, ушул шартты кошпо».
 *
 * 🔴 ЭМНЕ ЖОК КЫЛБАБЫЗ? Эркин сорт. Клиент sort=passwordHash
 *     деп аңсай алмайт - Pageable БИЗ КУРАБЫЗ, климент эмес.
 *
 * 🟢 DB БЕТЛЕШҮҮ: Page + Pageable -> LIMIT/OFFSET базада.
 *     Жазуулардын баары Java эстуне тартылбайт (task 27).
 */
@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>,
                JpaSpecificationExecutor<Transaction> {

    /**
     * Эсептин операциялары, ЕҢ ЖАҢЫСЫ БАШТА (түшүндүрмэ «биз көрсөткөн»).
     *
     * 🧡 PHASE 5те бул метод таяр коюлган. Азыр биз аны
     *    тарых үчүн колдонобул; бирок PHASE 7нин негизги жолу -
     *    төмөнкү findAll(Specification, Pageable).
     *
     * 🧡 Индексти КЕРЕК ЭМЕС жазуу керек эмес: V1де
     *    ix_transactions_account_id_created_at мындай эле индекс
     *    бар (account_id, created_at DESC). Биздин иреттөө
     *    аны СЫРТТАНДЫ КОЛДОНОТ.
     */
    List<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * 🧪 ТЕСТ КӨМЕКЧИСИ - жазуунун created_at убагын кол менен коюу.
     *
     * 🟡 ЭМНЕ ГЕРЕК?
     *     Transaction entity @CreatedDate (Spring auditing) колдонот -
     *     ал убакыт ЫЗЫНДАН келет жана Java аркылуу өзгөртүлбөйт.
     *     Бирок «убакыт боюнча фильтр» жана «бир убакта -> id DESC»
     *     тестлери үчүн жазуулардын УБАГЫН өзүбүз тандаш керек.
     *     Ошондуктан тест көмекчиси катарында атайын метод.
     *
     * 🔴 ӨНДҮРҮМЧҮЛҮК КОДКО КЫЛЫМЫЗ:
     *     Бул метод гана тест үчүн. Өндүрүмдө «журнал жазуусунун
     *     убагын өзгөртүү» = жазууну өзгөртүү = append-only
     *     эрежесинин бузулушу.
     *
     * @Modifying + @Transactional -> JPQL UPDATE милдеттүү (SELECT эмес,
     *     себеби биз жазуу керек: JPQL UPDATE гана @Modifying менен
     *     аткарылат).
     */
    @Modifying
    @Query("update Transaction t set t.createdAt = :createdAt where t.id = :id")
    void updateCreatedAt(@Param("id") Long id, @Param("createdAt") java.time.Instant createdAt);
}
