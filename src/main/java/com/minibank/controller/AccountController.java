package com.minibank.controller;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.dto.account.AccountResponse;
import com.minibank.dto.transaction.MoneyOperationRequest;
import com.minibank.dto.transaction.MoneyOperationResponse;
import com.minibank.dto.common.PageResponse;
import com.minibank.dto.transaction.TransactionFilter;
import com.minibank.dto.transaction.TransactionResponse;
import com.minibank.dto.transaction.TransferRequest;
import com.minibank.dto.transaction.TransferResponse;
import com.minibank.security.SecurityUtils;
import com.minibank.service.AccountService;
import com.minibank.service.MoneyService;
import com.minibank.service.TransactionService;
import com.minibank.service.TransferService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;

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
    private final TransferService transferService;
    private final TransactionService transactionService;

    public AccountController(AccountService accountService,
                             MoneyService moneyService,
                             TransferService transferService,
                             TransactionService transactionService) {
        this.accountService = accountService;
        this.moneyService = moneyService;
        this.transferService = transferService;
        this.transactionService = transactionService;
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

    /**
     * POST /api/accounts/me/transfer — менин эсебимден башка эсепке.
     *
     * 🧡 БУЛ ЖЕРДЕ «МЕН КИММИН?» ДЕГЕН СҮРООГо ЖООП БЕРИЛЕТ:
     *     • КАЙНЕК (source) — ТОКЕНДЕН. Эч качан сүрөттөн ЭМЕС.
     *       Чыгаруучу `?accountId=73` же `{"fromAccountId": 73}`
     *       жазып, БАШКАЛАРДЫН АКЧАСЫН АЛЫП КЕТЕ алам деп
     *       аракет кылышы мүмкүн. Биз аны КАБЫЛ АЛБАЙБЫЗ:
     *       чүйлүш талаалары СИЛКИЛЕТ, эсеп токенден табылат.
     *     • КЕЙИНКЕ (destination) — `toAccountNumber` менен. Бул
     *       АЧЫК маалымат: «ушул эсепке котору» — бул чыныгы
     *       которуунун мааниси. БУГУН чүйлүш кылышы МҮМКҮН
     *       (эсептүн ээси өз акчасын каалайт).
     *
     * 🧡 @Valid — сумма 0/терс/4төн көп каттуу -> 400 (базага
     *    тийгүн туруп). Дагы катмар — Service'те.
     *
     * 🧡 200 (201 ЭМЕС): жаңы ресурс ЖАРАЛБАГАН — акча көчтү.
     *
     * 🔴 409 — self-transfer («А → А») же акча жетишпейт.
     * 🔴 404 — destination жок, же колдонуучунун өз эсеби жок.
     * 🛡️ 401 — токенсиз.
     */
    @PostMapping("/me/transfer")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return transferService.transfer(currentUserId, request);
    }

    /**
     * GET /api/accounts/me/transactions - menim taryhym.
     *
     *  BU NEDE "/me/transactions" WE "/{id}/transactions" DÄL?
     *     "Men kimmim?" soragyna "seniň öz taryhň" jogaby.
     *     Eger "/{id}" ýazsaýdy, kollonuwçy 1,2,3... diýip sany
     *     ýazyp, BAŞGALARYŇ taryhyny göre bilirdi (IDOR).
     *     Şonuň üçin ýeke-täk ýol - tokenden.
     *
     *  SORAG PARAMETRLERI (hemmesi ÝASY, defaultly):
     *     page=0    sahypa (0-dan başlaýar)
     *     size=20   sahypada näçe ýazgy (iň ýokary 100)
     *     type      DEPOSIT | WITHDRAW | TRANSFER_IN | TRANSFER_OUT
     *     from      ISO-8601 wagt: 2026-09-01T00:00:00Z (inclusive)
     *     to        ISO-8601 wagt: 2026-09-30T23:59:59Z (inclusive)
     *     minAmount iň az suma
     *     maxAmount iň köp suma
     *
     *  JOGAP KADALARY:
     *     200 - her bitew (sahypa boş bolsa hem!)
     *     400 - page<0, size<1, size>100, nädogry type, from>to
     *     401 - token ýok
     *     404 - kollonuwçynyň esaby ýok
     *
     *  🔴 JOGAPDA NÄMÄ ÝOK? Entity, parol, user obýegi - hiç haçan.
     *  🟡 ORDER BY created_at DESC, id DESC - iň täze birinji we
     *     tertip DETERMINISTIK (belgileri Service-de).
     */
    @GetMapping("/me/transactions")
    public PageResponse<TransactionResponse> getMyTransactions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {

        // 🔴 KIM? - tokeniden. Sýr ötkende "?userId=999" ýazylyp
        //    goýlanda hem biz ÖŇKI kollonuwçynyň taryhyny görýäris.
        Long currentUserId = SecurityUtils.getCurrentUserId();

        TransactionFilter filter =
                new TransactionFilter(page, size, type, from, to, minAmount, maxAmount);

        return transactionService.getMyTransactions(currentUserId, filter);
    }
}
