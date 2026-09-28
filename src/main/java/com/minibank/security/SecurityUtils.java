package com.minibank.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.minibank.exception.UnauthorizedException;

/**
 * «Учурдагы колдонмону ким?» — чыныгы көйгөмдүн жоопу.
 *
 * 🧠 ЧОҢ СЫР: колдонуучунун id өтүнүч менен ЭМЕС, ФИЛЬТРДЕН келет.
 *     Мына бул REST'теги коопсуздуктун негизги идеясы:
 *     клиент «мен 53-номерлүү колдонмонуман» деп айта албайт.
 *     Айта алган — чыныгы кол китепче (токен). Ал токен ТАЛДАНЫП,
 *     анын ичиндеги id ыйыктырылып, бизге «53» келип чыгат.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Ушул класстын нуска жасошун тыйктарбыз
    }

    /**
     * Чыныгы колдонуучунун id.
     *
     * @throws UnauthorizedException — эгер колдонуучу жок болсо
     *         (фильтр иштебесе). Биз аны JSON 401 менен кайтарабыз,
     *         эмес катасын "чыгарабыз" — 500 болуп кетпесин.
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("User is not authenticated");
        }

        return user.id();
    }

    /** Токен жок болсо true — чын эмесе false. */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser;
    }
}
