package com.minibank.dto.user;

import java.time.Instant;

import com.minibank.entity.Role;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Колдонмонунун сырткы көрүнүшү.
 *
 * ⚠️ БУЛ ЖЕРДЕ ПАРОЛЬ ЖОК. Пароль, аны анык хэши да, эч качан
 * API аркылуу КАЙТАРЫЛБАЙТ. Бул маанилүү эреже: биз чейин "hash коюп,
 * кол бош кол менен өчүрүү" деп айтабыз.
 *
 * @param id        — DB'дин ички номуру
 * @param fullName  — көрүүнүш үчүн ат
 * @param email     — email (кичи тамга менен сакталган)
 * @param role      — USER же ADMIN
 * @param createdAt — качан катталган (Instant = убакыт + убакыт белгиси)
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema).
 */
@Schema(
        description = "Колдонуучунун сырткы көрүнүшү. Пароль жана "
                + "пароль хэши БУЛ ЖЕРДЕ ЖОК жана болбос да — API "
                + "эч качан аны кайтарат.",
        example = """
                {
                  "id": 1,
                  "fullName": "Test User",
                  "email": "test@example.com",
                  "role": "USER",
                  "createdAt": "2026-01-01T10:00:00Z"
                }"""
)
public record UserResponse(

        @Schema(description = "Маалымат базасынын ички номуру. "
                + "Сырткы сурооларда пайдаланылбайт — «мен киммин» "
                + "суроосу токенден чечилет.", example = "1")
        Long id,

        @Schema(description = "Колдонуучунун толук аты.", example = "Test User")
        String fullName,

        @Schema(description = "Email, кичи тамга менен сакталган.",
                example = "test@example.com", format = "email")
        String email,

        @Schema(description = "Роль: USER (кадимки колдонуучу) же "
                + "ADMIN (баарын окуйт, жаза жок).",
                example = "USER", allowableValues = {"USER", "ADMIN"})
        Role role,

        @Schema(description = "Катталган убакыт (UTC, ISO-8601).",
                example = "2026-01-01T10:00:00Z", format = "date-time")
        Instant createdAt
) {
}
