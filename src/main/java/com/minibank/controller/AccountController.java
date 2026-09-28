package com.minibank.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.dto.account.AccountResponse;
import com.minibank.dto.transaction.MoneyOperationRequest;
import com.minibank.dto.transaction.MoneyOperationResponse;
import com.minibank.security.SecurityUtils;
import com.minibank.service.AccountService;
import com.minibank.service.MoneyService;

import jakarta.validation.Valid;

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
 *
 *   🧡 PHASE 5: мына «/me» астына эки жаңы суроо кошулду —
 *   эсепке акча САЛУУ (deposit) жана ЧЫГАРУУ (withdraw).
 *   🛡️ Экөөсү да ID ЭМЕС колдонушат: ID токенден. Демек IDOR
 *   коркучу азыр дагы да азайды — сүрөткө коюлуучу ОРНО
 *   сөз жок. Чындаган, биз «мен киммин?» деген суроого жооп
 *   берүүн күткөн ЭКИ суроо жазып алдык.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final MoneyService moneyService;

    public AccountController(AccountService accountService, MoneyService moneyService) {
        this.accountService = accountService;
        this.moneyService = moneyService;
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

    /**
     * POST /api/accounts/me/deposit — менин эсебиме акча САЛУУ.
     *
     * 🧡 БУЛ ЖЕРДЕ СУРООНУН ТОЛУК ТАПШЫРМАСЫ ОКУЛУП ЖАТЫР:
     *
     *   1. 🔴 СУРОО `{ "amount": 500.0000, "description": "..." }`
     *      БИЗ ЭЧ КАЧАН accountId, userId АЛМАЙБЫЗ — аларды
     *      SecurityUtils'тан АЛЫП ЖАБЫНАБЫЗ. Сүрөткө
     *      «?userId=53» жазып, башка колдонуучунун эсебине
     *      кирүүгө аракет кылуу — IDOR деп аталат. Бизде
     *      мындай «көчүү» жолун колдонуу үчүн жер БЫЛТЫР
     *      жок: чын эле эсеп ТОКЕНДИКИ колдонуучуга таанча.
     *
     *   2. 🧡 @Valid — СУММАНЫ ТЕКШЕРҮҮ, БАЗАГА КАЙСЫ БҮРҮН.
     *      Мисалы 0, -100, 100.12345 -> БАЗАГА ЖЕТПАЙТ, 400
     *      кесеби. (Дагы катмар: Service'те да текшерүү бар.)
     *
     *   3. 🧡 200 (201 ЭМЕС). Себеби: эсеп ӨЗГӨРДҮ, жаңы ресурс
     *      ЖАРАЛБАГАН. 201 — «эсеп ачтың» үчүн (POST /api/accounts).
     *
     *   4. 🛡️ Жоопто колдонуучунун маалыматы, пароль — ЖОК.
     *      Биз DTO (MoneyOperationResponse) кайтарабыз, Entity ЭМЕС.
     */
    @PostMapping("/me/deposit")
    public MoneyOperationResponse deposit(@Valid @RequestBody MoneyOperationRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return moneyService.deposit(
                currentUserId, request.amount(), request.description());
    }

    /**
     * POST /api/accounts/me/withdraw — менин эсебимден акча ЧЫГАРУУ.
     *
     * 🔴 409 CONFLICT — «калган акча жетишпейт» (InsufficientFunds).
     *    Мис: баланс 650.0000, суралган 1000.0000 -> 409.
     *    🧡 БАЛАНС ТЕРС БОЛУП КАЛБАЙТ — текшерүү Service'те,
     *       DB'де CHECK (balance >= 0) менен да.
     *
     * 🛡️ 401 — токенсиз. 🔴 400 — сумма чоң/терс/4төн көп каттуу.
     *    404 — эсеп жок.
     */
    @PostMapping("/me/withdraw")
    public MoneyOperationResponse withdraw(@Valid @RequestBody MoneyOperationRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return moneyService.withdraw(
                currentUserId, request.amount(), request.description());
    }
}
