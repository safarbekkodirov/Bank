package com.minibank.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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
public interface UserRepository extends JpaRepository<User, Long> {

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
}
