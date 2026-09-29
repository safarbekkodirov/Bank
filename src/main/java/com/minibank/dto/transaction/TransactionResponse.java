package com.minibank.dto.transaction;

import java.math.BigDecimal;
import java.time.Instant;
/**
 * BIR OPERACIYANY JOOPU: transaction history bir elementi.
 *
 * Entity DENILMEZ: task 3 - "Entity ni tuz API response kataru gaytarba".
 * Eger biz Transaction obegini cykarsak, onun icinde `account` bar,
 * ol Account obeginin icinde-de `user` (passwordHash) bar. Jackson
 * icini oysa, parol hashi kliente cykyp baryn ya. Seyle biz dine
 * SAYLANAN meydanlary gosyarys.
 *
 * 🔴 BU YERDE YOK (joopa cykmaýar):
 *     - password / passwordHash / User obegi  (howpsuzlyk)
 *     - esabyň IÇKI id si (account.id)        (esap nomu ýeterlik)
 *     - esabyň balansy (`balance`)  -> esap özünde, /me de
 *       (BU YERDE BAR: balanceAfter - operaciýadan SON esap näçe bolupdy,
 *        beýleki orunda. Ikisini garyşdyrma.)
 *
 * 🧡 type: TransactionType enum, emma DTO-da String. Klient ucin
 *     "DEPOSIT" diyen tekst gerek, Java enum del. Type düzedilende,
 *     DTO aýrylmaz.
 *
 * @param id           zurnal ýazgynyň içki nomeri
 * @param type         DEPOSIT | WITHDRAW | TRANSFER_IN | TRANSFER_OUT
 * @param amount       BU OPERASIYADA näçe (SALYMÇY, manysy - Transaction.java)
 * @param balanceAfter operaciýadan soň esap näçe bolupdy
 * @param description  goşmaça maglumat, ýoksa null
 * @param createdAt    operaciýanyň wagty (UTC, ISO-8601)
 */
public record TransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt
) {
}
