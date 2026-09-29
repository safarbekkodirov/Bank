package com.minibank.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.minibank.entity.User;

/**
 * User Entity менен PostgreSQL таблицасын (users) байланыштырган класс.
 *
 * Бул ИНТЕРФЕЙС — анын ичинде эч кандай метод жазылбайт, биз Java код жазбайбыз.
 * Анын атын карап, Spring Data өзү SQL жазып берет:
 *
 *   Optional<User> findByEmail(String email);
 *        ↓ автоматтык
 *   SELECT * FROM users WHERE email = ?;
 *
 * Эмне үчүн ушул? Биз SQL БИЗДЕ ЖАЗЫП ЖАШПАЙТ. Биз "эмнени табабыз?"
 * деп сөйлөйбүз — Spring "кайтип табабыз?" деп ойлойт.
 *
 * JpaRepository<User, Long> деген эмне?
 *   User — эмне үлү (Entity), Long — анын негизги ачкычынын типи.
 *   Анын ичинен дароо келет: save(), findById(), findAll(), delete()...
 */
@Repository
public interface UserRepository
        extends JpaRepository<User, Long>,
                JpaSpecificationExecutor<User> {

    /**
     * Email боюнча издөө.
     *
     * Optional дегенди колдонгон себеби: эл ЖОК болсо келерет (эмес), же
     * БАР болсо келедь. Optional — "балким бар, балким жок" дегенди
     * коопсуз жол менен билдирет. (null коюунун көп учураган каталары бар.)
     *
     * @param email — изделген email (кийинки Service кичирейтип берет)
     * @return бар болсо User, жок болсо Optional.empty()
     */
    Optional<User> findByEmail(String email);

    /**
     * Эл барбы? — фака "ооба/жок" керек болгон учун.
     * findByEmail().isPresent() кеттирсе, бирок exists жөнүүнөн тез.
     */
    boolean existsByEmail(String email);

    /*
     * PHASE 8 uchun SpecificationExecutor goşuldy (yokaryda extends).
     * Admin "search" ukaby dinamiki: fullName ýa-da email boýunça.
     * Düzgünli metod (findByEmailContaining) goýsa, iki sany ýazmaly
     * ýa-da boş netije gatyşyk emele gelýär - Specification bolsa
     * BIR ýere ýygnanýar: "şu şerti goş, şu şerti goşma".
     */
}
