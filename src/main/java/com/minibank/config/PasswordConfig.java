package com.minibank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder (BCrypt) — колдонмодо бир гана инстанция болушу керек.
 *
 * Эмне @Configuration/@Bean?
 *   Spring "контейнерге" бардык объектини (@Service, @Controller, @Repository)
 *   өзү жасайт. Биз кол менен new эмес, аны арыз беребиз — ал бизге Bean деп
 *   айтат. Мына ошондуктан бизде бир гана BCrypt коштормосу болот, иштеген
 *   жерде бирдей күчтүүлүк менен.
 */
@Configuration
public class PasswordConfig {

    /**
     * BCryptPasswordEncoder:
     *   - Парольди ТЕКСТКЕ КАТТАЙ АЛБАЙТ (бир тараптуу hash)
     *   - Ар бир жолу ТИЗИМДИК туз сольт => бир пароль эки эсе AR башкаша
     *   - "Жеңил" сырларды БЫСТЫК (brute-force) талгап кыйын кылат
     *
     * Мурунку күч (strength) = 10 (2^10 = 1024 айлампа). Биз колдонгон жок,
     * ДЕФОЛТ 10. Чоң сан = кооптуу, бирок ЖАЙЫЛАНЫШЫ жайылат.
     *
     * ⚠️ Чек: BCrypt кириштеги 72 байттан ашканды КЕСЕП, колтап жатат.
     *    Ошондуктан пароль талабыбызда max = 72 коюлган.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
