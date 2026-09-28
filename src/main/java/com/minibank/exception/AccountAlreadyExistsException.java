package com.minibank.exception;

/**
 * 409 CONFLICT — бул колдонуучунун эсеби МУРУНТАН ЭЛЕ бар.
 *
 * 🧡 Себеби эки катмарда бар:
 *   1. Биз Java'да existsByUserId() менен текшеребиз -> түшүнүктүү
 *      ката беребиз, колдонуучуга түшүнүктүү байт.
 *   2. Маалымат базасында UNIQUE(user_id) бар -> эки параллелди суроо
 *      жыгылмай, маалымат базасы КАЙТАРАТ. Биз аны 409 менен кармап,
 *      колдонуучуга жалга сынык эмес жооп беребиз.
 */
public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException(Long userId) {
        super("User with id " + userId + " already has a bank account");
    }
}
