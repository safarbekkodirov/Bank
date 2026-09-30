package com.minibank.dto.auth;

import com.minibank.dto.user.UserResponse;

import io.swagger.v3.oas.annotations.media.Schema;

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
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema).
 */
@Schema(
        description = "Логин ийгилик болгондо кайтарылган жооп. "
                + "Ушул жооптун `accessToken` маанисин Swagger UI'нын "
                + "«Authorize» терезесене киргизип, корголгон "
                + "endpoint'терди сыноого болот.",
        example = """
                {
                  "accessToken": "eyJhbGciOiJIUzI1NiJ9.<мокум>.<мокум>",
                  "tokenType": "Bearer",
                  "expiresIn": 900,
                  "user": {
                    "id": 1,
                    "fullName": "Test User",
                    "email": "test@example.com",
                    "role": "USER",
                    "createdAt": "2026-01-01T10:00:00Z"
                  }
                }"""
)
public record AuthResponse(

        @Schema(description = "JWT токени (ачык көрүнгөн түрү — "
                + "кириш китепчеси). Кийинки корголгон сурооларда "
                + "«Authorization: Bearer <accessToken>» header'inde "
                + "жиберилет. Мөөрүтү өткөндө (expiresIn) жараксыз болот.",
                example = "eyJhbGciOiJIUzI1NiJ9.<мокум>.<мокум>")
        String accessToken,

        @Schema(description = "Токен түрү. HTTP стандарты боюнча "
                + "«Bearer» деп жазылат: «Authorization: Bearer <token>».",
                example = "Bearer", allowableValues = {"Bearer"})
        String tokenType,

        @Schema(description = "Токен канча СЕКУНД иштейт. Азыркы "
                + "жөндөө: 900 секунд (15 минут). Мөөрүтү өткөндө "
                + "КАЙТА логин керек (-refresh токени ЖОК).",
                example = "900")
        long expiresIn,

        @Schema(description = "Логин болгон колдонуучунун маалыматы. "
                + "Пароль жана пароль хэши БУЛ ЖЕРДЕ ЖОК.")
        UserResponse user
) {
}
