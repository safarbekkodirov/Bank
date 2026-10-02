package com.minibank.exception;

/**
 * ==========================================================================
 *  МААЛЫМАТ БАЗАСЫ ЖЕТКИЛСИЗ — INFRASTRUCTURE FAILURE (PHASE 10D)
 * ==========================================================================
 *
 *  🧠 БУЛ ФАЙЛ НИ ÜÇIN?
 *
 *      PHASE 10C-de bir ýalňyş tapyldy we öňki fazada
 *      «out-of-scope» diýlip goýuldy. Ýagdaý şeýle:
 *
 *          1. Kolonoмo işleýär
 *          2. PostgreSQL ÖÇEN (docker compose stop postgres)
 *          3. Klient ÖŇKI valid JWT токенini bilen
 *             GET /api/users/me çalýar
 *          4. JwtAuthenticationFilter базадан ulanyjy
 *             gözleýär (userRepository.findById)
 *          5. Exception -> filter -> security chain ->
 *             /error -> 401
 *
 *      🔴 Bu ÝALŇ. Sebäbi 401 diýmek «SENIŇ TOKEŇIŇ
 *         NÄDOGRY» diýen many. Emma token SHOL DOGRYDI —
 *         ýalňyş ýer «токен» däl-de, «база» boldy.
 *
 *      Klient näme edýär? Eger iPhone-da bolsa: tokendi täzeleýär,
 *      ýene bir gezek hödürleýär, ýene 401 alýar, tokenini
 *      ýitirýär, ulanyjy NYKDAN çykýar. Sebäbi ONDUGY ÝOK:
 *      kömekçäniň «kadalara görä şol ýalňyş ýok» diýip
 *      subut edip bilmäge ýok.
 *
 *  🧡 TÄZE SEMANTIKA:
 *
 *      | Ýagdaý                        | HTTP | Many                     |
 *      |-------------------------------|------|--------------------------|
 *      | Token ýok/nädogry/öçen       | 401  | Authentication failure   |
 *      | Kolon çaldy, DB ýOK          | 503  | Infrastructure failure   |
 *
 *      Bu tapawut KÖNÜLDÜLDER — olar düýb-eýt başga mesele:
 *      401 = «SEN nädogry»; 503 = «SEN dogry, emma MEN peseldi».
 *
 *  🔴 BU NÄHILI 500-den ÄŇŇÄKI?
 *      Doly öwrenilende şeýle görnüp biler: «infrastructure
 *      katasy, şonuň üçin 500 gerek dälmi?»
 *      ÝOG — 500 = «server özünde ýalňyş etdi» (programma
 *      näsazlygy). 503 = «server sazlamanyňda bir ýokary ýagdaý
 *      döredildi, özi saýalýar» (infrastrukturanyň ýagdaýy).
 *      Load balancer ýaly maşyna 503 görse, ol indiki
 *      soragda ýene azykarlar — «ýalňyş» däl-de «wagtlaýyn».
 *      500 bolsa: ol ýumşak zähmetiň bolmazlygyny bilýär.
 * ==========================================================================
 *
 *  🧡 NÄME ÜÇIN ÖZ AÝRATYN KLASS?
 *      Sezbi: @RestControllerAdvice Exception.class bilen
 *      ÄHLI-ni 500 edýär. Biz ýöne «база ýок» ýagdaýyny
 *      «программа ýalňyş»dan AÝYRMAN aňlatmaly.
 *      Şonuň üçin bu ýönekeý bir ýalňyş synply — onu
 *      GlobalExceptionHandler ýörite tanap, 503 berýär.
 *
 *  🔴 KIM, NÄME ÜÇIN ULANYR?
 *      JwtAuthenticationFilter — «токен dogry, ýöne колon
 *      gözleýip bilmedi» ýagdaýynda.
 *
 *  ⚠️ BU KLASS KIMIŇ ÜÇIN DÄL:
 *      • InvalidCredentialsException — НЕТ, ol 401-dir.
 *      • UserNotFoundException     — НЕТ, ol 404-dir.
 *      • NullPointerException      — НЕТ, ol 500-dir
 *        (программа ýalňyşy, infrastructure ýalňyşy DÄL).
 */
public class DatabaseUnavailableException extends RuntimeException {

    /**
     * 🔴 Ýalňyş sebäbi — server tarapynda LOG üçin, klient üçin DÄL.
     *
     * 🧡 NÄME ÜÇIN ÝALYŇŞ SÖZ GOŞÝARYS?
     *     «Insufficient funds» ýaly ýalňyş gerek bolmadyk ýagdaýda
     *     Exception döredip, diňe «gerek däl» diýip ýatýardy — bu
     *     үсүнсиз «ААТ» bolup galyardy.
     *     Infastrukturanyň näçe gezek we haýsy ýerde üçün-üçünden
     *     gadagan edildigini görmek gerek — bu log üçin peýdaly
     *     maglumat. Emma ol HIGIŞI klient-e BARMAÝAR (тест бар).
     *
     * @param cause asyl ýalňyş — DataAccessException / PersistenceException.
     *              Biz ony «möhüm sebap» diýip saklaýarys: Stack trace
     *              logda GEREKLI, ýöne juтaşdyrylýan ýagdaýda çykmaýar.
     */
    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}