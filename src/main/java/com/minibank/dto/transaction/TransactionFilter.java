package com.minibank.dto.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HISTORY СОРОГУНУН ФИЛЬТРИ - берилген параметрлер БИР ОБЪЕКТТЕ.
 *
 * 🧠 ЭМНЕ БУЛ АЙРЫ КЛАСС?
 *     Сорог 7 параметрлүү (page, size, type, from, to, min, max).
 *     Controller да 7 сан @RequestParam жазуу - окууга кыйын, жана
 *     ар бир тестте 7 сат жазуу керек. Объект: бир сат, окууга ыңгайтуу.
 *
 * 🧡 ЭМНЕ БУЛ SERVICE ГЕ ӨТКӨРҮЛБӨДҮ?
 *     DTO кайра Service ге өткөрүлгөндө ички өзгөртүү жок.
 *     Өлчөм үчүн өсүш Service де, көчүрмө жок.
 *
 * @param page      0 ден баштаган бет белгиси (null -> 0)
 * @param size      бет көлөмү (null -> 20, максимум 100)
 * @param type      DEPOSIT/WITHDRAW/TRANSFER_IN/TRANSFER_OUT (null = баары)
 * @param from      ушул убакыттан кийин (киргизүлүү, null = чектелбеген)
 * @param to        ушул убакытка чейин (киргизүлүү, null = чектелбеген)
 * @param minAmount эң аз сумма (киргизүлүү, null = чектелбеген)
 * @param maxAmount эң көп сумма (киргизүлүү, null = чектелбеген)
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

    /** Биринчи бет. */
    public static final int DEFAULT_PAGE = 0;

    /** Бет көлөмү - 20 жетиштүү жана RAM үчүн кооптуу. */
    public static final int DEFAULT_SIZE = 20;

    /** Эң жогорку бет көлөмү. Себеби бул жерде - ДОСДЕН КОРГОО. */
    public static final int MAX_SIZE = 100;

    public TransactionFilter {
        page = (page == null) ? DEFAULT_PAGE : page;
        size = (size == null) ? DEFAULT_SIZE : size;
        type = blankToNull(type);
    }

    /**
     * Бош же тек боштук болгон текстти null кылабыз.
     *
     * 🧡 СЕБЕБИ: клиент «?type=» (жазбаган) же «?type=   »
     *     жазса - бул «нөл» катарында келет. Эгер биз аны
     *     TransactionType ке өткөргөнгө аракет кылсак -> 400
     *     (жалган жооп): клиент кокустан бош жазды, ката жок.
     *     Туура чечим: «бош = фильтр жок» деп кабылдоо.
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
