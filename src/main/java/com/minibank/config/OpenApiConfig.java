package com.minibank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 3 сүрөтүнүн ЖАРАТУУЧУ (PHASE 10A).
 *
 * ==========================================================================
 *  🧠 БУЛ ФАЙЛ НЕГЕ КЕРЕК?
 * ==========================================================================
 *  springdoc бизге endpoint'терди «өзи окуйт» (коддон). Ал чыныгы
 *  дешилген жоопторду: Controller'дин жолун, DTO'нун тааларын,
 *  SecurityConfig'теги эрежени АВТОМАТТЫК окуйт.
 *
 *  Бирок үч нерсе АВТОМАТТЫК ОКУЛМАЙТ:
 *
 *     1. КИТАПЧАНЫН АТЫ («чыныгы банк API» деп көрсөтүү)
 *     2. ВЕРСИЯ
 *     3. КООПСУЗДУК СХЕМАСЫ (JWT кандай берилет)
 *
 *  Аларды биз ЖАЗАБЫЗ — бул жерде. Бир жерде, бир таза чеч.
 *
 * ==========================================================================
 *  🔴 КООПСУЗДУК: БУЛ ФАЙЛ КООПСУЗДУК РЕЖИМДИ ӨЗГӨРТМӨЙТ
 * ==========================================================================
 *  Бул жерде биз SecurityConfig'ке бир жол да жазабыз. Анда дагы
 *  коопсуздук эрежеси («ким керек») жазылмак — биз жөн гана
 *  документация үчүн JWT схемасын СИПАТАП жазып жатабыз.
 *
 *  Реалдуу коргоо SecurityConfig'те:
 *     JwtAuthenticationFilter -> «Authorization: Bearer <token>»ды окуйт
 *     hasRole("ADMIN")        -> админ эндпоинттерин коргоёт
 *  БУЛ ЖЕРДЕ биз «документацияда ушундай көрүнөт» деп гана айтабыз.
 */
@Configuration
public class OpenApiConfig {

    /**
     * OpenAPI'деги коопсуздук схемасынын аты.
     *
     * 🧡 ЭМНЕ «bearerAuth» ДЕП АТАЛЫШЫ КЕРЕК?
     *     Чентеги Swagger UI'да «Authorize» баскычынын үстүндө
     *     «bearerAuth» деп жазылат. Биз Controller'ларда
     *     {@code @SecurityRequirement(name = "bearerAuth")} менен
     *     ЭС көрсөтөп жазып турабыз. Экө бир ат болушу ШАРТ — эгер
     *     ат башка болсо, Swagger'да токен күтпейт.
     */
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Негизги OpenAPI сүрөтү.
     *
     * 🧠 Бул bean автоматтык: колдонмо иштегенде Spring аны биргерип,
     *    `/v3/api-docs` деген жерге ушул сүрөттүн JSON'ын берет.
     *    Биз анын үстүнө «биздин» маалымат кошобуз (Info).
     *
     * @return OpenAPI 3.0 сүрөтү — анын ичинде жана кийинки
     *         сурооларда биздин маалыматыбыз, Security схемабыз, жана
     *         бардык табылган endpoint'тер.
     */
    @Bean
    public OpenAPI miniBankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini Bank API")
                        .version("1.0.0")
                        .description(describeApi()))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, bearerJwtScheme()));
    }

    /**
     * JWT Bearer коопсуздук схемасы.
     *
     * 🔴 БУЛ ЧЫНЫГЫ КООПСУЗДУК ЭМЕС — БУЛ АНЫН ДОКУМЕНТАЦИЯСЫ.
     *
     *     Чыныгы текшеруү JwtAuthenticationFilter'де. Бул жерде
     *     биз аны Swagger'га «ушундай көрүнөт» деп гана айтабыз:
     *       type: http          — HTTP header аркылуу
     *       scheme: bearer      — «Authorization: Bearer <token>»
     *       bearerFormat: JWT   — ичинде JWT токен
     *
     *     Чыныгы токендин ИЧИНДЕ эмме бар (user id, role)? Биз
     *     КАЧАНБЫ КОРСӨТӨБҮЗ — тек «<token>» жазып, Swagger'да
     *     ошол жерге «токен» жазылат. Сыр, колдонуучунун маалыматы,
     *     токендин структурасы — ДАЙЫН ДОКУМЕНТАЦИЯГА КИРБЕЙТ.
     */
    private SecurityScheme bearerJwtScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        Кириш үчүн токен. «Authorize» баскычын басып,
                        «<token>» ордуна «POST /api/auth/login»дин
                        жообундагы accessToken маанисин жазыңыз.

                        🔐 Учунчу тараптан «Bearer» деген сөз жазылбайт —
                        Swagger UI ал сөзү өзү кошоит. Демек, жөн гана
                        токендин өзүн жазыңыз.""");
    }

    /**
     * API'нин сүрөттөмөсү (description).
     *
     * 🧠 ЭМНЕ БУЛ ЖЕРДЕ МААНИЛҮҮ?
     *     Бул биринчи сөз — кийинки өңүгүмүн башында. Эгер
     *     «чыныгы банк» деп жазсак, кийинки адам чын эле аны
     *     менен иштешет деп ойлойт. Ошондуктан чыныгын АЙТЫП
     *     турабыз: бул ОКУУ максатындагы ЖЕРГИЛИКТИ жоба.
     */
    private String describeApi() {
        return """
                Окуу максатындагы локалдык banking backend. Чыныгы
                банк ЭМЕС, чыныгы акча иштемейт, чыныгы төлөм системасы
                ЖОК. Бул Java/Spring Boot үйрөнүү үчүн жасалган.

                🏦 Негизги мүмкүнчүлүктөр: катталуу, JWT менен
                логин, банк эсеби, акча салуу/чыгаруу, эсептен
                эсепке которуу, операция тарыхы (бет боюнча +
                фильтр), жана админ үчүн окуу гана.

                🔐 Коопсуздук: сессия ЖОК. Ар бир суроо «Authorization:
                Bearer <token>» header'ин талап кылат. Токен ачык
                сыр менен (HS256) кол коюлган, мөөрүтү чектелген.

                🧡 Пароль хэши, JWT сыры, SQL жана stack trace
                ЭЧ КАЧАН бул документацияда чыга жатпайт.""";
    }
}