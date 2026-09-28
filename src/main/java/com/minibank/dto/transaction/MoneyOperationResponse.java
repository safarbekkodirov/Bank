package com.minibank.dto.transaction;

import java.math.BigDecimal;

/**
 * Deposit / Withdraw ЖООПУ — операциядан КИЙИНКИ абал.
 *
 * 🧡 Бул эмес, эсептин толук абалы. Клиент «менин эсебимдеги бардык
 *     операциялар» деп сурап, чоң жоопту алышы мүмкүн эле. Биз
 *     бир операциянын натыйжасын гана КАЙТАРАМЫЗ.
 *
 * 🔴 БУЛ ЖЕРДЕ ЖОК (жоопко чыгбайт):
 *     - password / passwordHash (колдонуучунун сыры)
 *     - колдонуучунун объектиси
 *     - эсептин ички idси (бизге керек эмес — эсеп номуру жетишкен)
 *
 * @param accountNumber эсеп номуру (18 белги) — «кай эсепке» деген суроого жооп
 * @param currency      "RUB"
 * @param amount        БУЛ ОПЕРАЦИЯДА канча кошулду/алынды (САЛЫМЧЫ — доң ЭМЕС)
 * @param balance       операциядан КИЙИН эсепте канча болгон
 * @param transactionId журналдағы жазуунун номуру (кийин «тапшырма» аныктап, көрсөтүү үчүн)
 */
public record MoneyOperationResponse(
        String accountNumber,
        String currency,
        BigDecimal amount,
        BigDecimal balance,
        Long transactionId
) {
}