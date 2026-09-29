package com.minibank.dto.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HISTORY SORAGNIN FILTERI - barland parametrlar BIR OBYDE.
 *
 *  EMNE BU AYRY KLAS?
 *     Sorag 7 parametirli (page, size, type, from, to, min, max).
 *     Controller-da 7 sany @RequestParam yazmak - okalmak kompl
 *     we her testde 7 setir yazmaly. Obyekt: bir setir, okalyar.
 *
 *  BU NEDE SERVICE GEÇIRILMEDIK?
 *     DTO gaýtadan Service-a geçirilende içki öwrüş ýok.
 *     Ölçeg üçin ösüş Service-de, göçürme ýok.
 *
 * @param page      0-den başlaýan sahypa belgisi (null -> default 0)
 * @param size      sahypa göwrümi (null -> default 20, max 100)
 * @param type      DEPOSIT/WITHDRAW/TRANSFER_IN/TRANSFER_OUT (null = ähli)
 * @param from      bu pursatdan soň (inclusive, null = çäksiz)
 * @param to        bu pursata çenli (inclusive, null = çäksiz)
 * @param minAmount iň az suma (inclusive, null = çäksiz)
 * @param maxAmount iň köp suma (inclusive, null = çäksiz)
 */
public record TransactionFilter(
        Integer page,
        Integer size,
        String type,
        Instant from,
        Instant to,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {

    /** Birinji sahypa. */
    public static final int DEFAULT_PAGE = 0;

    /** Adaty sahypa göwrümi - 20 ýeterlik we RAM üçin howpsuz. */
    public static final int DEFAULT_SIZE = 20;

    /** Iň ýokary sahypa göwrümi. Sebäbi bu ýerde - DOSDDEN GORAG. */
    public static final int MAX_SIZE = 100;

    public TransactionFilter {
        page = (page == null) ? DEFAULT_PAGE : page;
        size = (size == null) ? DEFAULT_SIZE : size;
        type = blankToNull(type);
    }

    /**
     * Bos ýa-da diňe boşluk bolan teksti null edýäris.
     *
     *  SEBÄBI: klient "?type=" (ýöne ýazmadyk) ýa-da "?type=   "
     *     ýazsa - bu "syfyr" hökmünde gelýär. Eger biz ony
     *     TransactionType öwürmäge çalşsaýdy -> 400 (ýalňyş jogap):
     *     klient tötänleýin boş ýazdy, ýalňyş ýok.
     *     Dogry çözgüt: "boş = filter ýok" diýip kabul etmek.
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
