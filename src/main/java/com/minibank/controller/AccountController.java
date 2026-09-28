package com.minibank.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.dto.account.AccountResponse;
import com.minibank.security.SecurityUtils;
import com.minibank.service.AccountService;

/**
 * Банк эсептеринин REST APIсы.
 *
 * 🛡️ БУЛ ЖЕРДИН ЭҢ МААНИЛҮҮ ОЙУ — «КӨЗГӨ ЧЫГАРБАЙТЫЗ»:
 *
 *   Биз ачык эмес эндпоинт жазабыз. Жазылганы:
 *       GET /api/accounts/{id}          ❌ ЖАЗБАЙТ
 *       GET /api/accounts?userId=53     ❌ ЖАЗБАЙТ
 *       GET /api/accounts/me            ✅ МЫНА БУ
 *
 *   Себеби: эгер {id} эндпоинти болсо, колдонуучу чын эле 1, 2, 3...
 *   сандарды маалымат базасына «малдап» көрүп, БАШКАЛАРДЫН эсебин
 *   таап алар эле. Бизге кыз: чыныгы эсептер ЖОК болсо, эле
 *   «сен 1-айлампага кирдиң, бул 2-адамдын акчасы» деп чыгарабыз.
 *
 *   «/me» = «мен киммин?» деген суроо. Жооп — «сен өзүңдүн гана
 *   эсебиңди көрөсүң». ID сүрөттөн ЭМЕС, токенден келилет.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * POST /api/accounts — эсеп ачуу.
     *
     * 🧠 Request body ТАЛАП КЫЛЫНБАЙТ: эсептин маалыматы колдонуучуга
     *    эмес, ТОКЕНге жараша түзүлөт. Валюта эр жерде RUB.
     *
     * 🔴 401 — токен жок болсо SecurityConfig тоқтотот (Controller'ге
     *    жетпейт). Мына бул «иш көрүү» дегенди кийинки
     *    телефондогу жазууларда көрсөтөм.
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount() {
        // ID токенден, колдонуучу эмес, чалбайбыз
        Long currentUserId = SecurityUtils.getCurrentUserId();
        AccountResponse response = accountService.createAccount(currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/accounts/me — менин эсебим.
     *
     * 200 — эсеп бар
     * 404 — эсеп жок (бизнес-эреже, GlobalExceptionHandler)
     * 401 — токенсиз
     */
    @GetMapping("/me")
    public AccountResponse getMyAccount() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return accountService.getMyAccount(currentUserId);
    }
}
