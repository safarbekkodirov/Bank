package com.minibank.exception;

/**
 * Email мурунтан катталган — бизнес-катасы.
 *
 * 🧠 Эмне RuntimeException?
 *    Java'да экcепция 2 түргө бөлүнөт:
 *      - Checked (Exception)        -> кол менен "catch кылыш керек" деп чырмайт
 *      - Unchecked (RuntimeException) -> "эгер анык болсо көрсөт" деп жок
 *    Биздин бардык бизнес-каталар RuntimeException: алар "программанын
 *    катасы эмес, колдонуучунун сурагынын натыйжасы".
 *
 *    Бул Springдеги маанилүү бир нерсеге таасир этеди: @Transactional
 *    өзгөчөлүктөрдү көрсө, аларды КАЙТАРАТ (rollback) — эгер алар болбосо
 *    эле (checked болсо) транзакция ЖАРЫЙ САКДА КАЛЫП КЕТЭТ.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("An account with email '" + email + "' is already registered");
    }
}
