package com.minibank.dto.transaction;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * АКЧА ОПЕРАЦИЯСЫНЫН СУРООСУ — deposit жана withdraw экөөсүнө бир.
 *
 * 🧡 Эмне эки эндпоинтке БИР DTO?
 *     Суроо «бир сумма + комментарий» — экөөсүнө бирдей. Эки копия
 *     жасалса, бирөөсүндө 4-каттуу текшерүү унутулуп, экинчиси
 *     ачык калмакчы. (Бир калган эреже — бир жерде жазылган, ар дайым
 *     бирдей иштеген эреже.)
 *
 * 🧠 ENTITY ≠ DTO эрежеси:
 *     Биз ЭЧ КАЧАН Account эмес, Transaction эмес кабыл албайбыз.
 *     Клиент "id" жасай алса, башкалардын эсебине өтүп кетмекчи
 *     (IDOR) — эсеп ТОКЕНден табылат.
 *
 * 🧡 ВАЛИДАЦИЯ — ҮЧ КАТМАР:
 *     1. @NotNull        — сумма жок болбош керек
 *     2. @DecimalMin     — сумма 0'дан ЧОҢ болууга тийиш
 *     3. @Digits(f=4)    — 4төн ашык ондук бөлүк БОЛБОШУ керек
 *     Эмне @Digits, эмес @DecimalMax? Себеби: 100.12345 — чоңдук
 *     жагынан ченелмейт, БАРЫП 4-каттуу эмес. Демек чоңдук эмес,
 *     «каттуулук» керек.
 *
 * @param amount      — канча акча. Мисалы 500.0000. Чоңдук жана каттуулук — экөө тексирилет
 * @param description — кошумча маалымат. КЫЙИККИ (optional) → null болушу мүмкүн
 */
public record MoneyOperationRequest(

        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "0.0", inclusive = false,
                message = "Amount must be greater than 0")
        @Digits(integer = 15, fraction = 4,
                message = "Amount must have at most 4 decimal places")
        BigDecimal amount,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description
) {

    /**
     * 🧠 КОМПАКТ КОНСТРУКТОР — кириш чегинде тазалоо.
     *     Регистрациядаги (RegisterRequest) ой жүгүртүүнүн
     *     бирдей көчүрмөсү: боштук кеткен "   " — анык эмес,
     *     ошондуктан null кылып, журналда "бош коробочка" эмес,
     *     чыныгы NULL сакталат.
     */
    public MoneyOperationRequest {
        description = normalizeDescription(description);
    }

    private static String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}