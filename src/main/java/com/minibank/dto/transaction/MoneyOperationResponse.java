package com.minibank.dto.transaction;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

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
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema).
 */
@Schema(
        description = "Операциядан КИЙИНКИ абал. Эмес, эсептин "
                + "толук абалы — бир операциянын натыйжасы гана "
                + "кайтарылат.",
        example = """
                {
                  "accountNumber": "408178100000000001",
                  "currency": "RUB",
                  "amount": 1000.0000,
                  "balance": 1000.0000,
                  "transactionId": 1
                }"""
)
public record MoneyOperationResponse(

        @Schema(description = "Операция жүргөн эсеп номуру (18 цифра).",
                example = "408178100000000001")
        String accountNumber,

        @Schema(description = "Валюта («RUB»).", example = "RUB",
                allowableValues = {"RUB"})
        String currency,

        @Schema(description = "БУЛ операцияда канча салынды/алынды — "
                + "САЛЫМЧЫСЫ, эсептин бүт балансы ЭМЕС. "
                + "Withdraw'та да сумма тегин (+) көрүнөт: "
                + "чыгарулган 100 деп жазылат.",
                example = "1000.0000")
        BigDecimal amount,

        @Schema(description = "Операциядан КИЙИН эсепте канча "
                + "болгон — чыныгы учурдагы баланс.",
                example = "1000.0000")
        BigDecimal balance,

        @Schema(description = "Журналдагы жазуунун ички номуру. "
                + "Тапшырманы аныктап көрсөтүү үчүн.",
                example = "1")
        Long transactionId
) {
}