package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * ==========================================================================
 *  DATABASE BACKUP / RESTORE — PHASE 10B
 * ==========================================================================
 *
 *  🧠 БУЛ ТЕСТ ЭМНЕ УЧҮН?
 *
 *  Скрипттер — СЫРТКЫ утилиталарга (docker, pg_dump, pg_restore)
 *  таянып, файл менен иштейт. Мына тезис:
 *
 *      «скрипт иштеди» деген сөз — онын КАТАСЫ ОЙБОЛУШУ
 *      жөнүндө эмес, АЗЫРКЫ абал жөнүндө. Эртең кечке бузулуп
 *      калган скрипт «иштеп жаткан» көрүнөт.
 *
 *  Ошондуктан биз эң ЖАМААН деген 3 нерсени тосуп алабыз:
 *      1. backup чыныгы файл ЖАРАТАБЫ?         (0 байт эмес)
 *      2. ал ЧЫНЫГЫ PostgreSQL dump мүмкүнбү?  (pg_restore --list)
 *      3. restore скрипты ТЕЗИГЕЗДЕ базаны ӨЧҮРБӨЙМҮ?
 *
 * ==========================================================================
 *  🔴 ӨТӨ МААНИЛҮҮ: БУЛ ТЕСТ МААЛЫМАТ БАЗАНЫ ӨЧҮРБЕЙТ
 * ==========================================================================
 *  Restore СКРИПТИ «--yes-please» менен чыныгы базаны тазалайт.
 *  Биз бул тестте ошун ЧАЛБАЙТ. Антпесе, жакында run болгон
 *  башка тестлерге маалымат ташып, 323 тест «кокус» бузулар эле.
 *
 *  Эгерде бир күнү чыныгы backup→restore СЦЕНАРИЙИН автоматтык
 *  кылуу керек болсо — ал БОЛТУН башка базада (CREATE DATABASE
 *  ..._test) болушу керек, эч качан .env'теги базада эмес.
 *
 *  🟡 ЭМНЕ @EnabledIfEnvironmentVariable?
 *  Docker/скрипттер CI'да болбосо, тест «эмне кылбастан» өтүп
 *  кетсин (skip) деп издейбиз. «Иштеген» жанылганды
 *  жалган чыгаргандан артык.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "PHASE10B_DOCKER_TESTS",
        matches = "true", disabledReason = "Set PHASE10B_DOCKER_TESTS=true to run")
class DatabaseBackupRestoreTest {

    /** Каталог: ар кандай иштеп жатканда да проекттиң тамырын табабыз. */
    private static Path projectDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            if (Files.exists(dir.resolve("scripts/backup-db.sh"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate project root");
    }

    private static Path backupScript;
    private static Path restoreScript;
    private static Path createdBackup;

    @BeforeAll
    static void locateScripts() {
        Path root = projectDir();
        backupScript = root.resolve("scripts/backup-db.sh");
        restoreScript = root.resolve("scripts/restore-db.sh");

        // 🛡️ Скрипт ЭЧ КАЧАН жок болмашы керек — Docker'сиз CI'да
        //    чалыштыруу «docker табылмады» деп чыга эле, чыныгы
        //    себеби жашырун калмак эле.
        assertThat(Files.isExecutable(backupScript))
                .as("scripts/backup-db.sh must exist and be executable (chmod +x)")
                .isTrue();
        assertThat(Files.isExecutable(restoreScript))
                .as("scripts/restore-db.sh must exist and be executable (chmod +x)")
                .isTrue();
    }

    // ==================================================================
    //  1) BACKUP ЖАРАТАБЫ?
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("1. backup-db.sh -> файл ЖАРАТАДЫ, бош эмес, backups/ ичине")
    void backupScriptCreatesNonEmptyDump() throws Exception {

        Result result = run(backupScript.toString());

        assertThat(result.exitCode())
                .as("backup script output:\n%s", result.output())
                .isZero();

        // Скрипт акырында файлдын жолун stdout'ка басып берет.
        Path file = lastPathLine(result.output());
        assertThat(file).as("backup artifact path").isNotNull();

        createdBackup = file;
        assertThat(Files.exists(createdBackup)).isTrue();
        assertThat(Files.size(createdBackup))
                .as("backup must NOT be 0 bytes (an 'empty success' is a LIE)")
                .isGreaterThan(0L);
        assertThat(createdBackup.toString())
                .as("backups must land inside backups/ so .gitignore can protect them")
                .endsWith(".dump");
        assertThat(createdBackup.getParent().getFileName().toString())
                .isEqualTo("backups");
    }

    @Test
    @Order(2)
    @DisplayName("2. backup — ЧЫНЫГЫ PostgreSQL dump, 4 таблица ТОЛУМ")
    void backupArtifactIsAValidPostgresDump() throws Exception {

        assertThat(createdBackup).as("step 1 must run first").isNotNull();

        Result copy = run("docker", "cp", createdBackup.toString(),
                "minibank-postgres:/tmp/verify_java.dump");
        assertThat(copy.exitCode()).as("docker cp:\n%s", copy.output()).isZero();

        Result toc;
        try {
            toc = run("docker", "exec", "minibank-postgres",
                    "pg_restore", "--list", "/tmp/verify_java.dump");
        } finally {
            run("docker", "exec", "minibank-postgres", "rm", "-f", "/tmp/verify_java.dump");
        }

        // 🔴 ЭГЕР pg_restore файлды ОКУП БОЛБОСО — «backup бар»
        //    деген сөз ТУЗДУК болгон болот. Биз «бар» деп эле
        //    ишенбейт — «ОКУЛУП БОЛДУ» деп иштейбиз.
        assertThat(toc.exitCode())
                .as("pg_restore --list must READ the backup:\n%s", toc.output())
                .isZero();

        // 🧡 ЭМНЕ БУЛ ЖЕРДЕ «public users» ДЕП ТЕКШЕРЕБИЗ?
        //    pg_restore --list каждары мынадай:
        //        217; 1259 16385 TABLE public users        minibank
        //    • Ат СИРТТА ээнин аты («minibank») тургандыктан
        //      «...users$» деп текшерсек — АСТАСЫНАН ЭЧ УЧРАН
        //      ыйнамак эле. (Биз буну PHASE 10B'де чыныгтан
        //      ушул күйгө түгелди.)
        //    • «TABLE» жана «TABLE DATA» экөөсү — схема да,
        //      маалымат да болушу керек.
        for (String table : List.of("users", "accounts",
                "transactions", "flyway_schema_history")) {
            boolean present = toc.output().lines()
                    .anyMatch(l -> l.matches(".*\\bTABLE( DATA)? public " + table + "\\s.*"));
            assertThat(present)
                    .as("backup must contain schema+data for table «%s»", table)
                    .isTrue();
        }
    }

    // ==================================================================
    //  2) RESTORE ТЕЗИГЕЗДЕ ӨЧҮРБӨЙМҮ?  (ЭҢ МААНИЛҮҮ БӨЛҮМ)
    // ==================================================================

    @Test
    @Order(3)
    @DisplayName("3. restore — «--yes-please» БОЛМАСА ЭЧ НИМЕ ӨЧҮРМЕЙДҮ")
    void restoreScriptRefusesToDestroyWithoutExplicitFlag() throws Exception {

        assertThat(createdBackup).as("step 1 must run first").isNotNull();

        String before = countsOfBusinessTables();

        // 🛡️ ЭҢ МААНИЛҮҮ ТЕСТ. Бул тест ӨТПӨСӨ — скрипт
        //    «кунун таш эте» деп жазылган, еки жолу баскан
        //    көнүп өткөн кимдирге маалымат ЖОГОЛУП калмак эле.
        Result dryRun = run(restoreScript.toString(), createdBackup.toString());

        assertThat(dryRun.output())
                .as("must WARN about the destructive operation")
                .contains("БУЛ ЖОК ЭТҮҮЧҮ БУЙРУК");
        assertThat(dryRun.output())
                .as("must explain how to really do it")
                .contains("--yes-please");
        assertThat(dryRun.output())
                .as("must state that this is a local LEARNING project")
                .contains("ОКУУ ДОЛБООР");

        String after = countsOfBusinessTables();

        assertThat(after)
                .as("DRY-RUN MUST NOT CHANGE A SINGLE ROW")
                .isEqualTo(before);
    }

    @Test
    @Order(4)
    @DisplayName("4. restore — жок/бузулган файл -> КАТА (жогандук иш success көрүнбөйт)")
    void restoreScriptFailsLoudlyOnBadInput() throws Exception {

        // Файл ЖОК
        Result missing = run(restoreScript.toString(), "backups/definitely-missing-file.dump");
        assertThat(missing.exitCode())
                .as("missing backup file must fail (non-zero)")
                .isNotZero();

        // Файл БОШ (0 байт)
        Path empty = Files.createTempFile("phase10b-empty", ".dump");
        Result emptyResult = run(restoreScript.toString(), empty.toString());
        assertThat(emptyResult.exitCode())
                .as("empty (0 byte) backup must fail")
                .isNotZero();
        Files.deleteIfExists(empty);

        // Файл БУЗУЛГАН (каарык күн)
        Path corrupt = Files.createTempFile("phase10b-corrupt", ".dump");
        Files.write(corrupt, randomBytes(4096));
        Result corruptResult = run(restoreScript.toString(), corrupt.toString());
        assertThat(corruptResult.exitCode())
                .as("corrupted backup must fail (pg_restore --list rejects it)")
                .isNotZero();
        Files.deleteIfExists(corrupt);

        // Аргумент ЖОК
        Result noArgs = run(restoreScript.toString());
        assertThat(noArgs.exitCode())
                .as("missing argument must fail and print usage")
                .isNotZero();
    }

    @Test
    @Order(5)
    @DisplayName("5. backup — жазылмай турган макет -> КАТА")
    void backupScriptFailsWhenTargetIsNotWritable() throws Exception {

        // «.../no-such-dir/x.dump» — ата-эне папка жок.
        Result bad = run(backupScript.toString(), "no-such-directory/x.dump");
        assertThat(bad.exitCode())
                .as("unwritable/missing target dir must fail BEFORE pg_dump runs")
                .isNotZero();
        assertThat(bad.output())
                .as("must explain WHY (not a silent pg_dump error)")
                .contains("ХАТА");
    }

    @AfterAll
    static void cleanUp() {
        // Биз түзгөн backup файлын тазалайбыз — тест артында
        // диск толуп калбасын. ЧЫНЫГЫ колдонуучунун backup'лары
        // башка ат менен түзүлөт, аларды көрбөйбүз.
        if (createdBackup != null) {
            try {
                Files.deleteIfExists(createdBackup);
            } catch (IOException ignored) {
                // тазалау — эң маанилүү эмес
            }
        }
    }

    // ==================================================================
    //  ЖАРДАМЧЫЛАР
    // ==================================================================

    /** Негизги үч таблицанын санын «users/accounts/txns» форматында. */
    private static String countsOfBusinessTables() throws Exception {
        Result r = run("docker", "exec", "minibank-postgres", "psql",
                "-U", dbUser(), "-d", dbName(),
                "-tAc", "select (select count(*) from users)||'/'||"
                        + "(select count(*) from accounts)||'/'||"
                        + "(select count(*) from transactions);");
        assertThat(r.exitCode())
                .as("count query failed:\n%s", r.output()).isZero();
        return r.output().trim();
    }

    private static String dbName() {
        return env("POSTGRES_DB");
    }

    private static String dbUser() {
        return env("POSTGRES_USER");
    }

    /** .env'ти окуйбуз — биздеги бирден-бир чыныгы конфигурация. */
    private static String env(String key) {
        try {
            for (String line : Files.readAllLines(Path.of(".env"), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key + "=")) {
                    return trimmed.substring(key.length() + 1).trim();
                }
            }
        } catch (IOException ignored) {
            // .env жок болсо — Spring Boot да аны окуй алмак эле.
        }
        return "minibank";
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new java.security.SecureRandom().nextBytes(data);
        return data;
    }

    /** Скрипт stdout'унда акыркы «жол» сыртаган сабы. */
    private static Path lastPathLine(String output) {
        List<String> lines = new ArrayList<>(List.of(output.split("\\R")));
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.startsWith("/") && line.contains("backups/")) {
                return Path.of(line);
            }
        }
        return null;
    }

    private static Result run(String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        // 🔴 КУТУУ ЧӨҮГӨН: чексиз күтсөк, калбай калган процесс
        //    CI'ны ТҮГЕСДИРИП коёт. 120 секунд — артык.
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "Command timed out: " + String.join(" ", command));
        }

        String output = new String(
                process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        return new Result(process.exitValue(), output);
    }

    private record Result(int exitCode, String output) {
    }
}