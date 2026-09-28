package com.minibank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.auth.RegisterRequest;
import com.minibank.dto.user.UserResponse;
import com.minibank.entity.Role;
import com.minibank.entity.User;
import com.minibank.exception.EmailAlreadyRegisteredException;
import com.minibank.repository.UserRepository;

/**
 * КАТТАЛУУ ТЕСТИ (Service катмары).
 *
 * 🧠 БУЛ ТЕСТ КАНДАЙ?
 *   @SpringBootTest = ЧЫҢГЫ Spring колдонмосу: чыныгы база, чыныгы
 *   Flyway, чыныгы Bean'дер. Бирок сырткы HTTP жок — Service'ти
 *   түз чалбабыз, Java аркылуу чалабыз.
 *
 * 🧠 ЭМНЕ ЧЫҢГЫ PostgreSQL МЕНЕН?
 *   Бул этаптын негизги максаты — "Java Entity менен SQL таблицасы
 *   БИРИ-БИРИНЕ ТУУРА КЕЛЕБИ" дегенди көрсөтүү.
 *   Репозиторийди "көзөмөлчү" (mock) кылсак, биз айласып кетмек:
 *   Entity катасы (тип, узундук, @Enumerated) эч кимде БОЛБОЙ калар эле.
 *   Ошондуктан бул жерде настоящий база керек.
 *
 * 🧡 @Transactional ТЕСТТЕ = "тест бүткөндөн кийин РОЛЛБЭК".
 *   Сыноо калган маалыматты ТАЗА БАЗАДА калтыруу үчүн абдан маанилүү:
 *   бир сыноо экинчисин бузуп, сыноолор бири-бирине тоскоо болбош керек.
 */
@SpringBootTest
@Transactional
class UserServiceRegistrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 🧠 ЭМНЕ БУЛ КЕРЕК? Тест БАЗА ТАЗА деп өз ишин катуулбаш керек.
     *
     * Чыныгы окуя: биз curl менен бир колдонуучу каттарып, базага
     * "ali@example.com" калдырдык. Тест да "ali@example.com" колдонду —
     * жана БАЙКОЛБОЙ ӨЗҮН ТАБЫЛЫП, ЖЫГЫЛДИ. Бул "бир убакта иштеген,
     * бирок бирге иштебеген" тест — ЭҢ КАЙРЫТКАНЫ.
     *
     * Чечим: ар бир тесттин АЛДЫНда тазалоо. Бирок @Transactional деген
     * тестте — бүткөндөн кийин РОЛЛБЭК болгондуктан, тазалоу да
     * КАЙТЫЛАТ. Демек:
     *     тест башында  -> users = 0
     *     тест ичинде  -> катталып, текшерилет
     *     тест аягында -> РОЛЛБЭК -> колдонуучу кайра КАЛЫП КЕЛЕТ
     * Сен каттарып койгон чыныгы колдонуучуң ЖОК БОЛБОЙТ.
     */
    @BeforeEach
    void clearUsersTable() {
        userRepository.deleteAll();
    }

    private RegisterRequest request(String fullName, String email, String password) {
        return new RegisterRequest(fullName, email, password);
    }

    @Test
    @DisplayName("1. Жаны колдонмону ийгиликтүү катталат")
    void registersNewUserSuccessfully() {
        UserResponse response = userService.register(
                request("Алиев Али", "ali@example.com", "Password123!"));

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.fullName()).isEqualTo("Алиев Али");
        assertThat(response.email()).isEqualTo("ali@example.com");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("1б. ID базанын өзүнөн келеді (Hibernate IDENTITY)")
    void assignsIdFromDatabase() {
        UserResponse response = userService.register(
                request("Тест", "id-test@example.com", "Password123!"));

        User found = userRepository.findById(response.id())
                .orElseThrow(() -> new AssertionError("Базада колдонуучу табылбады"));

        assertThat(found.getId()).isEqualTo(response.id());
    }

    @Test
    @DisplayName("2. Email КИЧИ ТАМГА менен сакталат (trim + lowercase)")
    void storesEmailInLowercaseAndTrimmed() {
        userService.register(request("Тест", "  MiXeD.CaSe@Example.COM  ", "Password123!"));

        User saved = userRepository.findByEmail("mixed.case@example.com")
                .orElseThrow(() -> new AssertionError("Кичи тамгалуу email табылбады"));

        assertThat(saved.getEmail()).isEqualTo("mixed.case@example.com");
        assertThat(saved.getEmail()).doesNotContain(" ").doesNotStartWith(" ").doesNotEndWith(" ");
    }

    @Test
    @DisplayName("3. Пароль ТЕКСТ ЭМЕС — ачык сөз базада жок")
    void doesNotStorePlainTextPassword() {
        String plainPassword = "SuperSecret123!";

        userService.register(request("Тест", "plain-text@example.com", plainPassword));

        User saved = userRepository.findByEmail("plain-text@example.com").orElseThrow();

        assertThat(saved.getPasswordHash())
                .isNotEqualTo(plainPassword)
                .doesNotContain(plainPassword)
                .doesNotContain("SuperSecret");
    }
    @Test
    @DisplayName("4. Пароль BCrypt хэш түрүндө сакталат")
    void storesPasswordAsBcryptHash() {
        String plainPassword = "Password123!";

        userService.register(request("Тест", "bcrypt@example.com", plainPassword));

        String hash = userRepository.findByEmail("bcrypt@example.com")
                .orElseThrow()
                .getPasswordHash();

        // BCrypt хэшинин белгиси менен башталат
        assertThat(hash).startsWith("$2a$");

        // ЧЫҢГЫ БАЙЛЫШТЫРУУ: энкодер бул хэш "Password123!" менен туурабы?
        assertThat(passwordEncoder.matches(plainPassword, hash)).isTrue();
        // Башка пароль менен кетпеши керек
        assertThat(passwordEncoder.matches("WrongPassword", hash)).isFalse();
    }

    @Test
    @DisplayName("4б. Бир эле пароль эки колдонуучуда БАШКАСА хэш болот (соль)")
    void generatesDifferentHashForSamePassword() {
        userService.register(request("Биринчи", "salt1@example.com", "SamePassword123"));
        userService.register(request("Экинчи", "salt2@example.com", "SamePassword123"));

        String hash1 = userRepository.findByEmail("salt1@example.com").orElseThrow().getPasswordHash();
        String hash2 = userRepository.findByEmail("salt2@example.com").orElseThrow().getPasswordHash();

        // Пароль бирдей, бирок хэш ТАШКАРЫ -> "соль" иштеп жатат.
        // Эгер эки хэш бирдей болгонда, эки эле парольдүү колдонуучунун
        // бар экенин аныктаса болмок (сырткы чыгаруучулардын базаларын
        // салыштырууга мүмкүнчүлүк болмок).
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("5. Роль ДЕФОЛТТО USER БОЛОТ (колдонуучу өзү ADMIN боло албайт)")
    void assignsDefaultRoleUser() {
        userService.register(request("Тест", "role@example.com", "Password123!"));

        User saved = userRepository.findByEmail("role@example.com").orElseThrow();

        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("6. Бир email менен экинчи колдонмону катталып БОЛБОЙТ")
    void rejectsDuplicateEmail() {
        userService.register(request("Биринчи", "duplicate@example.com", "Password123!"));

        assertThatThrownBy(() -> userService.register(
                request("Экинчи", "duplicate@example.com", "AnotherPassword123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("duplicate@example.com");

        Optional<User> stored = userRepository.findByEmail("duplicate@example.com");
        assertThat(stored).isPresent();
        assertThat(stored.get().getFullName()).isEqualTo("Биринчи");
    }

    @Test
    @DisplayName("6б. Чоң тамга менен = кичи тамга менен (бир эле эл)")
    void treatsEmailCaseInsensitively() {
        userService.register(request("Биринчи", "case-test@example.com", "Password123!"));

        assertThatThrownBy(() -> userService.register(
                request("Экинчи", "CASE-TEST@example.com", "Password123!")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    @DisplayName("7. Ответте ПАРОЛЬ ЖОК (hash да жок)")
    void responseDoesNotContainPassword() {
        UserResponse response = userService.register(
                request("Тест", "response-safety@example.com", "Password123!"));

        String json = response.toString();
        assertThat(json)
                .doesNotContain("Password123!")
                .doesNotContain("$2a$")
                .doesNotContain("passwordHash");
    }
}
