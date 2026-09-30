package com.minibank.dto.transaction;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * БИР ОПЕРАЦИЯНЫ ЖООПУ: transaction history бир элементи.
 *
 * 🧠 ЭМНЕ БУЛ Entity ЭМЕС?
 *     Task 3: «Entity ни түз API response катары кайтарба».
 *     Эгер биз Transaction объектин чыгарсак, анын ичинде `account`
 *     бар — ал Account объектинин ичинде да `user` (passwordHash)
 *     бар. Jackson ичин оюлса, пароль хэши клиентке чыгып кетет.
 *     Ошондуктан биз ДИҢЕ ТАНДАЛГАН тааларды кошобуз.
 *
 * 🔴 БУЛ ЖЕРДЕ ЖОК (жоопко чыкпайт):
 *     - password / passwordHash / User объекти  (коопсуздук)
 *     - эсептин ИЧКИ id си (account.id)       (эсеп номуру жетишеди)
 *     - эсептин балансы (`balance`)  -> эсеп өзүндө, /me де
 *       (БУЛ ЖЕРДЕ БАР: balanceAfter - операциядан КИЙИН эсеп
 *        канча болду, башка жерде. Экөөнү чатырбо.)
 *
 * 🧡 type: TransactionType enum, бирок DTO да String. Клиент үчүн
 *     «DEPOSIT» деген текст керек, Java enum эмес. Type өзгөрсө,
 *     DTO ажырылбайт.
 *
 * @param id           журнал жазуусунун ички номуру
 * @param type         DEPOSIT | WITHDRAW | TRANSFER_IN | TRANSFER_OUT
 * @param amount       БУЛ ОПЕРАЦИЯДА канча (САЛЫМЧЫ, мәнси - Transaction.java)
 * @param balanceAfter операциядан кийин эсеп канча болду
 * @param description  кошумча маалымат, жок болсо null
 * @param createdAt    операциянын убагы (UTC, ISO-8601)
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema).
 */
@Schema(
        description = "Бир операция — transaction history'деги бир "
                + "элемент. 🔴 БУЛ ENTITY ЭМЕС: пароль хэш, колдонуучу "
                + "объекти жана эсептин ички idси КАЙТАЙТЫЛБАЙТ.",
        example = """
                {
                  "id": 1,
                  "type": "DEPOSIT",
                  "amount": 1000.0000,
                  "balanceAfter": 1000.0000,
                  "description": "Initial deposit",
                  "createdAt": "2026-01-01T10:10:00Z"
                }"""
)
public record TransactionResponse(

        @Schema(description = "Журнал жазуусунун ички номуру. Ирет "
                + "белгиси катарында да колдонулат: createdAt бирдей "
                + "болсо, id DESC менен тартип аныкталат.",
                example = "1")
        Long id,

        @Schema(description = "Операциянын түрү: DEPOSIT (салуу), "
                + "WITHDRAW (чыгаруу), TRANSFER_IN (башка эсептен "
                + "келген), TRANSFER_OUT (башка эсепке кеткен).",
                example = "DEPOSIT",
                allowableValues = {"DEPOSIT", "WITHDRAW",
                        "TRANSFER_IN", "TRANSFER_OUT"})
        String type,

        @Schema(description = "БУЛ операцияда канча — САЛЫМЧЫСЫ. "
                + "Withdraw жана TRANSFER_OUT үчүн да ТЕГИН (+): "
                + "чыгарулган 100 деп жазылат, минус белгиси жок.",
                example = "1000.0000")
        BigDecimal amount,

        @Schema(description = "Операциядан КИЙИН эсеп канча болду. "
                + "Үстүнкү баланс ЭМЕС — операция учурундагы абал.",
                example = "1000.0000")
        BigDecimal balanceAfter,

        @Schema(description = "Кошумча маалымат. Жок болсо null.",
                example = "Initial deposit")
        String description,

        @Schema(description = "Операциянын убакты (UTC, ISO-8601). "
                + "Ирет: DESC (эң жаңа биринчи).",
                example = "2026-01-01T10:10:00Z", format = "date-time")
        Instant createdAt
) {
}
