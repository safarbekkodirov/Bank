package com.minibank.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ADMIN gorunusi: esep (PHASE 8).
 *
 * 🧡 EMNE AccountResponse EMES? Ular bir-birine MEWEZILDIR:
 *     AccountResponse = "men kimmim" - MENIN esebim. Ichinde user
 *     YOQ (kollonuwcu basqalaryny gormeli emes).
 *     AdminAccountResponse = "bu kimnin esebi" - basqalardyŋ
 *     esebini ADMIN gorady. Administrator uchun kimdigini
 *     bilmek ZARUR: sony kime tabyshy? Shu yerdan anik bolady.
 *
 * 🟡 N+1 SORAG - BUL YERDE IINKIERI:
 *     Account.user = LAZY. Eger biz N esepti okap, soŋra har
 *     birine userdi ayry oka (getUser().getEmail()) - bu N
 *     goLEK sorag bolady (1 + N). 1000 esep -> 1000 sorag!
 *     INDIKI YERDE: biz user maglumatyny BIR SORAGDA getirep
 *     alarymyz (join fetch). Barlag TransactionServiceTest we
 *     AdminAccountApiTest barlaydi.
 *
 * 🔴 PASSWORD BURADA HECH QAÇAN.
 *
 * @param id           ichki nomur
 * @param accountNumber 18 belgilik esap nomuru
 * @param userId        esap kimge tabyshy (kim)
 * @param userEmail     - kolonuwcu
 * @param userFullName  - aty
 * @param currency      "RUB"
 * @param balance       0.0000 formatynda
 * @param createdAt     kachan esap achylan
 * @param updatedAt     kachan ozgergen
 */
public record AdminAccountResponse(
        Long id,
        String accountNumber,
        Long userId,
        String userEmail,
        String userFullName,
        String currency,
        BigDecimal balance,
        Instant createdAt,
        Instant updatedAt
) {
}
