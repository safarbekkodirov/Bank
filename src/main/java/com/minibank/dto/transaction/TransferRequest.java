package com.minibank.dto.transaction;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 🔄 TRANSFER СУРООСУ — бир эсептен башка эсепке.
 *
 * 🧡 ЭМНЕ БУЛ MoneyOperationRequest ЭМЕС?
 *     Себеби: transfer'те «маани» үч таалар (көпүбү, кемүүбү,
 *     канча) — deposit/withdraw'та бир гана. Кошуш кошсо кийин
 *     чалбаш кыйын болот. Демек БОТУНЧА ОКУУЧУ — жаңы, ачык класс.
 *
 * 🧠 ENTITY ≠ DTO:
 *     Биз эч качан `Account` объектисин КАБЫЛ АЛБАЙБЫЗ. Клиент
 *     `{"accountId": 5}` жазып, чүйлүп, өзүнүн акчасын башка
 *     эсепке сүйлөшүн (IDOR) үчүн колго ала алмакчы эле.
 *     «Кантип табабыз?» деген суроо — БИЗДИН ИШИМЕЗ, чыгаруучунун
 *     эмес. Демек `toAccountNumber` — ТЕКСТ, чыныгы эсеп объектиси
 *     эмес.
 *
 * 🔴 СУРООДО БУЛ ТААЛАА ЖОК — ЭЧ КАЧАН:
 *     • `fromAccountNumber` / `fromAccountId` / `accountId` / `userId`
 *     Эгер биз мына бейтарап келсек, колдонуучу өзүнөн башка
 *     эсепти «буйрук кылуучу» (көпкөнчүлүк — чыныгы коопсуздук
 *     ката) кылып, БАШКАЛАРДЫН АКЧАСЫН АЛЫП КЕТАЛЫ. Демек биз
 *     аларды КАБЫЛ АЛБАЙТЫЗ: алар болсо, Jackson СИЛКИМЕЙТ
 *     (Spring Boot өтүнкүнү таанышбаган тааларды «ойлоп» салмайт)
 *     жана биз өз эсебибизден гана иштейбиз.
 *
 * 🧡 ВАЛИДАЦИЯ (PHASE 5тин аныктамасын КАЙРА колдонобул):
 *     1. @NotBlank      — toAccountNumber жок болбош керек
 *     2. @Pattern       — эсеп номуру 18 цифра («408178100000000068»)
 *     3. @NotNull       — сумма жок болбош керек
 *     4. @DecimalMin    — сумма 0'дан ЧОҢ
 *     5. @Digits(f=4)   — 4төн ашык ондук бөлүк БОЛБОШУ керек
 *
 * @param toAccountNumber — КИЙИНКІ эсеп (18 цифра). «Бир колдонуучу»
 * @param amount          — канча которуу
 * @param description     — кошумча маалымат, кыйыкки
 */
public record TransferRequest(

        @NotBlank(message = "Destination account number must not be blank")
        @Pattern(regexp = "\\d{18}",
                message = "Destination account number must be exactly 18 digits")
        String toAccountNumber,

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
     *     MoneyOperationRequest менен БИРДЕЙ тартип (PHASE 5):
     *     боштук кеткен "   " — анык эмес, ошондуктан null кылып,
     *     журналда «бош коробочка» эмес, чыныгы NULL сакталат.
     *
     * 🧡 Эсеп номурун да тазалайбыз: клиент кокус «  408...068  »
     *     жазып коюшту мүмкүн. Боштук кеткен номур БАЗАДА ТАБЫЛБАЙТ
     *     -> 404, ал эми колдонуучу «мен туура жаздым го» деп
     *     карап кала берет. Биз журап, кийин табыла тургандай
     *     кылып беребиз.
     */
    public TransferRequest {
        toAccountNumber = normalizeTo(toAccountNumber);
        description = normalizeDescription(description);
    }

    private static String normalizeTo(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}