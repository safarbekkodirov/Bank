package com.minibank.dto.auth;

import java.util.Locale;
import java.util.function.UnaryOperator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
 */
public record RegisterRequest(

        @NotBlank(message = "Full name must not be blank")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address, for example name@example.com")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
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
