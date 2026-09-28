package com.minibank.dto.auth;

import com.minibank.dto.user.UserResponse;

/**
 * ЛОГИН ЖООБУ — токен + ким киргени.
 *
 * 🧡 Энд ПАРОЛЬ, ПАРОЛЬ ХЭШИ ЖАШАШЫ МҮМКҮН ЭМЕС.
 *    Биз токендин ичине да, бул жооптун ичине да ПАРОЛЬ САЛМАЙБЫЗ.
 *
 * @param accessToken «Кол китепче» — кийинки сурооларда тапшырылади
 * @param tokenType   «Bearer» деп жазылат, аны HTTP header'де колдонобуз
 * @param expiresIn   Токен канча секунд иштейт (900 = 15 минут)
 * @param user        Кирген колдонмонунун маалыматы (пароль жок)
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
