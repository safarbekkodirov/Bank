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
import com.minibank.dto.user.UserResponse;
import com.minibank.service.AuthService;
import com.minibank.service.UserService;

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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
