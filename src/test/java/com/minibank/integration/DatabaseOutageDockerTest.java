package com.minibank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 *  REAL DATABASE OUTAGE — PHASE 10D (opt-in, docker)
 * ==========================================================================
 *
 *  🧡 BU TEST NÄME ÜÇIN BAR? (DatabaseFailureAuthenticationTest-den
 *     näme tapawutly?)
 *
 *      DatabaseFailureAuthenticationTest → «FILTER haýsy ýagdaýda 503
 *          diýär?» diýen LOGIKIÝA soragyny barlaýar (spy arkaly,
 *          hakykata ýokaryk ýagdaýy döredýär).
 *
 *      BU TEST → «ÇYNY JAR, çYNY PostgreSQL öçen ýagdaýda nähili
 *          işleýär?» diýen REALISTIKANY barlaýar.
 *
 *      Iň howply ýagdaý: «ýalňyş ýok diýip öwrendik, emma hakykata
 *      işlemeýär» — şonuň üçin ikisi hem gerek.
 *
 * ==========================================================================
 *  🔴 HOWPSUZLYK: BU TEST MAGLUMAT ÝITIRMEÝÄR
 * ==========================================================================
 *
 *      docker compose stop postgres     → diňe ÖÇÜRÝÄR
 *      docker compose start postgres    → diňe ÇALÝAR
 *
 *      ⛔ «docker compose down -v» BU TESTDE HIÇ HAÇAN ÝOK!
 *      ⛔ @AfterAll — «hemişe» konteýneri gaýtadan işledýär.
 *      ⛔ Test ÖŇKI ýagdaýy ýatlayar: eger Postgres başdan DOWN bolsa,
 *          ony işletmäýäris (developer-iň ýagdaýyny üýtgetmeýäris).
 *
 *  🟡 @BeforeAll: bu test ÖZ-ÖZÜN JAR başylýar.
 *      Ýogsam test «işläp durýarmy?» diýip hiç bir şerti barlaýyp
 *      bolmaýardy — şeýle bir test «ýalňyşdan yzarlanýar» diýip
 *      hakykata hiç bir şeyi barlamaýar.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "PHASE10D_DOCKER_TESTS",
        matches = "true", disabledReason = "Set PHASE10D_DOCKER_TESTS=true to run")
class DatabaseOutageDockerTest {

    private static final int APP_PORT = 8080;
    private static final String BASE = "http://localhost:" + APP_PORT;
    private static final String PASSWORD = "Password123!";

    private static Path projectRoot;
    private static Process application;
    private static boolean databaseWasRunning;
    private static String validToken;
    private static final String TEST_EMAIL = "phase10d-outage@example.com";

    @BeforeAll
    static void setUpEverything() throws Exception {
        projectRoot = locateProject();

        // 🟡 Öňki ýagdaýy ýATLAÝARYS — öňürti ýok etmeýäris.
        databaseWasRunning = isDatabaseUp();
        if (!databaseWasRunning) {
            // Bu ýagdaýda test öz işini barlap bilmez: öňünden
            // DOWN bolan bazany «gaýtadan işledip» ölçemek bize
            // gadagan — ol developer-iň ýagdaýydyr.
            return;
        }

        startApplication();
        waitForHealth("UP");

        registerAndLogin();
    }

    @AfterAll
    static void tearDownEverything() throws Exception {
        // 🔴 BU BÖLÜM IŇ MÖHÜM — «her halatynda» arassa ýagdaý.
        stopApplication();
        if (databaseWasRunning) {
            runQuietly("start");
        }
    }

    // ==================================================================
    //  1) ЧЫНЫГЫ ЫЧЫЛЫШ: DB ÖÇEN — 401 DÄL!
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("1. valid token + real DB outage -> 503 (401 BOLMAÝAR!)")
    void validTokenDuringRealOutageIsNotUnauthorized() throws Exception {
        assumeInfrastructureReady();

        // 1) Işleýän ýagdaý — token hakykat.
        assertThat(authStatus("/api/users/me"))
                .as("baseline: DB is UP and the token is valid")
                .isEqualTo(200);

        // 2) 🔴 Hakyky outage.
        runQuietly("stop");
        waitForDatabaseDown();

        // 3) 🔴 ESASY BARLAG: 401 BOLMAÝAR.
        long startedAt = System.currentTimeMillis();
        int outageStatus = authStatus("/api/users/me");
        long elapsedMs = System.currentTimeMillis() - startedAt;

        assertThat(outageStatus)
                .as("database outage must NOT be reported as 401")
                .isNotEqualTo(401);
        assertThat(outageStatus)
                .as("database outage must be a 5xx infrastructure failure")
                .isEqualTo(503);

        // 🧡 PHASE 10C-däki Hikari sazlamasy: 30 sekunt däl-de 3 sekunt.
        assertThat(elapsedMs)
                .as("DB outage must not block for Hikari's default 30s timeout")
                .isLessThan(15_000L);

        // 4) ⛔ Jogap ýalňyş syzdyrmaly däl.
        String body = authenticatedBody("/api/users/me");
        assertThat(body).doesNotContain("org.springframework");
        assertThat(body).doesNotContain("jdbc");
        assertThat(body).doesNotContain("localhost");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain(PASSWORD);
        assertThat(body).doesNotContain(validToken);
    }

    @Test
    @Order(2)
    @DisplayName("2. nädogry token outage wagtynda hem ÝENE 401")
    void invalidTokenStillUnauthorizedDuringOutage() throws Exception {
        assumeInfrastructureReady();

        runQuietly("stop");
        waitForDatabaseDown();

        // 🔴 «Her şey 503» diýen ýalňyşlyga garşy:
        // token ýalňyş bolsa — ol 401 BOLMALY, bazа bolsa 503.
        assertThat(statusWithHeader("/api/users/me", "Bearer not.a.real.jwt"))
                .as("an INVALID token must stay 401 even when the DB is down")
                .isEqualTo(401);

        assertThat(status("/api/users/me"))
                .as("NO token at all must stay 401 even when the DB is down")
                .isEqualTo(401);
    }

    @Test
    @Order(3)
    @DisplayName("3. DB gaýtansa — şol bir token YENE işleýär")
    void recoveryRestoresNormalBehaviour() throws Exception {
        assumeInfrastructureReady();

        runQuietly("start");
        waitForDatabaseUp();
        waitForHealth("UP");

        // 🧡 «Recovery» diýmek diňe 200 däl-de, ŞOL bir token-iň
        //    özüniň işlemegini aňladýar — ýagny biz «ýarym»
        //    goýberen ýagdaý ýok.
        assertThat(authStatus("/api/users/me"))
                .as("the very same token must work again after recovery")
                .isEqualTo(200);
    }

    // ==================================================================
    //  INFRASTRUCTURE
    // ==================================================================

    private static void assumeInfrastructureReady() {
        org.junit.jupiter.api.Assumptions.assumeTrue(databaseWasRunning,
                "PostgreSQL was not running when the test started — "
                        + "the test will not change a developer-set state");
        assertThat(validToken).as("the application must have started and logged in")
                .isNotBlank();
    }

    private static Path locateProject() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            if (Files.exists(dir.resolve("compose.yml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate project root");
    }

    private static void startApplication() throws Exception {
        Path jar = projectRoot.resolve("target/minibank-0.0.1-SNAPSHOT.jar");
        assertThat(Files.exists(jar))
                .as("Run ./mvnw clean package BEFORE this test "
                        + "(the JAR must exist to start the application)")
                .isTrue();

        application = new ProcessBuilder(
                "java", "-jar", jar.toAbsolutePath().toString())
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(projectRoot.resolve("target/phase10d-app.log").toFile())
                .start();
    }

    private static void stopApplication() {
        if (application != null) {
            application.destroy();
            try {
                application.waitFor(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                application.destroyForcibly();
            }
        }
    }

    private static void waitForHealth(String expected) throws Exception {
        for (int i = 0; i < 60; i++) {
            if (expected.equals(healthStatus())) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Application never became health=" + expected);
    }

    private static String healthStatus() {
        try {
            String body = curl("/actuator/health", null);
            if (body.contains("\"UP\"")) {
                return "UP";
            }
            if (body.contains("\"DOWN\"")) {
                return "DOWN";
            }
        } catch (Exception ignored) {
            // Application heniz ýok.
        }
        return "UNKNOWN";
    }

    private static void registerAndLogin() throws Exception {
        String payload = "{\"fullName\":\"Phase 10D\","
                + "\"email\":\"" + TEST_EMAIL + "\","
                + "\"password\":\"" + PASSWORD + "\"}";
        curl("/api/auth/register", payload);

        String login = "{\"email\":\"" + TEST_EMAIL + "\","
                + "\"password\":\"" + PASSWORD + "\"}";
        String response = curl("/api/auth/login", login);
        int from = response.indexOf("\"accessToken\":\"");
        int to = response.indexOf('"', from + 15);
        validToken = response.substring(from + 15, to);
    }

    // ==================================================================
    //  DOCKER HELPERS — diňe «stop»/«start», hiç haçan «down -v»!
    // ==================================================================

    private static void runQuietly(String action) throws Exception {
        new ProcessBuilder("docker", "compose", action, "postgres")
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor(120, TimeUnit.SECONDS);
    }

    private static boolean isDatabaseUp() throws Exception {
        Process process = new ProcessBuilder(
                "docker", "compose", "ps", "--status", "running", "--services")
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        process.waitFor(60, TimeUnit.SECONDS);
        return process.exitValue() == 0 && output.contains("postgres");
    }

    private static void waitForDatabaseUp() throws Exception {
        for (int i = 0; i < 60 && !isDatabaseUp(); i++) {
            Thread.sleep(1000);
        }
    }

    private static void waitForDatabaseDown() throws Exception {
        for (int i = 0; i < 60 && isDatabaseUp(); i++) {
            Thread.sleep(1000);
        }
    }

    // ==================================================================
    //  HTTP HELPERS
    // ==================================================================

    private static int status(String path) throws Exception {
        return statusWithHeader(path, null);
    }

    /** 🧡 ŞOL BIR «valid token» bilen — authenticated sorag. */
    private static int authStatus(String path) throws Exception {
        return statusWithHeader(path, "Bearer " + validToken);
    }

    private static int statusWithHeader(String path, String authorization) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(
                java.util.List.of("curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                        "-m", "45", BASE + path));
        if (authorization != null) {
            command.add("-H");
            command.add("Authorization: " + authorization);
        }
        Process process = new ProcessBuilder(command).start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        process.waitFor(60, TimeUnit.SECONDS);
        return output.isEmpty() ? 0 : Integer.parseInt(output);
    }

    private static String body(String path) throws Exception {
        return curl(path, null);
    }

    /** 🧡 ŞOL BIR token bilen jogabyň özi (ýalňyş sızdırmak üçin). */
    private static String authenticatedBody(String path) throws Exception {
        return curl(path, null, "Bearer " + validToken);
    }

    private static String curl(String path, String jsonBody) throws Exception {
        return curl(path, jsonBody, null);
    }

    private static String curl(String path, String jsonBody,
                               String authorization) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(
                java.util.List.of("curl", "-s", "-m", "45", BASE + path));
        if (authorization != null) {
            command.add("-H");
            command.add("Authorization: " + authorization);
        }
        if (jsonBody != null) {
            command.add("-H");
            command.add("Content-Type: application/json");
            command.add("--data");
            command.add(jsonBody);
        }
        Process process = new ProcessBuilder(command).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            process.waitFor(60, TimeUnit.SECONDS);
            return sb.toString();
        }
    }
}
