package com.minibank.exception;

/**
 * 404 NOT FOUND — колдонуучунун эсеби жок.
 *
 * 🧡 Эмне 404, эмес 400 же 403?
 *     Биз "сен эсебиң жок" деп айтабыз — чыныгы эсеп ЖОК экени анык.
 *     Эгер 403 ("укугуң жок") десек, чыгаруучу "эсеп БАР, бирок меники
 *     эмес" деп ойлойт — демек БАШКА колдонуучунун эсеби бар экенин
 *     билет. Бул маалымат угуу (information leak).
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
