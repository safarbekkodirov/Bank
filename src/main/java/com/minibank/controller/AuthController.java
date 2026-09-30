package com.minibank.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.dto.auth.AuthResponse;
import com.minibank.dto.auth.LoginRequest;
import com.minibank.dto.auth.RegisterRequest;
import com.minibank.dto.common.ApiError;
import com.minibank.dto.user.UserResponse;
import com.minibank.service.AuthService;
import com.minibank.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * REST APIнын "киребергис дүргөсү".
 *
 * 🧠 Controller деген эмне?
 *   Клиент (браузер, телефон) HTTP сүрөт жөнөтөт -> Spring аны ушул
 *   класстын методуна таштайт. Биздин иш:
 *     1. JSONды DTOго айкөкчү кылуу
 *     2. @Valid менен текшерүү
 *     3. Service кечирүү
 *     4. Жоопту HTTP катарын коюп жөнөтүү
 *   БАШКАСЫ ЖОК. Бизнес-логика (@Transactional, SQL) ушул жерде
 *   ЭМЕС — ал Service'те. Себеби: эгер бизнес-логика Controller болсо,
 *   экинчи бир "ачакта" жасаганда, эркинди көчүрүп коюш керек болмок.
 *
 *   🧠 ENTI ≠ DTO: Биз параметрге User ЭМЕС RegisterRequest алабыз,
 *   жоопту User ЭМЕС UserResponse кайтарабыз. Себеби: эгер биз Entity
 *   алсак, бир күн эсепке password_hash кошулганда — ошол талаа
 *   АВТОМАТТЫК раск қилинет (JSONга кошо жөнөтүлөт) жана күтпөгөн
 *   жерден чыгат. DTO — бул "көзөгө көрүнгөн талаалардын ТИЗИМИ".
 */
@RestController
@RequestMapping("/api/auth")
@SecurityRequirements   // 🟡 Бүт AuthController ТОКЕНСИЗ иштейт
@Tag(name = "Auth", description = "Катталуу жана логин. Бул эндпоинттер "
        + "АЧЫК (permitAll) — токен керек ЭМЕС, алар токен БЕРЭТ.")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    /**
     * POST /api/auth/register — катталуу
     *
     * @Valid = DTO дагы анотацияларын (@NotBlank, @Email, @Size)
     *         ТЕКШЕР. Эгер ката болсо, Spring өзү 400 менен токтотот
     *         жана биз GlobalExceptionHandlerге жөнөтөт.
     * @RequestBody = Сүрөттөн келген JSONды RegisterRequest объектисине
     *                (Jackson автоматтык жасайт).
     */
    @PostMapping("/register")
    @Operation(
            summary = "Катталуу",
            description = """
                    Жаңы колдонуучу жаратады. Эсеп АЧЫЛБАЙТ — аны
                    үчүнчү кадамда `POST /api/accounts` керек.

                    🛡️ Роль СУРОГДО КАБЫЛ КЫЛЫНБАЙТ: `{"role": "ADMIN"}`
                    жазсан да, колдонуучу `USER` болуп кала берет (тест 10).

                    🔐 Жоопта пароль ЖОК — BCrypt хэш сакталат, эч качан
                    кайтпайт.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Колдонуучу жаратылды",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Валидация өтпөдү "
                    + "(fullName/email/password бош, email форматсыз, "
                    + "пароль 8ден аз) же JSON бузулган",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Бу email БУГУНАН "
                    + "кийин катталган",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "415", description = "Content-Type "
                    + "application/json ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        // 201 Created = "Жаңы нерсе жаратылды" (анын дареси бар).
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login — логин
     *
     * 200 OK  = токен алынды
     * 400     = каталуу маалымат (email жаман, пароль бош)
     * 401     = email же пароль туура эмес
     *
     * 🔴 БУЛ ENDPOINT АЧЫК (permitAll) — токен керек ЭМЕС. Анда
     *    токен алышат. SecurityConfig'теги authorizeHttpRequests()
     *    аны ошондой жазып калган.
     */
    @PostMapping("/login")
    @Operation(
            summary = "Логин",
            description = """
                    Email + пароль → JWT токен.

                    🧡 `accessToken` маанисин `Authorization: Bearer <token>`
                    header'инде жиберип, бардык корголгон endpoint'терди
                    иштесе болот. Swagger UI'да: «Authorize» → ушул токен.

                    🔴 Токен мөөрүтү 15 минут (900 секунд). Анан токен
                    ЖАРАКСЫЗ болот жана КАЙТА логин керек (refresh токени
                    ЖОК — өзгөртүүлбөйт).

                    🔴 Email же пароль туура эмес болсо — 401 БИРдей
                    билдирүү менен. «Бул email жок» деп айтсак, чыгаруучу
                    эсептеринин бар экенин таап алар эле (user enumeration).""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Токен алынды",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Валидация өтпөдү "
                    + "(email бош/форматсыз, пароль бош) же JSON бузулган",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Email же пароль "
                    + "туура эмес",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "415", description = "Content-Type "
                    + "application/json ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
