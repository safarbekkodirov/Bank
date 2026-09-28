package com.minibank.dto.common;

import java.time.Instant;
import java.util.List;

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
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {

    /** Таанын катасы: кайсы таа, эмне үчүн. */
    public record FieldViolation(String field, String message) {
    }

    /** Таа-каталары жок үчүн ыңгайлуу конструктор. */
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }
}
