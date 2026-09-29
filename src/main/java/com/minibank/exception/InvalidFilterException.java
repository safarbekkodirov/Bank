package com.minibank.exception;

/**
 * 400 BAD REQUEST - history soragynyň parametri nädogry.
 *
 *  NÄMÄ ÜÇIN 400 (400 emes 500)?
 *     Klient "size=101" ýa-da "type=BLABLA" ýazdy - bu KLIENTIŇ
 *     kadasy, bizniň serweriň ýalňyşy däl. 500 diýsek - "server
 *     döwüldi" diýip ýalňyş sanymyýarys, üstede monitorda acal
 *     ýagdaýlar köpelýär.
 *
 *  Bu ýerde 4 ýagdaý gutarýar:
 *     page < 0
 *     size < 1 ýa-da size > 100
 *     type belli däl
 *     from > to  (we minAmount > maxAmount)
 */
public class InvalidFilterException extends RuntimeException {

    public InvalidFilterException(String message) {
        super(message);
    }
}
