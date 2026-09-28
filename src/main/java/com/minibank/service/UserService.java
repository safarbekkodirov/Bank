package com.minibank.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.auth.RegisterRequest;
import com.minibank.dto.user.UserResponse;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.exception.EmailAlreadyRegisteredException;
import com.minibank.exception.UserNotFoundException;
import com.minibank.mapper.UserMapper;
import com.minibank.repository.UserRepository;

/**
 * Колдонмону боюнча бизнес-логика.
 *
 * 🧠 Service деген эмне?
 *   Бул — "эслик журнал": КАДАМДАРдын ичиндеги нерсе.
 *   Controller ТАПШЫРМА берсе, Service "ЭЧ КИМДИН ЭМНЕ КЫЛУУСУ" көрсөтөт:
 *     1. email тазалоо
 *     2. ушул email барбы -> текшерүү
 *     3. пароль хэшлөө
 *     4. User түзүү
 *     5. базага салуу
 *   Мына бул жерде "@Transactional" -> "БААРЫ бир иш болсун: же баары,
 *   же ЭЧ НЕРСЕ".
 *
 * 🧡 КОНСТРУКТОР аркылуу четинеү
 *   Биз эч нерсени new кылбайбыз: репозиторий, энкодер, маппер —
 *   баары Springге "мене керек" деп айтабыз, ал бизге берип жатат.
 *   (Бул "Dependency Injection". Тестте биз аларды кол менен "подмена"
 *   кылып, чыныгы базага уламай турган кылабыз.)
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /**
     * КАТТАЛУУ.
     *
     * @Transactional = "Бул ыкма ичиндеги БАРДЫК DB операциялары бир
     * "иш" болсун. Ката болсо — БАРДЫГЫ КАЙТЫЛ (rollback) жана
     * колдонуучунун базада жарым-парттуу колдонмосу КАЛБАЙТ.
     *
     * Биздин бул ыкмада эки DB буйругу бар (exists? + save). Экөөнү
     * тез жашып кетсе, экөөнү тез — же эч нерсе. Мисалы, эгер
     * save учурунда интернет үзүлсө: колдонуучу калмайт, эсеп эсеги
     * ичи калмайт.
     *
     * @param request — колдонмону келген маалымат (анын парольсүз)
     * @return        — колдонуучу маалыматы (парольсүз, көрүнүш үчүн гана)
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User with id " + id + " not found"));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        // (1) Email ТАЗАЛАНГАН келген — RegisterRequest'дин өзүндө
        //     (тымдаш конструкторунда) trim керилген. Бизге мына ошол
        //     тазалган маалымат келет. Кээде көпүнөн ошол жерде гана
        //     жасаш керек экенин ойлойбуз, бирок бул — КИРИШ ЧЕГИ.
        //     🛡️ Кийинки коргоо: маалымат базасында да CHECK (email = lower(email))
        String email = request.email();

        // (2-3) Ушул email барбы? -> эгер бар, бизнеската (409).
        //       🧠 БУНДА Java тарапта. Маалымат базасында да UNIQUE бар.
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        // (4) Парольди хэшлөө.
        //     🔴 МЫКТЫҮ: Пароль бул жерде өзгөрөт -> өзгөргөн нерсе
        //     гана сакталат. Коддун өзүнөн да кейин бул өзгөрүү чарчамайт.
        String passwordHash = passwordEncoder.encode(request.password());

        // (5-7) Entity түзүү.
        //        🔐 РОЛЬ = АР ДАЙИН "USER". Биз аны колдонмодон кийин:
        //        чүн кийин "Просьба на роль админа" деген өтүнүчтү
        //        кошпоо керек! Демек, бул жердеги 6-сап абдан маанилүү.
        User user = new User(
                request.fullName().trim(),
                email,
                passwordHash,
                Role.USER
        );

        // (8) Базага салуу. (id, createdAt, updatedAt Hibernate кошот)
        User savedUser = userRepository.save(user);

        // (9-10) Entity -> DTO (пароль жок) жана жоопко кайтаруу.
        return userMapper.toResponse(savedUser);
    }
}
