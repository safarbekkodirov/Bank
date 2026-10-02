package com.minibank.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ==========================================================================
 *  REQUEST LOGGING — PHASE 10C
 * ==========================================================================
 *
 *  🧠 БУЛ ФАЙЛ НЕГЕ КЕРЕК?
 *
 *      «Ката бар» деген хабар — колдонмону өлтүргөн эмес, бирок
 *      «кайсы суроо, кайчан, канча убакыт, эмне үчүн 400/500»
 *      деген 4 maglumat BOLSA, 4 minutda tapylýar.
 *      Ýogsam — «Эмне ýagdaý bolup biler?» diýip 2 sagat gözleg
 *      edilýär we jogap ýok tapylýar.
 *
 *      Log şeýle görnüşde bolýar:
 *          GET /api/accounts/me -> 200 (18 ms)
 *
 *  🔴 ЭМНЕ ÜÇIN BU FILTR BILEN, BU ARTHI BIR ŞEKILDE ÝAZYLMAÝAR?
 * ==========================================================================
 *
 *      1) ⛔ Authorization HEADER — HECH HAÇAN.
 *         Энde «Bearer eyJhbGciOiJIUzI1NiJ9...» — ýagny JWT-iň
 *         ÖZI durýardy. Şol tókendiň bilen bir adam 15 minut
 *         (möhreti doly) sizin aktyňyza girip biler. Bir setir
 *         log = bir setir hakyky gapy açylyşy.
 *
 *      2) ⛔ Request BODY — HECH HAÇAN (default).
 *         Biziň DTO-larymyzda `password` bar. Body-i ýazsak,
 *         ýazgymyzda ähli açyk parol golar. (PHASE 9-daky test 20
 *         şeýle «ýalňyş» ýagdaýy saklaýardy.)
 *
 *      3) ⛔ QUERY STRING — ýazylmaýar.
 *         Biz getRequestURI() ulanýarys, getRequestURL() däl.
 *         Sebäbi URL-de «?minAmount=...&maxAmount=...» görnüp
 *         durýar — ol maýl hasaby ýaly ýörite maglumat bolup
 *         biler we logyň göwrümini çalt artyp ýetýär.
 *
 *      4) ✅ Diňe 4 zat: METHOD, JOL, STATUS, WAGT.
 *         Munuň ýeterlikligi is «tagtaýyn» bolmak üçin: bular
 *         gysga, olaryň näbelli bahasy ýok, we olar boýunça
 *         «näme haýsy endpoint gyynylyp görnüp bilýär» diýip
 *         aýdyň çykyp bilýär.
 *
 *  🧡 NÄME ÜÇIN DIŇE /api/** JOGAP EDILÝÄR?
 *      · /swagger-ui/**  — Swagger her gezek 20+ faýl (js, css)
 *        sorap alýar. Olaryň hemmesi loglanan bolsa, biziniň
 *        «hakyky» surogumuz 20 setir içinde gömülürdi.
 *      · /actuator/health — HA, al «monitorki» soraglar. Sekunda
 *        10 gezek bir adam soran bolsa, log 99% ýalan kelgilerden
 *        gurar. (Monitorki soraglaryň sanawy — auditorit/ýygnama
 *        ulgamynyň işi, ol ýörite log ýazýar.)
 *      · /v3/api-docs   — bir gezek göçürilýän dokumentasiýa.
 *
 *  🧡 NÄME ÜÇIN oncePerRequest = "bir gezek"?
 *      Bir HTTP soragy « Servlet» arkaly bir gezek gelýär. Emma
 *      «forward» bolanda (mysal üçin Swagger /swagger-ui.html →
 *      /swagger-ui/index.html) servlet ÇAŇY bir gezek ýene
 *      işledilýär. OncePerRequestFilter şonyň öňüni kesýär —
 *      ýogsam bir sorag logda 2 gezim ýazylardy.
 *
 *  📌 5xx BARLAGY: Biz ýokaryda ýazan ýalňyş barada JIKME-JIK
 *      ýazýarys. GlobalExceptionHandler ol ýerde «stack trace»
 *      bilen ýazýar (server tarapynda). Şonuň üçin bu ýerde
 *      ýene bir gezel «stack trace» goşmaýarys — bir ýalňyşy
 *      logda 2 gezi görmek gowudyr, ýöne ýalňyşy «gizlemek»
 *      ýok.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /**
     * 🧡 Biziň REST API-miziň bir bölegi bolan jollar.
     * «/api» däl-de «/api/» bolmaly: эгерде kün ýazylanda
     * «/api-docs» diýen ýa-da başga bir zol goşulýansa, ol
     * ýalňyşlykla surog hasaplanmaz.
     */
    private static final String API_PREFIX = "/api/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getRequestURI() = «/api/users/me». query string (») YOK.
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startedAtNanos = System.nanoTime();

        // 🧠 failure — nämä üçin? Eger chain.doFilter ýokary ýaňa
        //    ýalňyş zatlasa, response.getStatus() heniz «200» bolup
        //    durýar (Tomcat ýogsa ony 500 diýip üýtgeder).
        //    Biz logda «200» görkezsek — bu ÝALŇ. Şonuň üçin
        //    ýalňyş geçen ýagdaýda status hökman 500.
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
            int status = (failure == null) ? response.getStatus() : 500;

            // 🔴 4 argument. Heç haçan header, heç haçan body.
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    durationMs);
        }
    }
}