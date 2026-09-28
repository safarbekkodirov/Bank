package com.minibank.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.minibank.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT токенди ЖАСОО жана ТЕКШЕРҮҮ — колдонмодун жалгыз ушул башка
 * балдары токенге тийген.
 *
 * 🧠 JWT деген эмне?
 *     Токен = `баштапкы_бөлүк.борбор_бөлүк.кыр_бөлүк`
 *     Борбор бөлүк — БАЗА ҮЧҮН ТЕКСТ (көрүнөт, чечүүгө БОЛОТ).
 *     Кыр бөлүк — сыр менен коолгон «манжет» (чечүүгө БОЛБОЙТ).
 *
 *     Биз аны «кол китепче» деп атайбыз: кээ бир кирип чыгыш чеги бар,
 *     аны өзү жасайт, ээси өзү менен чүйлөп жүрөт. Серверде «кирип чыгыш
 *     күнтөрү» (сессия) КАЛБАЙТ.
 *
 * 🧠 Негизги эреже:
 *     Токенге ПАРОЛЬ же ПАРОЛЬ ХЭШИН КОЮУГА БОЛОТ. Жок! Анда токен
 *     уургандарга БАРЫГЫ табылады. Биз жөн гана: id, role, убакыт.
 */
@Service
public class JwtService {

    /** Токендеги «маани» аттары (claim). */
    public static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration expiration;

    /**
     * Сырдан byte[] тим кылабыз.
     * ⚠️ JJWT'дын өзү текшерет: HMAC сыры 256 бит (32 байт)дан КИЧИ
     *    болсо `WeakKeyException` ыргытат. Демек "кичи сыр" колдонулбайт —
     *    колдонмо жардамчысыз түз иштемейт, жана тест көрсөтөт.
     */
    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes());
        this.expiration = properties.expiration();
    }

    /**
     * Логин өткөндө чакырылат: колдонуучунун маалыматын токенге жазат.
     *
     * Токен ичиндеги көрүнүш (claim):
     *   sub  = колдонуучунун id ("53")   — ээси ким?
     *   role = "USER" | "ADMIN"         — ээси ким?
     *   iat  = токен түзүлгөн убакыт     — качан берилген?
     *   exp  = токен өчөт убакты         — качан бүтөт?
     *   iss  = "minibank"                — ким берди?
     *
     * 🔴 ПАРОЛЬ, ПАРОЛЬ ХЭШИ, EMAIL — ЭЧ НЕРСЕ ЖОК.
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuer("minibank")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    /** Токен канча секунд турбайт (JSON'да `expiresIn` үчүн). */
    public long getExpiresInSeconds() {
        return expiration.toSeconds();
    }

    /**
     * Токенди ТЕКШЕРЕТ жана ичиндеги id алып берет.
     *
     * JJWT бизге ЭКИ ҢАД ОРУНДА текшерет:
     *   1) кол тамгасы (кыр бөлүк) — «мени өзүм жазыпсам гана өтөт»
     *   2) өткөн убакыт (exp)     — «убакты өтсө, токен өлөт»
     *
     * @throws JwtException — токен бузулган, кол тамгасы туура эмес
     *                       же мөөрүтү өткөн.
     */
    public Long parseAndGetUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer("minibank")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.valueOf(claims.getSubject());
    }

    /** Ким экенин көрсүтүү үчүн (лог, сыноо) — id чыгарат. */
    public Long parseUserIdWithoutVerification(String token) {
        // 🔴 БУЛ МЕТОД ТОЛУК ВЕРИФИКАЦИЯ КЫЛБАЙТ! Тестте «токенде
        //    эмне бар?» деп КӨРҮҮ үчүн гана колдонулат. Өндүрүш кодунда
        //    колдонулбашы керек — антпесе колдонуучу өзүнө чүйлөп,
        //    ар кимдин маалыматын окуй алады.
        int firstDot = token.indexOf('.');
        int lastDot = token.lastIndexOf('.');
        if (firstDot < 0 || lastDot <= firstDot) {
            throw new IllegalArgumentException("Malformed token");
        }
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.substring(firstDot + 1, lastDot)));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"sub\":\"(\\d+)\"").matcher(payload);
        if (!m.find()) {
            throw new IllegalArgumentException("No sub claim");
        }
        return Long.valueOf(m.group(1));
    }
}
