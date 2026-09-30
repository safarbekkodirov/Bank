package com.minibank.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ADMIN көрүнүшү: эсеп (PHASE 8).
 *
 * 🧡 ЭМНЕ AccountResponse ЭМЕС? Алар бири-бирине КЕЗЕМЕЛДҮҮ:
 *     AccountResponse = «мен киммин» - МЕНИН эсебим. Ичинде user
 *     ЖОК (колдонуучу башкаларды көрмөгөн жөн).
 *     AdminAccountResponse = «бул кимдин эсеби» - башкалардын
 *     эсебин ADMIN көрөт. Administrator үчүн ким экенин
 *     билүү ЗАРЫЛ: ошул акча кимге таандык? Мына бул жерден
 *     анык болот.
 *
 * 🟡 N+1 СОРОГ - БУЛ ЖЕРДЕ ИЙБИ:
 *     Account.user = LAZY. Эгер биз N эсепти окап, кийин
 *     ар бирине колдонуучуну өзүнчө ока (getUser().getEmail()) - бул N
 *     КОШУМЧА сорог болот (1 + N). 1000 эсеп -> 1000 сорог!
 *     БУЛ ЖЕРДЕ: биз колдонуучу маалыматын БИР СОРОГДА алып,
 *     коюк (join fetch). Текшерүүчүлөр бул жерде бар.
 *
 * 🔴 ПАРОЛЬ БУЛ ЖЕРДЕ ДАЙЫН ЖОК.
 *
 * @param id           ички номур
 * @param accountNumber 18 тамгалуу эсеп номуру
 * @param userId        эсеп кимге таандык (ким)
 * @param userEmail     - колдонуучу
 * @param userFullName  - аты
 * @param currency      «RUB»
 * @param balance       0.0000 форматында
 * @param createdAt     качан эсеп ачылган
 * @param updatedAt     качан өзгөргөн
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema). Пароль БУЛ ЖЕРДЕ ЖОК.
 */
@Schema(
        description = "Админ көрүнүшү: эсеп + анын ээси. AccountResponse'ден "
                + "АЙЫРМАКЫ: ээсинин маалыматы бар (kim экенин "
                + "билүү админ үчүн зарыл). 🔴 ПАРОЛЬ ХЭШ ЖОК.",
        example = """
                {
                  "id": 1,
                  "accountNumber": "408178100000000001",
                  "userId": 1,
                  "userEmail": "test@example.com",
                  "userFullName": "Test User",
                  "currency": "RUB",
                  "balance": 1000.0000,
                  "createdAt": "2026-01-01T10:05:00Z",
                  "updatedAt": "2026-01-01T10:05:00Z"
                }"""
)
public record AdminAccountResponse(

        @Schema(description = "Эсептин ички номуру.", example = "1")
        Long id,

        @Schema(description = "Эсеп номуру (18 цифра).",
                example = "408178100000000001")
        String accountNumber,

        @Schema(description = "Эсеп КИМГЕ таандык — ээсинин ички "
                + "номуру. «Ошол эсеп кимдики?» суроосунун жообу.",
                example = "1")
        Long userId,

        @Schema(description = "Ээсинин эл. почтасы.",
                example = "test@example.com", format = "email")
        String userEmail,

        @Schema(description = "Ээсинин толук аты.", example = "Test User")
        String userFullName,

        @Schema(description = "Валюта («RUB»).", example = "RUB",
                allowableValues = {"RUB"})
        String currency,

        @Schema(description = "Учурдагы баланс (0.0000 форматында).",
                example = "1000.0000")
        BigDecimal balance,

        @Schema(description = "Эсеп качан ачылган (UTC, ISO-8601).",
                example = "2026-01-01T10:05:00Z", format = "date-time")
        Instant createdAt,

        @Schema(description = "Акыркы өзгөрүүнүн убакты (UTC, ISO-8601).",
                example = "2026-01-01T10:05:00Z", format = "date-time")
        Instant updatedAt
) {
}
