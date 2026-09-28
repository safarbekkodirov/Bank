package com.minibank.service;

import org.springframework.stereotype.Component;

import com.minibank.repository.AccountRepository;

/**
 * ЭСЕП НОМУРУН ТҮЗҮҮЧҮ — жалгыз ошул башка нерсеге жооптуу.
 *
 * 🧠 ФОРМАТ: 18 белги, эки бөлүк
 *     40817810 0000000001
 *     └────┬───┘ └────┬────┘
 *      БАНК КОДУ     ЭСЕП САНЫ
 *      8 белги       10 белги
 *
 *     Мисал: 408178100000000001
 *     Биздин кийинки: 408178100000000002
 *
 * 🛡️ ПАРАЛЛЕЛЛДҮЛҮК КООПСУЗДУК — эң маанилүү бөлүм
 *
 *   ❌ ЖАМЫН ЖОЛ (жөнөкөй эсептөгүч, AtomicLong):
 *      сервер өчсө -> 0 башталат -> эски номурлар КАЙТАЛАНАДЫ
 *      2 сервер болсо -> эки иштеген жай, эки эсептөгүч -> ДУБЛЬ
 *
 *   ❌ ЖАМЫН ЖОЛ (рандом):
 *      18^10 ≈ 3.5 триллион вариант. Чоң, бирок кезимдик туруп,
 *      көп эсептүү чоң банк үчүн кубултуу керек.
 *
 *   ✅ БИЗДИК ЖОЛ: маалымат базасынын эсептөгүчү (SEQUENCE)
 *      - Сервер өчсө да -> эсептөгүч МААЛЫМАТ БАЗАСЫНДА жашат
 *      - 2 сервер болсо да -> экөө тең БИР эсептөгүчтү колдонот
 *      - Параллелди сурооларда -> эч качан дубль бербейт
 *      Демек: retry керек ЭМЕС. Эгер рандом колдонсок, керек болмок.
 */
@Component
public class AccountNumberGenerator {

    /**
     * Банк коду — 8 белги. Чыныгы банкта бул банктын лицензия
     * номуру болот (Россияда БИК деп аталат). Бизде — үлгү.
     */
    private static final int BANK_CODE = 40817810;

    /** Эсеп санынын белгиси. 18 = 8 + 10. */
    private static final int SEQUENCE_DIGITS = 10;

    private final AccountRepository accountRepository;

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Жаңы, уникалдуу эсеп номурун кайтарат.
     *
     * Формат: "%08d%010d" -> жазылганда так 8 + 10 = 18 белги.
     *   1   -> "0000000001"
     *   42  -> "0000000042"
     *   999 -> "0000000999"
     *
     * 🧠 Эмне String.format("%010d", ...), Integer.toString() ЭМЕС?
     *     Integer.toString(1) = "1"     (1 белги — керексиз)
     *     String.format("%010d", 1) = "0000000001"  (10 белги — туура)
     *     Банк эсеп номурунун узундугу АЛДЫНДАН БЕЛГИЛЕНГЕН: 18.
     */
    public String generate() {
        long sequence = accountRepository.nextAccountNumberSequence();
        return BANK_CODE + String.format("%0" + SEQUENCE_DIGITS + "d", sequence);
    }

    /** Сыноолор үчүн: канча белги экенин текшерүү. */
    public static int numberLength() {
        return 8 + SEQUENCE_DIGITS;   // 18
    }
}
