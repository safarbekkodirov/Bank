package com.minibank.exception;

import java.math.BigDecimal;

/**
 * 409 CONFLICT — эсепте акча ЖЕТПЕЙТ.
 *
 * 🧠 Эмне 409 (Conflict), эмес 400 (Bad Request)?
 *     400 = "СЕНИН СҮРОГҢ ТАП ТУРАТ" (санды жазбаптың, терс жаздың)
 *     409 = "СЕНИН СҮРОГҢ ТУУРА, бирок УЧУНЧУРАН ТАП ТУРАТ" —
 *           эсепте 650.0000 бар, сен 1000.0000 сураптың.
 *     Демек клиент өз кадамын өзгөртө алат: 650.0000 же 100.0000
 *     жазып кайра айтат. Биз "кайта көрүңүз" дебибиз ЭМЕС, анын
 *     ордуна 409 кесеби.
 *
 * 🛡️ МААЛЫМАТ УГУУ (information leak) ЖОК:
 *     Бул катаны АВТОРИЗАЦИЯЛАНГАН колдонуучунун ӨЗ эсеби жөнүндө.
 *     Биз анын калган балансын КАЙТАРБАЙБИЗ — чыгаруучу өзү
 *     балансты "/api/accounts/me" аркылуу канча болсо ошол алат.
 *     Биз айтканбыз — «кайсы суммага жетпеди» деген факт гана.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }

    /**
     * Колдонуучуга көрүнүктүү бүт жазуу.
     * Ким канча алышы мүмкүн экенин да айтып беребиз — анын ӨЗ акчасы.
     */
    public static InsufficientFundsException of(BigDecimal balance, BigDecimal requested) {
        return new InsufficientFundsException(
                "Insufficient funds. Current balance: " + balance
                        + ", requested: " + requested);
    }
}
