package com.minibank.dto.transaction;

import java.math.BigDecimal;

/**
 * 🔄 TRANSFER ЖООПУ — ЭКИ эсептин жаңы абалы.
 *
 * 🧡 ЭМНЕ БИР ЖООПТО ЭКИ ЭСЕП?
 *     Себеби: transfer'те «маани» эмес, «иш» көп — акча экөө
 *     жерде өзгөрдү. Клиент «мен канча бердим, доо канча алды»
 *     деп БИР суроо жасайт, биз БИР жооп беребиз. Эки бөлүктү
 *     (эки HTTP жооп) кылсак, «бири ийгилик, бири ката» деген
 *     абал пайда болуп, чыгаруучу «акча кайда?» деп калар эле.
 *
 * 🔴 БУЛ ЖЕРДЕ ЖОК (жоопко чыгбайт):
 *     - password / passwordHash (колдонуучунун сыры)
 *     - колдонуучунун объектиси
 *     - эсептин ИЧКИ idси (эсеп номуру жетишкен — эстен чыгаруучунун
 *       башкалардын эсеп номурун табууга жардам болбошун үчүн
 *       чыгарабыз; эсеп номуру аны таанышы үчүн АЧЫК маалымат)
 *     - колдонуучунун idси
 *
 * @param transactionId    эки жазуунун БИРДИК номуру (transfer idси) —
 *                         эки эсептин жазуусу да ОШО idге тиелед
 * @param fromAccountNumber биздин эсеп номуру
 * @param toAccountNumber   кай эсепке кетти
 * @param amount            БУЛ ОПЕРАЦИЯДА канча которулду (САЛЫМЧЫ)
 * @param fromBalance       биздин эсеп КИЙИН канча болгон
 * @param toBalance         кай эсеп КИЙИН канча болгон
 * @param currency          "RUB" (эки эсеп тең — валюта конвертациясы жок)
 */
public record TransferResponse(
        Long transactionId,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        BigDecimal fromBalance,
        BigDecimal toBalance,
        String currency
) {
}