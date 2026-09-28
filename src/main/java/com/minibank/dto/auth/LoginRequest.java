package com.minibank.dto.auth;

import java.util.Locale;
import java.util.function.UnaryOperator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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
 */
public record LoginRequest(

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address, for example name@example.com")
        String email,

        @NotBlank(message = "Password must not be blank")
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
