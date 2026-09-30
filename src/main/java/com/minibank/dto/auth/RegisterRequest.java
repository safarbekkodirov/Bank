package com.minibank.dto.auth;

import java.util.Locale;
import java.util.function.UnaryOperator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * КАТТАЛУУ ЖӨНҮМӨ — колдонмонунон келген JSON.
 *
 * 🧠 ENTITY ≠ DTO эрежеси:
 *    Entity (User)  = маалымат базасынын үлүсү  → ички, чыгак өзгөрүшө ачык
 *    DTO (Record)   = сырткы дүйнөгү (API)      → өзгөртүүдөн коргоо, көзөмөлдөнүү
 *
 * Эмне Record? Java 21'де Record — өзгөрмөсө (immutable) маалымат усуну.
 * Анын getters/equals/toString/constructor баары автоматтык келиет.
 * DTO үчүн абдан ыңгайлуу: эч качан өзгөрө албайт, эч катан getter жазбайбыз.
 *
 * Анотациялар (@NotBlank, @Email...) — энди Controller'де @Valid
 * берилгенде автоматтык текшерүүлөр. Бул эрежелер API киришинде
 * (базага чейин!) иштейт.
 *
 * @param fullName — көрүүнүш үчүн ат (150 белгиге чейин, DB кантип)
 * @param email    — кириш үчүн email (түзүлгөн түзүлүш, 255 белгиге чейин)
 * @param password — ачык пароль (8..72 белги: 8 — кооптуулук, 72 — BCrypt чеги)
 *
 * 📄 PHASE 10A: @Schema анотациялары ТОЛУК ДОКУМЕНТАЦИЯ ҮЧҮН.
 *    Алар эч кандай валидация КОШПАЙТ — @NotBlank/@Email/@Size
 *    чыныгы текшерүүлөр өзгөрүшөр жок. @Schema болсо Swagger UI'да
 *    «эмне күтүлөт» деген маалыматты көрсөтөт.
 */
@Schema(
        description = "Катталуу суроосу. Чыныгы эсеп күйгөндө кийинки "
                + "кадамдар керек: эсеп ачуу (POST /api/accounts) жана "
                + "акча салуу (POST /api/accounts/me/deposit).",
        example = """
                {
                  "fullName": "Test User",
                  "email": "test@example.com",
                  "password": "Password123"
                }"""
)
public record RegisterRequest(

        @NotBlank(message = "Full name must not be blank")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        @Schema(description = "Колдонуучунун толук аты. Бош бөлүк жана "
                + "мурун/артындагы боштуктар тазаланат. "
                + "Максимум 150 белги.",
                example = "Test User", maxLength = 150)
        String fullName,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address, for example name@example.com")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Schema(description = "Логин үчүн колдонулуучу email. Кириш "
                + "талаасы (regex) текшерет; мурун/артындагы боштуктар "
                + "тазаланып, КИЧИ тамгага айландырылат "
                + "(Test@Example.com → test@example.com). "
                + "Максимум 255 белги.",
                example = "test@example.com",
                format = "email", maxLength = 255)
        String email,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Schema(description = "АЧЫК пароль, 8..72 белги. 8 — кооптуулук "
                + "үчүн минимум, 72 — BCrypt алгоритминин чеги. "
                + "Пароль ХЭШ түрүндө сакталып, эч качан API жообунда "
                + "кайтпайт. ТАЗАЛАНБАЙТ: боштук — парольдун бөлүмү.",
                example = "Password123", minLength = 8, maxLength = 72,
                format = "password")
        String password
) {

    /**
     * 🧠 КОМПАКТ КОНСТРУКТОР — бул record'дун "нормализация" чеги.
     *
     *   БУЛ КОД КАЧАН ИШТЕЙТ?
     *   Jackson JSONды окууп объект түзгөндө иштейт, ДЕМЕК @Valid
     *   текшерүүсүнө ТАЗАЛАНГАН маалымат киредь. Биз Service'ге
     *   кол жеткизгенде аны алдын эле тазаланган болот.
     *
     * 🧠 ЭМНЕ БУЛ ЖЕРДЕ, Service'те ЭМЕС?
     *   Биз чыныгы сыноо чыктык: "  HTTP.Case@Example.COM " жатканда
     *   сервер 400 берди. Себеби: @Email анотациясы асты-үстү боштукту
     *   ТАНЫБАЙТ — тазаланыш алдан текшеруудан КИЙИН келиши керек.
     *
     *   🏆 ТУРАБЫ ТАЗАЛОО СЫРТКЫ ЧЕГДЕ (мына) жасалышы керек.
     *   Анда ким чейт кылып жасайт — Service, Controller же Тест —
     *   БАРЫСЫ тазаланган маалымат алат. Эгер Service'те жасалганда,
     *   Service'ти башка жол менен чакырган код аны айлап өтүп,
     *   колдонуучуга 400 кайтарып берип жар болмок.
     *
     * 🧠 ЭМНЕ ПАРОЛЬДИ ТАЗАЛАБАЙБЫЗ?
     *   Парольдеги боштук — ПАРОЛЬДУН БОЛУМУ. " my pass " жана
     *   "my pass" — эки БАШКА пароль. Тазаласак, колдонуучу өзүнүн
     *   тандаган паролду жашыруун өткөн кийин унутат. Кичи тамгага
     *   которуу да жасалбайт: "P@ss" -> "p@ss" болсо, дагы эле катуусуз.
     */
    public RegisterRequest {
        fullName = trimToNullPreserving(value -> value, fullName);
        email = trimToNullPreserving(value -> value.toLowerCase(Locale.ROOT), email);
    }

    private static String trimToNullPreserving(
            UnaryOperator<String> transform, String value) {
        if (value == null) {
            return null;
        }
        return transform.apply(value.trim());
    }
}
