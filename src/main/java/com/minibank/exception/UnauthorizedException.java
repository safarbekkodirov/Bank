package com.minibank.exception;

/**
 * 401 Unauthorized — колдонуучу танылбады же токени туура эмес.
 *
 * (Пароль туура эмес болгон учурда да ӨЗГӨЧӨЛҮГҮ жооп — "Invalid email
 *  or password" — эгер "пароль туура эмес" деп айтсак, чыныгы эсеп
 *  бар экенин чыгарып берер эле.)
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
