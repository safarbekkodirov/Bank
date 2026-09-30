package com.minibank.dto.account;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Эсептин сырткы көрүнүшү.
 *
 * 🛡️ БУЛ ЖЕРДЕ ЖОК:
 *   - password / passwordHash (колдонуучунун сыры)
 *   - user (чын эле көрүнбөшү керек эмес — колдонуучу өз эсебин
 *     каратат, башкалардын эсебине көз жеткизегиси керек эмес)
 *   - БАШКА колдонуучунун маалыматы
 *
 * 🧠 balance эмне үчүн "сан" катары чыгат, "текст" эмес?
 *   Jackson BigDecimalды анын ТЕКСТИНЕ жазып берет, ошондуктан
 *   0.0000 -> JSON'да "0.0000" болуп чыгат (маани 0, көрүнүш 0.0000).
 *   Банк экранда 0.0000 көрсөтүшү керек — "0" эмес.
 *
 * @param id            маалымат базасынын ички номуру
 * @param accountNumber 18 белгилик эсеп номуру
 * @param currency      "RUB"
 * @param balance       0.0000 форматында
 * @param createdAt     эсеп качан ачылган
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema).
 */
@Schema(
        description = "Банк эсебинин маалыматы. Токендеги колдонуучунун "
                + "өз эсеби. Башка колдонуучунун эсебин алыш үчүн "
                + "эч кандай endpoint ЖОК (IDOR коргоо).",
        example = """
                {
                  "id": 1,
                  "accountNumber": "408178100000000001",
                  "currency": "RUB",
                  "balance": 1000.0000,
                  "createdAt": "2026-01-01T10:05:00Z"
                }"""
)
public record AccountResponse(

        @Schema(description = "Маалымат базасынын ички номуру. "
                + "Клиентке ID менен суроо жіберүү ЭМЕС — «/me» "
                + "эндпоинттери токенден иштейт.", example = "1")
        Long id,

        @Schema(description = "Эсеп номуру — так 18 цифра: банк коду "
                + "(40817810) + 10 орундук эсеп саны. Мисал: "
                + "408178100000000001. Көчүрүү учурунда «кейинки "
                + "эсеп» табыш үчүн колдонулат (transfer).",
                example = "408178100000000001", minLength = 18, maxLength = 18)
        String accountNumber,

        @Schema(description = "Валюта. Азыр бир гана «RUB» "
                + "(конвертация ЖОК).", example = "RUB", allowableValues = {"RUB"})
        String currency,

        @Schema(description = "Учурдагы эсеп балансы. Тег эмес — "
                + "сан (JSON'да «0.0000» көрүнөт). Терс бөлүшө "
                + "МУМКҮН ЭМЕС: базада CHECK (balance >= 0).",
                example = "1000.0000")
        BigDecimal balance,

        @Schema(description = "Эсеп качан ачылган (UTC, ISO-8601).",
                example = "2026-01-01T10:05:00Z", format = "date-time")
        Instant createdAt
) {
}
