package com.minibank.dto.transaction;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 🔄 TRANSFER СОРООСУ — бир эсептен башка эсепке.
 *
 * 🧡 ЭМНЕ БУЛ MoneyOperationRequest ЭМЕС?
 *     Себеби: transfer те «маани» үч талап бар (көпүбү, кемүүбү,
 *     канча) — deposit/withdraw та бир гана. Кошуш кошсо кийин
 *     чабыш кыйын болот. Демек БААРЫНЧА ОКУУЧУ — жаңы, ачык класс.
 *
 * 🧠 ENTITY ≠ DTO:
 *     Биз эч качан `Account` объектин КАБЫЛ АЛБАЙБЫЗ. Клиент
 *     `{"accountId": 5}` жазып, чүйлүп, өз акчасын башка эсепке
 *     сүйлөшүнө (IDOR) аракет кылышы мүмкүн эле.
 *     «Кантип табабыз?» деген сорог — БИЗДИН ИШИБИЗ, эмес.
 *     Демек `toAccountNumber` — ТЕКСТ, чыныгы эсеп объекти эмес.
 *
 * 🔴 БУЛ СОРООДА БУЛ ТАЛАП ЖОК - ЭЧ КАЧАН:
 *     • `fromAccountNumber` / `fromAccountId` / `accountId` / `userId`
 *     Эгер биз мына бейтарап келсек, колдонуучу өзүнөн башка
 *     эсепти «буйрук кылуучу» (көпкөнчүлүк — чыныгы коопсуздук
 *     ката) кылып, БАШКАЛАРДЫН АКЧАСЫН АЛЫП КЕТАЛЫ. Демек биз
 *     аларды КАБЫЛ АЛБАЙТЫЗ: алар болсо, Jackson СИЛКИНЕЙТ
 *     (Spring Boot суроуну таанышбаган тааларды «ойлоп» салмайт)
 *     жана биз өз эсебибизден гана иштейбиз.
 *
 * 🧡 ВАЛИДАЦИЯ (PHASE 5 тин аныктамасын КАЙРА колдонобул):
 *     1. @NotBlank      — toAccountNumber жок болбош керек
 *     2. @Pattern       — эсеп номуру 18 цифра («408178100000000068»)
 *     3. @NotNull       — сумма жок болбош керек
 *     4. @DecimalMin    — сумма 0 дан ЧОҢ
 *     5. @Digits(f=4)   — 4 төн ашык ондук бөлүк БОЛБОШУ керек
 *
 * @param toAccountNumber — КЕЙИНКИ эсеп (18 цифра). «Бир колдонуучу»
 * @param amount          — канча которуу
 * @param description     — кошумча маалымат, кыйыкки
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema). Валидация ӨЗГӨРГӨН ЖОК.
 */
@Schema(
        description = "Акча которуу суроосу. 🔴 БИЗДИН ЭСЕБИМ ТОКЕНДЕН "
                + "АЛЫНАТ — `fromAccountId`, `accountId`, `userId` "
                + "УШУЛ СУРООДА ЖОК жана кабыл да кылынбайт.",
        example = """
                {
                  "toAccountNumber": "408178100000000001",
                  "amount": 250,
                  "description": "Transfer"
                }"""
)
public record TransferRequest(

        @NotBlank(message = "Destination account number must not be blank")
        @Pattern(regexp = "\\d{18}",
                message = "Destination account number must be exactly 18 digits")
        @Schema(description = "Акча КАЙДА көчүрүлөт — кейинки эсептин "
                + "18 орундук номуру (так 18 цифра, мурун/артындагы "
                + "боштуктар тазаланат). Мисал: 408178100000000001. "
                + "Ошол эсеп болбосо → 404.",
                example = "408178100000000001",
                pattern = "^\\d{18}$", minLength = 18, maxLength = 18)
        String toAccountNumber,

        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "0.0", inclusive = false,
                message = "Amount must be greater than 0")
        @Digits(integer = 15, fraction = 4,
                message = "Amount must have at most 4 decimal places")
        @Schema(description = "Көчүрүлөтүн сумма. Шарттар: жок "
                + "болбош керек; 0ден ЧОҢ; ондук бөлүгү 4төн "
                + "ашпаш керек. Эсепте канча бар экенинен КӨП болбош "
                + "керек — антпесе → 409.",
                example = "250", minimum = "0.0001", exclusiveMinimum = true)
        BigDecimal amount,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        @Schema(description = "Кошумча маалымат. КЫЙИККИ. Максимум "
                + "500 белги (DB VARCHAR(500)).",
                example = "Transfer", maxLength = 500)
        String description
) {

    /**
     * 🧠 КОМПАКТ КОНСТРУКТОР — кириш чекчесинде тазалоо.
     *     MoneyOperationRequest менен БИРДЕЙ тартип (PHASE 5):
     *     боштук кеткен «   » — анык эмес, ошондуктан null кылып,
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
