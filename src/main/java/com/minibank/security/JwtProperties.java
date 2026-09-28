package com.minibank.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT жөндөөлөрү — `application.yml` ичинки `jwt:` бөлүмүнөн келет.
 *
 * 🧠 @ConfigurationProperties эмне?
 *   Бул бир кичине класстын тааларын, конфигурация файлынын бир бөлүмүнө
 *   "бул китепке" деп байланыштырат. Артыкчылыгы: бардык параметрлер
 *   БИР жерде, тип коюлган (String эмес — Duration), жана сыр кода
 *   көрүнбөйт.
 *
 *   Кодко 24 сааттын нанма кийин көчүрүп жазган болсок — сыр Git'ке кетмек эле.
 *   Ошондуктан сыр САРТТАН ЖОК, .env файлынан келедь.
 *
 * @param secret     HMAC сыры. 32 байттан чоң болууга ТИЙИШ КЕРЕК.
 * @param expiration Токендин жашоо убактысы (15m = 15 минут = 900 секунд).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration expiration) {
}
