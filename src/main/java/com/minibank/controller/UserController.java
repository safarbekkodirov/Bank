package com.minibank.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.dto.common.ApiError;
import com.minibank.dto.user.UserResponse;
import com.minibank.security.SecurityUtils;
import com.minibank.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Колдонуучунун өзүнө тиешелүү маалымат.
 *
 * 🧡 «/me» деген эмне?
 *     Биз URL'ге id ЖАЗБАЙТ. Себеби: эгер жазсак, экөөнү окуган
 *     көп кийин «мен башка колдонуучунун маалыматын сурап алам» деп
 *     өтүнүч кыла алабыз. Ал Коопсуздук ТЕМИРИ (IDOR).
 *
 *     «/me» = «МЕН КИММИН?» деген суроо. Жооп — «сен JWT токениңде
 *     жазган ошол колдонмосуң». Биз id'ни URL'ден ЭМЕС, токенден алабыз.
 *     Демек: көрсөткөн id чыныгы колдонуучунун idси менен БИРДИК
 *     болууга МҮМКҮН ЭМЕС — эгер токен туура болсо.
 */
@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User", description = "Токендеги колдонуучунун өз "
        + "маалыматы. URL'де ID ЖАЗЫЛБАЙТ — «/me» деген суроо "
        + "токенден жооп табат (IDOR коргоо).")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/users/me — чыныгы колдонуучунун маалыматы.
     *
     * 🔐 Бул сүрөттөн SecurityConfig аны authenticated деп белгилеген.
     *    Токенсиз келсе — чыпка "тааныш жок" дейт, SecurityConfig
     *    401 JSON жооп берет.
     */
    @GetMapping("/me")
    @Operation(
            summary = "Менин профилим",
            description = """
                    Логин болгон колдонуучунун маалыматын кайтарат.
                    ID токенден алынат — URL'де ID жок.

                    🛡️ Жоопта пароль/пароль хэши ЖОК (тест 20).

                    🔴 `?userId=...` жазып да, башка колдонуучунун
                    маалыматын ала албайт — «мен киммин?» суроосу
                    АЛДА ТОКЕНДЕН чечилет.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Профиль табылды",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок, "
                    + "узак/текширилбеген же мөөрүтү өткөн",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Токендеги "
                    + "колдонуучу базада жок",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserResponse getCurrentUser() {
        // ID токенден (SecurityContext -> principal). URL'ден ЭМЕС.
        Long userId = SecurityUtils.getCurrentUserId();
        return userService.getById(userId);
    }
}
