package com.minibank.dto.transaction;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 🔄 TRANSFER ЖООПУ — ЭКИ эсептин жаңы абалы.
 *
 * 🧡 ЭМНЕ БИР ЖООПТО ЭКИ ЭСЕП?
 *     Себеби: transfer те «маани» эмес, «иш» көп — акча экөө
 *     жерде өзгөрдү. Клиент «мен канча бердим, доо канча алды»
 *     деп БИР суроо жасайт, биз БИР жооп беребиз. Эки бөлүктү
 *     (эки HTTP жооп) кылсак, «бири ийгилик, бири ката» деген
 *     абал пайда болуп, чыгаруучу «акча кайда?» деп калар эле.
 *
 * 🔴 БУЛ ЖЕРДЕ ЖОК (жоопко чыкпайт):
 *     - password / passwordHash (колдонуучунун сыры)
 *     - колдонуучунун объектиси
 *     - эсептин ИЧКИ id си (эсеп номуру жетишеди — эстен чыгаруучунун
 *       башкалардын эсеп номурун табууга жардам болбошун үчүн
 *       чыгарабыз; эсеп номуру аны таанышы үчүн АЧЫК маалымат)
 *     - колдонуучунун id си
 *
 * @param transactionId    эки жазуунун БИРДИК номуру (transfer id си) —
 *                         эки эсептин жазуусу да ОШО id ге тиелед
 * @param fromAccountNumber биздин эсеп номуру
 * @param toAccountNumber   кай эсепке кетти
 * @param amount            БУЛ ОПЕРАЦИЯДА канча которулду (САЛЫМЧЫ)
 * @param fromBalance       биздин эсеп КИЙИН канча болгон
 * @param toBalance         кай эсеп КИЙИН канча болгон
 * @param currency          «RUB» (эки эсеп тең — валюта конвертациясы жок)
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema).
 */
@Schema(
        description = "Которуунун натыйжаси — ЭКИ эсептин жаңы абалы "
                + "БИР жоопто. Эки бөлүктү кылсак «бири ийгилик, "
                + "бири ката» деген абал пайда болор эле.",
        example = """
                {
                  "transactionId": 3,
                  "fromAccountNumber": "408178100000000001",
                  "toAccountNumber": "408178100000000002",
                  "amount": 250.0000,
                  "fromBalance": 750.0000,
                  "toBalance": 250.0000,
                  "currency": "RUB"
                }"""
)
public record TransferResponse(

        @Schema(description = "ЭКИ журнал жазуусунун (TRANSFER_OUT жана "
                + "TRANSFER_IN) БИРДИК номуру — экөө тең. Ошондуктан "
                + "тарыхта бир иш эки жазуу болуп көрүнөт.",
                example = "3")
        Long transactionId,

        @Schema(description = "БИЗДИН эсеп номуру (кайнак) — токенден "
                + "алынган эсеп.", example = "408178100000000001")
        String fromAccountNumber,

        @Schema(description = "КЕЙИНКИ эсеп номуру — суроодо "
                + "көрсөткөн.", example = "408178100000000002")
        String toAccountNumber,

        @Schema(description = "БУЛ операцияда канча которулду — "
                + "САЛЫМЧЫСЫ.", example = "250.0000")
        BigDecimal amount,

        @Schema(description = "Кайнак эсеп КИЙИН канча болгон.",
                example = "750.0000")
        BigDecimal fromBalance,

        @Schema(description = "Кейинки эсеп КИЙИН канча болгон.",
                example = "250.0000")
        BigDecimal toBalance,

        @Schema(description = "Валюта («RUB»). Эки эсеп тең — "
                + "конвертация жок.", example = "RUB", allowableValues = {"RUB"})
        String currency
) {
}
