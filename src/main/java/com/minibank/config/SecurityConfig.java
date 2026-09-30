package com.minibank.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.minibank.security.JwtAuthenticationFilter;
import com.minibank.security.JwtProperties;
import com.minibank.security.RestAccessDeniedHandler;
import com.minibank.security.RestAuthenticationEntryPoint;

/**
 * Колдонмонунун КООПСУЗДУК ТАРТИБИ.
 *
 * 🧠 Бул жерде бир нерсени түшүнүү абдан маанилүү:
 *     Spring Security — бул КОД ЭМЕС, бул бир ЧЫПКАЛАР ТИЗИМИ.
 *     Сүрөт Controller'га ЖЕТПЕЙ ТУРАТ, анын АЛДЫНДА коопсуздук
 *     чыпкалары иштейт. Биз ушул тартипти config менен ЖАЗАБЫЗ.
 *
 * 🧠 АЛТЫН ТАРТИБ (маанилүүлүк кийинки):
 *   1. @Configuration       — Spring мында чегибет
 *   2. SecurityFilterChain  — HttpSecurity көрсөткөн "коридор"
 *   3. СИЗЕ ТОКТОЙТОМ: html (form login) — БИЗДИН КОЛДОНМОДО КЕРЕК ЭМЕС
 *      Башкача айтканда: колдонуучу логин барагына БАРБАЙТ,
 *      түз кирип JSON менен токен алат (ошол эки жол бирөөсүнө)
 *   4. addFilterBefore(...) — биздин JWT чыпканы ОРТУНЧУ маанилик
 *      чыпкадан (UsernamePasswordAuthenticationFilter) АЛДЫН коёт
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 🔐 CSRF ӨЧҮРҮЛДҮ
                // CSRF = «көрүнүшсүз атакуч» (БАШКА сайт браузерге бузулган
                // JavaScript жөнөтүп, колдонуучунун эсебине суроу жасайт).
                // Биз COOKIE ЭМЕС, "Authorization: Bearer" header колдонобуз —
                // браузер автоматтык кошпойт, CSRF'ге мүмкүнчүлүк ЖОК.
                // Сессия колдонгон колдонмолор (form login) гана CSRF керек.
                .csrf(AbstractHttpConfigurer::disable)

                // 🔐 СЕССИЯ ЖОК — биз STATELESS (токен).
                // Кирип чыгып турган эсеп чыгып калбайт — токен
                // бүтсө, колдонуучу "чыкты" деп эсептелет.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 🔐 Ким кире алат?
                .authorizeHttpRequests(auth -> auth
                        // Логин жана катталуу — АЧЫК (токенсиз)
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // "Мен киммин?" — токен МИНЖИЛДУУ
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        // 🔐 PHASE 8: ADMIN УЙГУШТУРУУ - эң ЧЫҢАКЫ
                        //    ТАРТИБДЕ!
                        //    Бул ТАРТИБ МААНИЛҮҮ: төмөнкү anyRequest()
                        //    «кириш керек» деп гана ошол абалды
                        //    эске алат. Эгер admin шерти ANYREQUEST
                        //    ден СООЛ ЖАЗЫЛСА, ал ЖОК ЭЛЕ иштебейт жана
                        //    USER да /api/admin/** кире алар эле.
                        //    hasRole("ADMIN") -> "ROLE_ADMIN" издейт;
                        //    JwtAuthenticationFilter болсо role-ни
                        //    МААЛЫМАТ БАЗАСЫНАН окуп кошот
                        //    (токендеги role эмес!). Ошондуктан
                        //    базадаги role өзгөрсө, жаңы токен
                        //    керек болот - эски токен эски укугун
                        //    сактап калат.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 📄 PHASE 10A: OpenAPI/Swagger — ДОКУМЕНТАЦИЯ.
                        //    Бул САРТТЫН ТАПШЫРМАСЫ ЭМЕС. Swagger UI'ни
                        //    ачып, анда токенди киргизип, защищенный
                        //    endpoint'терди сыноо үчүн КЕРЕК. Документация
                        //    эч кандай маалымат ЖАШЫРМАЙТ: анда бир гана
                        //    схема, тасмалар, жооп форматтары көрсөтүлөт
                        //    (пароль, JWT сыры, SQL ЖОК).
                        //    🔴 ЭМНЕ ҮЧҮН БУЛ МИНИМУМ ДАРАЖАДА:
                        //    төмөндө anyRequest().authenticated() бар —
                        //    ошол эреже токенсиз САЧА БААРЫН ыргытар,
                        //    анын ичинде /v3/api-docs да. Swagger UI болсо
                        //    токенсиз ачылышы керек (эгер токен талаш
                        //    кылса — "Authorize" баскычы иштемейт).
                        //    Биздин API'лер (/api/**) ЭЧ КАЧАН ушул
                        //    ачык жолга түшпөйт — коргоо өзгөрүлбөйт.
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        // БАШКА БАРДЫК API — токенсиз КИРБЕЙТ
                        // (Фаза 4+ эсеп/акча чыгаар — ал азыр жок, эртең коргоого алабыз)
                        .anyRequest().authenticated())

                // 🔐 401/403 — HTML ЭМЕС, биздин JSON формат
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // 🔐 HTTP Basic (логин:пароль header'де) — ӨЧҮРӨЛДҮ
                .httpBasic(AbstractHttpConfigurer::disable)

                // 🔐 HTML логин барагы — ӨЧҮРӨЛДҮ
                // Биз REST API — барак жок, токен бар
                .formLogin(AbstractHttpConfigurer::disable)

                .logout(AbstractHttpConfigurer::disable)

                // 🔐 БИЗДИН ЧЫПКА: ар баштан турган Security чыпкасынын АЛДЫНДА
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
