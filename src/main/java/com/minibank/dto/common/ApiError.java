package com.minibank.dto.common;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ар бир ката үчүн бирдиктүү JSON жооп.
 *
 * 🧠 Эмне бирдиктүү формат керек?
 *    Клиент (башка программа) үчүн бир ката түрүн үч жолу башка түзүлүп
 *    берсе, ал аны чечүүгө убакыт сарптайт жана өзүнө кутулмайт.
 *    "Ката = timestamp + status + error + message + path [+ fieldErrors]"
 *    — абдан, кандай ката болсо, формат БИР.
 *
 * 🔴 Stack trace мында ЖОК. Stack trace — ички деталь; колдонуучуга
 *    "тынчтык кезирет" деп, логдорго гана жазылат. Аны чыгаруу =
 *    коопсуздук маалыматын (DB, файл жолдору) дүйнөгө чыгаруу.
 *
 * @param timestamp   — ката качыркан убакыт
 * @param status      — HTTP коду (409, 400, 500...)
 * @param error       — кодун аты (Conflict, Bad Request...)
 * @param message     — кимге, эмне айтылган
 * @param path        — кайсы барак (API) чалган
 * @param fieldErrors — таалардын каталары (400 катасында гана толук)
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema). Формат ӨЗГӨРГӨН ЖОК —
 *    ErrorContractTest сыноосу аны текшерет.
 */
@Schema(
        description = "Бүт API'деги КАТАЛЫН бирдиктүү JSON форматы. "
                + "Ката кандай болсо да, формат БИР. 401/403 мында "
                + "Spring Security'нин эмес, биздин "
                + "RestAuthenticationEntryPoint/RestAccessDeniedHandler "
                + "кайтарады.",
        example = """
                {
                  "timestamp": "2026-01-01T10:10:00Z",
                  "status": 400,
                  "error": "Bad Request",
                  "message": "Validation failed for the request",
                  "path": "/api/accounts/me/deposit",
                  "fieldErrors": [
                    { "field": "amount", "message": "Amount must be greater than 0" }
                  ]
                }"""
)
public record ApiError(

        @Schema(description = "Ката качыркан убакыт (UTC, ISO-8601).",
                example = "2026-01-01T10:10:00Z", format = "date-time")
        Instant timestamp,

        @Schema(description = "HTTP коду. Чындык: кайтарылган жооптун "
                + "реалдуу коду менен БИРДИК (400, 401, 403, 404, 409, "
                + "415, 500).",
                example = "400")
        int status,

        @Schema(description = "Кодун аты (HTTP стандарты боюнча): "
                + "Bad Request, Unauthorized, Forbidden, Not Found, "
                + "Conflict, Unsupported Media Type, Internal Server Error.",
                example = "Bad Request")
        String error,

        @Schema(description = "Адамга үчүн түшүнүктүү билдирүү. "
                + "🔴 Ички деталь ЖОК: stack trace, SQL, Hibernate, "
                + "файл жолу, класс аттары ЭЧ КАЧАН чыгып турбайт.",
                example = "Validation failed for the request")
        String message,

        @Schema(description = "Ката чыккан API жолу (query параметрсиз).",
                example = "/api/accounts/me/deposit")
        String path,

        @Schema(description = "Таа-таа деп чечтилген каталар. Валидация "
                + "каттамасы (@NotBlank, @Size...) өтпөгөндө "
                + "ТОЛТУРУЛАТ; башка каталарда — БОШ тизме [].",
                example = """
                        [
                          { "field": "amount",
                            "message": "Amount must be greater than 0" }
                        ]""")
        List<FieldViolation> fieldErrors
) {

    /**
     * Таанын катасы: кайсы таа, эмне үчүн.
     *
     * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema).
     */
    @Schema(description = "Бир таанын текшерүүсүнөн келип чыккан ката.")
    public record FieldViolation(

            @Schema(description = "Ката келип чыккан таанын аты "
                    + "(JSON'даги көрүнүшү).",
                    example = "amount")
            String field,

            @Schema(description = "Анын үчүн текшерүүнүн айткан "
                    + "калган бүтүш бүтүндөгү ката сөзү.",
                    example = "Amount must be greater than 0")
            String message
    ) {
    }

    /** Таа-каталары жок үчүн ыңгайлуу конструктор. */
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }
}
