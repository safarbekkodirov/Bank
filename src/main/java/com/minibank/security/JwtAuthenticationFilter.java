package com.minibank.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.dto.common.ApiError;
import com.minibank.entity.User;
import com.minibank.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ар бир HTTP сүрөткө чейин иштеген ЧЫПКА (filter).
 *
 * 🧠 Чыпка деген эмне?
 *     Сүрөт Controller'га жетпей ТУРАТ. Сервердеги БАРДЫК сүрөт
 *     аныктын аркылы өтөт: текшерүү → өткөрүү → кайта өткөрүү.
 *
 * 🧠 БИЗДИН ЧЫПКАНЫМЫЗДЫН ИШИ (сиз айткан 8 кадам):
 *   1. "Authorization: Bearer <token>" header барбы?
 *   2. Жок -> СЕБИТ ТОКТОТПАЙДЫ (ачык жерлер иштеп берсин)
 *   3. "Bearer" деген сөз туурабы?
 *   4. JWT кыр тамгасы туурабы?
 *   5. Мөөрүтү өткөбү?
 *   6. `sub` ичиндеги id -> маалымат базасынан колдонуучу табылабы
 *   7. Роль -> "ROLE_USER" / "ROLE_ADMIN" укуктар
 *   8. Authentication -> SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Клиент жөнөткөн формат: "Bearer abc.def.ghi" */
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    /** PHASE 10D: 503 jogaby ApiError görnüşinde ýazmaly — şoňa ObjectMapper. */
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository,
                                   ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // (1-2) Токен жок -> СЕБИТ ӨТҮП ЖАЙЫЛАТ. Мисалы /api/auth/register
        //     ачык — ал токенсиз иштеши КЕРЕК. Эгер бул жерде токтотсо,
        //     катталууга кире алмак.
        Optional<String> token = extractToken(request);

        if (token.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 🔴 PHASE 10D: authenticate() indi Exception däl-de, netijäni
            //    aýdýar: PROCEED / INFRASTRUCTURE_FAILURE.
            //    Sebäbi: bazа ýok bolanda 503 ýazmaly, ýöne Exception
            //    transport bu ýerde ÝETMEÝÄR — ol diňe controller
            //    ýatak ýalňyşlary üçin işleýär.
            AuthOutcome outcome = authenticate(token.get(), request, response);

            if (outcome == AuthOutcome.INFRASTRUCTURE_FAILURE) {
                // 🧡 Bu ýerde chain DOWAM EDILMEÝÄR.
                //    Eger ýene bir gezek «filterChain.doFilter(...)»
                //    diýsek, ExceptionTranslationFilter ýalňyşy
                //    tutup, 503 ýerine 401 ýazardy — ýagny ýene şol
                //    ýalňyş semanticsine gaýtaryn.
                return;
            }
        }

        // БААРЫНАН КЕЙИН өткөрөбүз: токен болобосо да, болсо да.
        filterChain.doFilter(request, response);
    }

    /** 🧠 authenticate() — nämäni edip bitti? */
    private enum AuthOutcome {
        /** Token dogry ýa-da «ulanyjy ýok» — chain dowam etsin. */
        PROCEED,
        /** Token dogry, ендема база жеткиликсиз — 503 ýazyldy, chain DUR. */
        INFRASTRUCTURE_FAILURE
    }

    /**
     * (4)-(8) Токенди талдап, колдонуучуну таанытабыз.
     *
     * 🔴 КАТАНЫ ТАПТАБАЙТ — жөн гана «тааныш жок» деп өтөбүз.
     *     Себеби: егер чыныгы катаны чыгарсак, чыгаруучулар токенди
     *     ТАКТАЙ АЛЫП, чоң калымчылыкка (brute force) колдонсо болмок.
     *     Иштегенде SecurityConfig'теги authorizeHttpRequests() 401 берет.
     *
     * 🧡 PHASE 10D: response параметри näme üçin?
     *     «Инфраструктура ýalňyşy» ýagdaýynda bize JOGAP ýazmaly
     *     (503). Eger ýalňyş Exception hökmünde ýok edilse,
     *     ExceptionTransport 401 berýär — ýoňalan ýalňyş.
     */
    private AuthOutcome authenticate(String token,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        try {
            // (4-5) Кыр тамгасы + мөҳрөт текшерилген
            Long userId = jwtService.parseAndGetUserId(token);

            // (6) Маалымат базасында бар экенин текшерүү.
            // 🧠 ЭМНЕ БУ КЕРЕК?
            //     Токен айкөчтүк иштеген учур колдонуучу өчүрүлген
            //     болушу МҮМКҮН (кийинги этапда блок кошобуз). Эгер
            //     колдонуучу жок болсо — биз аны ТААНЫЙ ТОКТОТПАЙТ.
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return AuthOutcome.PROCEED;
            }

            // (7) Роль -> Spring Security «укук» (authority)
            //     "ROLE_" префикси ОБЯЗАТЕЛЫҢ: биз .hasRole("ADMIN")
            //     деп жазабыз, ал "ROLE_ADMIN" деп издейт.
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

            // 🧡 КИЙИНКИ ЭТАПКА: authority'ни JWT'дин `role` claim'ине
            //    которууга болмок (user.getRole() -> claim). Биз маалымат
            //    базасынын ролун колдондук — БУ КИЧТҮРҮК: колдонуучу
            //    күнүнө 15 мүн ичинде USER->ADMIN күтөрүлсө да, эски
            //    токен эч нерсе өзгэрбейт. Роль «эски» болуп калмай.
            var principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // (8) Чыныгы колдонмонуну «коридор»га өткөрмү
            SecurityContextHolder.getContext().setAuthentication(authentication);

            return AuthOutcome.PROCEED;

        } catch (JwtException | IllegalArgumentException ex) {
            // Бул токен ЖАРАТЫЛГАН ЕМЕС. Биз аны өзүбүз жарламайбыз —
            // аны менен иштегенде, егер ыйлап берсек, чыгаруучу чоң
            // калымчылык жасаганда (көп токен тапшыруудан) катанын
            // детальдарын алып, пайда келет.
            logger.debug("Invalid JWT token received: " + ex.getMessage());
            SecurityContextHolder.clearContext();
            return AuthOutcome.PROCEED;
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            // ==========================================================================
            // 🔴 PHASE 10D — BU BLOK FAZANYň OСЯЗЫ: hakym däl «myk» ýerde.
            // ==========================================================================
            //
            // Token DIİE dogry barlanyldy, imza dogry, ýöne:
            //     userRepository.findById(...) → baza el EMEС (postgres öçen)
            //
            // 🔴 BU «taanyý Ûalnyý» EMEС.  «infrastrukturanyň Ûalnyý».
            //    Šonuň üçin biz 401 ÝAZMAУARYS: 401 diýmek
            //    «token nädogry» diýmek bolardy — ol ýalңýñ.
            //
            // 🧡 NÄMÆŞIňİ ýalnyý bolup bilerini AÇYKÇA sanap bilyäris:
            //    • Hibernate ýapyk bolmadyk transaction açyp bilmedi
            //      → CannotCreateTransactionException
            //    • Bağ lanti (connection) alnamadyk
            //      → CannotGetJdbcConnectionException / SQLTransientException
            //    • Ýol ýada-ya-da DNS ýalnyýlygy → SQLNonTransientException
            //    • Hibernate optimistik blokada → PessimisticLockingFailureException
            //    • wagt geçdi           → QueryTimeoutException
            //    BULARIň HӘPSLISI — bäşi «baza ýok» ýada-da
            //    «baza görüp bilmeÝär» diýen masgala degişlidir.
            //
            // 🔴 NÄHILI «EÎLIK» BILEN ÄHLESINI 503 EDIP BOLMAУARYS:
            //    Ýokaryda «Exception.class» diñip ýazsak, şu zatlaryň
            //    hemmesi 503 bolardy:
            //        • NullPointerException (bir ýalnyýlyk kod)
            //        • IllegalStateException (konfigurasiýa näsazlygy)
            //        • programmanyň hatasy → HÄMMESI «baza ýok» diñip
            //          ýalnyý görkeŽilýrdi.
            //    Şeeßle bolsa, haýsy zadyň sebäbini tapmamak mümkin
            //    bolmazdy — monitoring üçin bu PEST. şonuň üçin biz
            //    diİe ÜÇ anyk familiýa sanýarys.
            //
            // 🔴 «TransactionException» NI NÄME ÜÇIN GOŞУARYS?
            //    Bu — 10D-daky iİ önemli tapawut. Hakykata, PHASE 10C-de
            //    PostgreSQL öçen bolanda takmynan şeßle ýalnyý
            //    çykýardy:
            //
            //        org.springframework.transaction.CannotCreateTransactionException:
            //        Could not open JPA EntityManager for transaction
            //
            //    🔴 OL «TransactionException»-dyr, «DataAccessException» EMEС!
            //    Bu ikisi «in garañynda» (sibling) exception-ler: ikisi-de
            //    NestedRuntimeException-den gelip çykýar, ýöne biri
            //    beýlekisiniİ däl.
            //    şonuň üçin diİe «DataAccessException» diñse,
            //    hakyky «база жеткиликсиз» ýagdaýy SÜZÜLP geçýýrdi we
            //    biz ýene şol 401-e düsýrdik — ýagny bu faza
            //    hiç IŞLEMEÝDI bolardy.
            logger.error("Database unavailable while authenticating request "
                            + request.getMethod() + " " + request.getRequestURI(), ex);

            // ⛔ LOG-DA TOKEN/HEADER/PAROL ÝOK:
            //    request.getRequestURI() = «/api/accounts/me» — path,
            //    «Authorization: Bearer eyJ...» däl. Ýalnyý çyrasynda
            //    Exception mesaji bar: ol «Connection to localhost:5432
            //    refused» ýaly bolup biler — bu LOGDA gerek, emma
            //    JOGAPTA däl (indiki setir).
            writeServiceUnavailable(request, response);
            SecurityContextHolder.clearContext();
            return AuthOutcome.INFRASTRUCTURE_FAILURE;
        }
    }

    /**
     * 🔴 503 JOGAP ÝAZУARYS — global ExceptionHandler SYRPAYСMAУAR.
     *
     * 🧡 NÄME ÜÇIN GlobalExceptionHandler BU JAGDAУDA KÖMEK EDIP
     *     BILMEÝÄR?
     *     @RestControllerAdvice diİe CONTROLLERDAN gaýtan ýalnyýy
     *     tutýar. «Servlet.service() for servlet [dispatcherServlet]
     *     threw exception» diñen log şu ýerde — hakyky ýalnyý
     *     FILTER-DA döredildi, controller BARMAУAR. şonuň üçin
     *     biz özümüe ýazmaly.
     *
     * 🧡 NÄME ÜÇIN response.isCommitted() BARLAGY?
     *     Eger jogap ö́nüdenen ýazylan bolsa, ýene ýazmak →
     *     IllegalStateException. Şeßle ýalnyý has çylñyrymly
     *     ýagdaó döredýär. şonuň üçin barýarys.
     */
    private void writeServiceUnavailable(HttpServletRequest request,
                                         HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiError error = ApiError.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "Service temporarily unavailable. Please try again later.",
                request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), error);
    }

    /**
     * (1-3) Headerden «Bearer <token>» alyp, eger dogry format bolsa
     * tокенди ýarýar.
     */
    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
