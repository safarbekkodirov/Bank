package com.minibank.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.auth.AuthResponse;
import com.minibank.dto.auth.LoginRequest;
import com.minibank.entity.User;
import com.minibank.exception.InvalidCredentialsException;
import com.minibank.mapper.UserMapper;
import com.minibank.repository.UserRepository;
import com.minibank.security.JwtService;

/**
 * АУТЕНТИФИКАЦИЯ (логин) — бизнес-логикасы.
 *
 * 🧠 СУРОО: "Логинди колдонмону арынкы кода эмне үчүн жазабыз?"
 *     Жооп: Логика ӨЗГӮЧӮБӨН УЧУН. Кийин:
 *       - "Google менен кирүү" -> бул жерде кошобуз
 *       - "Башка контейнерде токен чыгаруу" -> бул жерден чалма-чал
 *       - "Тест -> чыныгы эмес, жасалма UserService" -> тектү кылып,
 *         чыныгы маалымат базасына улам кириш болбосо
 *     Эгер логика Controller'де болсо, кийин аны көчүрүп, башка
 *     жерде ТАГДА БАЙЛАМЫ келет.
 */
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    /**
     * 🛡️ УБАҚЫТТЫН ТАҮШҮНДӨӨСҮ (timing attack) КАРШЫ КОРГОО
     *
     * 🧠 КАМАР? BCrypt АЛДЫННАН БАЙКАТ КЫЙИН ТЕКШЕРЕТ.
     *     - Колдонуучу БАР болсо -> BCrypt иштейт -> ЖАЙ ЖОOP (50-100 мс)
     *     - Колдонуучу ЖОК болсо -> БАЗАГА БАРЫП ЭЛЕ ТАБЫЛБАЙТ -> ТЕЗ
     *
     *     Чыгаруучу убакытты өлчөп, "120 мс = пароль бар, 5 мс = жок"
     *     деп чыгара алат. Биз андай МААЛЫМАТ БЕРБЕЙБИЗ.
     *
     * 🛡️ ЧЕЧИМ: Колдонуучу жок болгон учурда да, ЧЫНДАП ЭМЕС, ТАЗА
     *     ЭМЕС, БИР ХЭШТИ BCrypt салыштыруу ЖАСАП, убакытты
     *     КАЛЫП КОЛДОНОБУЗ. Натыйжа экөө учурда тең жай.
     *
     *     Демек: "email жок" жана "пароль туура эмес" жооптору
     *     ЖАНА убакыты БИРДЕЙ.
     */
    private final String timingProtectionHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        // Иштеген учурда БИР ЖОЛУ эле эсептелет — ар бир ката жолунда
        // кайра-кайра эсептөбөгөн үчүн (башка жакка тез).
        this.timingProtectionHash = passwordEncoder.encode("this-is-not-a-real-password");
    }

    /**
     * ЛОГИН: email + password -> JWT токен.
     *
     * Баскыч-баскыч:
     *   (1) email тазаланган келедь (LoginRequest компакт конструкторунда)
     *   (2) маалымат базасында барбы?
     *   (3) BCrypt.matches(ачык пароль, хэш) — БИР ТАРАПТУУ
     *   (4) токен түзүү
     *   (5) жооп
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email();

        // (2) Издөө
        User user = userRepository.findByEmail(email).orElse(null);

        // (2б) ЖОК болсо: убакытты бирдей кылуу үчүн чыныгы эмес
        //      салыштыруу жасап, РЕАЛ ПАРОЛЬ ХЭШИНЕ БАРБАЙ, жооп беребиз
        if (user == null) {
            passwordEncoder.matches(request.password(), timingProtectionHash);
            throw new InvalidCredentialsException();
        }

        // (3) 🔐 Парольди САЛЫШТЫРАБЫЗ. Сырт, бир багыттуу.
        //     "Password123!" -> $2a$10$... -> эсептөө -> туура / жалган
        //     Биз СЫРДЫ БИЗБЕЙБИЗ, биз АНЫ ТЕКШЕРЕБИЗ.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // (4) Токен
        String token = jwtService.generateToken(user);

        // (5) Жооп
        return new AuthResponse(
                token,
                TOKEN_TYPE,
                jwtService.getExpiresInSeconds(),
                userMapper.toResponse(user));
    }
}
