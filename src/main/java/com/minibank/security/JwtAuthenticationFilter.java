package com.minibank.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.minibank.entity.User;
import com.minibank.repository.UserRepository;

import io.jsonwebtoken.JwtException;
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

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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
            authenticate(token.get(), request);
        }

        // БААРЫНАН КЕЙИН өткөрөбүз: токен болобосо да, болсо да.
        filterChain.doFilter(request, response);
    }

    /**
     * (4)-(8) Токенди талдап, колдонуучуну таанытабыз.
     *
     * 🔴 КАТАНЫ ТАПТАБАЙТ — жөн гана «тааныш жок» деп өтөбүз.
     *     Себеби: эгер чыныгы катаны чыгарсак, чыгаруучулар токенди
     *     ТАКТАЙ АЛЫП, чоң калымчылыкка (brute force) колдонсо болмок.
     *     Иштегенде SecurityConfig'теги authorizeHttpRequests() 401 берет.
     */
    private void authenticate(String token, HttpServletRequest request) {
        try {
            // (4-5) Кыр тамгасы + мөөрүт текшерилген
            Long userId = jwtService.parseAndGetUserId(token);

            // (6) Маалымат базасында бар экенин текшерүү.
            // 🧠 ЭМНЕ БУЛ КЕРЕК?
            //     Токен айкөчтүк иштеген учур колдонуучу өчүрүлгөн
            //     болушу МҮМКҮН (кийинги этапда блок кошобуз). Эгер
            //     колдонуучу жок болсо — биз аны ТААНЫЙ ТОКТОТПАЙТ.
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }

            // (7) Роль -> Spring Security «укук» (authority)
            //     "ROLE_" префикси ОБЯЗАТЕЛДУУ: биз .hasRole("ADMIN")
            //     деп жазабыз, ал "ROLE_ADMIN" деп издейт.
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

            // 🧡 КИЙИНКИ ЭТАПКА: authority'ни JWT'дин `role` claim'ине
            //    которууга болот (user.getRole() -> claim). Биз маалымат
            //    базасынын ролун колдондук — БУЛ КЫЧТУУРАК: колдонуучу
            //    күнүнө 15 мүн ичинде USER->ADMIN көтөрүлсө да, эски
            //    токен эч нерсе өзгөрбөйт. Роль «эски» болуп калбайт.
            var principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // (8) Чыныгы колдонмонуну «коридор»га өткөрмү
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException ex) {
            // Бул токен ЖАРАТЫЛГАН ЭМЕС. Бирок биз аны өзүбүз жарламайбыз —
            // аны менен иштегенде, эгер ыйлап берсек, чыгаруучу чоң
            // калымчылык жасаганда (көп токен тапшыруудан) катанын
            // детальдарын алып, пайда келет.
            logger.debug("Invalid JWT token received: " + ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * (1-3) Headerден «Bearer <token>» алып, эгер туура формат болсо
     * токенди чыгарат.
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
