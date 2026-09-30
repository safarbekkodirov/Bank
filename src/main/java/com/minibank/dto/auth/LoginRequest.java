package com.minibank.dto.auth;

import java.util.Locale;
import java.util.function.UnaryOperator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ЛОГИН ЖӨНҮМӨ — email + password.
 *
 * 🧠 Email катталуу менен БИРДЕЙ тазаланат (trim + кичи тамга),
 *    кийин биз "Ali@Mail.COM" жана "ali@mail.com" бирдей иштейт.
 *    Пароль — АБСОЛЮТТУК тазаланбайт, себеби парольдеги боштук
 *    ПАРОЛЬДУН БОЛУМУ (ошол эреже тим эле PHASE 3A'да үйрөнгөнбүз).
 *
 * @param email    кириш үчүн email
 * @param password ачык пароль — ЭЧ КАЧАН сакталбайт, тек салыштырылат
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema). Валидация өзгөргөн жок.
 */
@Schema(
        description = "Логин суроосу. Эки тааа: катталуудагы email "
                + "жана ачык пароль.",
        example = """
                {
                  "email": "test@example.com",
                  "password": "Password123"
                }"""
)
public record LoginRequest(

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address, for example name@example.com")
        @Schema(description = "Катталууда колдонулган email. "
                + "Регистрация менен бирдей: боштук тазаланып, "
                + "кичи тамгага айландырылат.",
                example = "test@example.com", format = "email")
        String email,

        @NotBlank(message = "Password must not be blank")
        @Schema(description = "АЧЫК пароль. Регистрациядагыдай эле: "
                + "ТАЗАЛАНБАЙТ, так салыштырылат. Сатылганда "
                + "BCrypt хэш менен салыштырылат.",
                example = "Password123", format = "password")
        String password
) {

    /** Катталуу менен бирдей логика — email тазаланат, пароль ОЗГӨРҮЛБЕЙТ. */
    public LoginRequest {
        email = normalize(email, value -> value.toLowerCase(Locale.ROOT));
    }

    private static String normalize(String value, UnaryOperator<String> transform) {
        return value == null ? null : transform.apply(value.trim());
    }
}
