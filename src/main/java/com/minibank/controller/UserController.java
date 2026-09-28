package com.minibank.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.dto.user.UserResponse;
import com.minibank.security.SecurityUtils;
import com.minibank.service.UserService;

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
    public UserResponse getCurrentUser() {
        // ID токенден (SecurityContext -> principal). URL'ден ЭМЕС.
        Long userId = SecurityUtils.getCurrentUserId();
        return userService.getById(userId);
    }
}
