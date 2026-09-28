package com.minibank.exception;

/**
 * 401 — логин учурунда email же пароль туура эмес.
 *
 * 🧠 ЭМНЕ БУЛ АЙЛАП ӨЗГӨЧӮ ЖАЗЫЛБАЙТ?
 *     Катабар, эгер жазсак:
 *       "Бул email табылбай жатат"      -> чыныгы эсеп БАР экенин чыгарат
 *       "Пароль туура эмес"            -> чыныгы эсеп БАР экенин чыгарат
 *     Демек бул "user enumeration" (эсеп табуу) деген колдонуучуну
 *     КАМУУЛСЫЗДЫК МЕТОДУ. Биз эки учурда тең бирдей жауап беребиз:
 *       "Invalid email or password"
 *     Чыгаруучу тажрыйбасы "мына ушул эсеп жок" деп күптөлбөйт.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
