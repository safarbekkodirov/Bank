package com.minibank.entity;

/**
 * Колдонмонунун роли.
 *
 * Энди Entity'де @Enumerated(EnumType.STRING) менен колдонулат.
 *
 * Эмне үчүн STRING, эмес ORDINAL?
 *
 *   @Enumerated(EnumType.ORDINAL)  -> базада САН сакталат: 0, 1, 2 ...
 *   @Enumerated(EnumType.STRING)   -> базада ТЕКСТ сакталат: 'USER', 'ADMIN'
 *
 * Мисал (бул чыныгы ката):
 *   Азыр:  enum Role { USER, ADMIN }        -> USER = 0, ADMIN = 1
 *   Кийин: enum Role { USER, ADMIN, MANAGER } -> ADMIN = 2 болуп калды!
 *   Жыйынтык: базада 1 = ADMIN деп жазылган колдонмонулар
 *              ЭМИ "MANAGER" болуп калабы -> кооптук кириш!
 *
 * ORDINAL базага ТАНАБАЙТ: эгер enum тартибин өзгөртсөң, эс ана — бузук
 * маалымат. STRING болсо, эс ана эстен чыгат. Ошондуктан БАШЫНАН STRING.
 */
public enum Role {

    /** Кадимки колдонмону: эсеп ачат, акча салат. */
    USER,

    /** Администратор: баарын көрөт. (PHASE 7) */
    ADMIN
}
