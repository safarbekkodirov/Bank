package com.minibank.security;

import java.time.Instant;

import com.minibank.entity.Role;

/**
 * Кириш чыгыш чыныгы колдонмону (JWT чыпкасы аны экен деп аныктаган).
 *
 * 🧠 Бул эмне? — «Кол китепчеси»
 *     JWT токени — колдонуучунун өзү жашырган, биз ТААШТАЛБАГАН кол китепче.
 *     Китепчеде жазылган: «мен 53-номерлүү Али экем, USERмын».
 *     Биз аны ачып текшеребиз — чыныгы паспортту текшергендей.
 *
 * 🧠 Эмне бир гана ID, эмне толук User ЭМЕС?
 *     ID гана: арыкта тез, эс ана — маалымат базасын өтүнбөйт жана
 *     эс анын өзгөргөн жүгүн (мисалы, колдонуучунун аты өзгөрсө) ЭСКИ ТОКЕНДЕ
 *     эле байын калар эле. Биз ар дайым АЗГИНЧА маалымат кармап турабыз.
 *     Башкача айтканда: токен = аныктын БИР БӨЛҮГҮ, бүт колдонуучу ЭМЕС.
 *
 * @param id    колдонуучунун id (JWT'дин `sub` claim'и)
 * @param email колдонуучунун email (лог жазуулары үчүн)
 * @param role  колдонуучунун роли
 */
public record AuthenticatedUser(Long id, String email, Role role) {

    /** «Сыр» шифтүү формула: эми Jetty pool'уну тешүүбүз керек эмес. */
    public AuthenticatedUser {
        // Java record'да бул бош болсо — иштейт. Классы тешүп чыгабыз деш.
    }

    public static AuthenticatedUser of(Long id, String email, Role role) {
        return new AuthenticatedUser(id, email, role);
    }
}
