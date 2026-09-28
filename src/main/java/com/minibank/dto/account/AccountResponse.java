package com.minibank.dto.account;

import java.math.BigDecimal;
import java.time.Instant;

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
 */
public record AccountResponse(
        Long id,
        String accountNumber,
        String currency,
        BigDecimal balance,
        Instant createdAt
) {
}
