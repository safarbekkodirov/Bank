package com.minibank.dto.admin;

import java.time.Instant;

import com.minibank.entity.Role;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ADMIN көрүнүшү: колдонуучу (PHASE 8).
 *
 * 🧡 «AdminUserResponse» деп ат бердик, себеби бул DTO бардык
 *     жерде колдонулбайт. «UserResponse» (PHASE 3A) - ар келонокчу
 *     өзүнүнү көрөт. «AdminUserResponse» - ADMIN админ панелинде
 *     БАШКАЛАРДЫ көрөт. Эки чек ар башка - себеби:
 *      1. Эки чек ар башка: USER эндпоинти «мен киммин» жообун
 *         бериши керек, ADMIN эндпоинти болсо «ким бар» (Id, role,
 *         updatedAt - Administrator үчүн керек).
 *      2. КИРИШ ЧЕГИ: UserResponse ге updatedAt кошпосо,
 *         /api/users/me да өзгөрүшсyz болот. Эки аралык - бир
 *         кетсе, экинчисине тийбейт.
 *
 * 🔴 ПАРОЛЬ ХЭШ БУЛ ЖЕРДЕ ЖОК, КОШУЛБАЙТ, ДАЙЫН ЖОК.
 *    Эгер бул жерде ката кылынган болсо - админ паролдарынын
 *    хэши дүйнөгө чыкмак эле. Биздин тесттер барлайт.
 *
 * @param id        ички номур
 * @param fullName  аты
 * @param email     email (кичи тамга)
 * @param role      USER | ADMIN
 * @param createdAt качан катталган
 * @param updatedAt качан өзгөргөн
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema). Пароль хэши БУЛ ЖЕРДЕ
 *    ЖОК — SecurityHardeningTest аны текшерет.
 */
@Schema(
        description = "Админ көрүнүшү: колдонуучу. 🔴 ПАРОЛЬ ХЭШ БУЛ "
                + "ЖЕРДЕ ЖОК ЖАНА КОШУЛБАЙТ. Болсо — админ "
                + "паролдарынын хэши дүйнөгө чыкмак эле.",
        example = """
                {
                  "id": 1,
                  "fullName": "Test User",
                  "email": "test@example.com",
                  "role": "USER",
                  "createdAt": "2026-01-01T10:00:00Z",
                  "updatedAt": "2026-01-01T10:00:00Z"
                }"""
)
public record AdminUserResponse(

        @Schema(description = "Колдонуучунун ички номуру.",
                example = "1")
        Long id,

        @Schema(description = "Колдонуучунун толук аты.",
                example = "Test User")
        String fullName,

        @Schema(description = "Email (кичи тамга менен сакталган).",
                example = "test@example.com", format = "email")
        String email,

        @Schema(description = "Роль: USER же ADMIN.", example = "USER",
                allowableValues = {"USER", "ADMIN"})
        Role role,

        @Schema(description = "Катталган убакыт (UTC, ISO-8601).",
                example = "2026-01-01T10:00:00Z", format = "date-time")
        Instant createdAt,

        @Schema(description = "Акыркы өзгөрүүнүн убакты (UTC, ISO-8601).",
                example = "2026-01-01T10:00:00Z", format = "date-time")
        Instant updatedAt
) {
}
