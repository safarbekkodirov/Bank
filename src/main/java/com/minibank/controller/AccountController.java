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
import com.minibank.dto.common.ApiError;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Account", description = "Банк эсеби, акча салуу/чыгаруу, "
        + "эсептен эсепке которуу жана операция тарыхы. 🛡️ БААРЫ "
        + "«/me» аркылуу иштейт: эсеп IDси СЫРТТАН КАБЫЛ АЛЫНБАЙТ, "
        + "токенден алынат — ошондуктан IDOR коркучу жок.")
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
    @Operation(
            summary = "Эсеп ачуу",
            description = """
                    Токендеги колдонуучуга жаңы банк эсебин ачат.

                    🧡 Request body ТАЛАП КЫЛЫНБАЙТ — эсептин маалыматы
                    колдонуучуга ЭМЕС, ТОКЕНге жараша түзүлөт. Валюта эр
                    жерде «RUB», баланс «0.0000».

                    🔴 Бизнес-эреже: бир колдонуучуга БИР эсеп. Эгер
                    эсеп мурунтан болсо → 409.

                    🛡️ Телото күйсүн `{"balance": 1000000}` — ЖОК.
                    Сервер мындай талааны көрбөйт, чын эсеп токенге
                    жараша болот (тест 11).""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Эсеп ачылды",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Токендеги "
                    + "колдонуучу базада жок",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Бу колдонуучунун "
                    + "эсепи МУРУНТАН БАР",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
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
    @Operation(
            summary = "Менин эсебим",
            description = """
                    Токендеги колдонуучунун эсебин жана УЧУРДАГЫ
                    балансын кайтарат.

                    🔴 Эгер эсеп ачыла элек болсо → 404 (маалымат
                    жок — бул КАТА эмес, абал).""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Эсеп табылды",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Бу колдонуучунун "
                    + "эсеби ЖОК (ачуу керек)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
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
    @Operation(
            summary = "Акча салуу (deposit)",
            description = """
                    Менин эсебиме акча САЛЫНАДЫ (баланс өсөт).

                    🧡 Суроо: `{"amount": 1000, "description": "..."}`.
                    Эсеп IDси СУРООДА ЖОК — эсеп токенден алынат.
                    `?userId=` же `{"accountId":}` жазсан да, башка эсепке
                    өтө албайт (тест 12, 13).

                    🧡 Сумма: жок болбош керек, 0ден ЧОҢ, ондук бөлүгү
                    4төн ашпаш керек. Башкача болсо → 400.

                    🧡 200 (201 ЭМЕС): эсеп ӨЗГӨРДҮ, жаңы ресурс
                    ЖАРАЛБАГАН. 201 болсо — «эсеп ачтың» үчүн
                    (POST /api/accounts).""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Акча салынды, "
                    + "жаңы баланс кайтарылды",
                    content = @Content(schema = @Schema(implementation = MoneyOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Сумма жок/0/"
                    + "терс/4төн көп ондук бөлүктү",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Бу колдонуучунун "
                    + "эсеби ЖОК",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "415", description = "Content-Type "
                    + "application/json ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
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
    @Operation(
            summary = "Акча чыгаруу (withdraw)",
            description = """
                    Менин эсебимден акча ЧЫГАРЫЛАДЫ (баланс азаят).

                    🔴 409 CONFLICT — «калган акча жетишпейт». Мисал:
                    баланс 650, суралган 1000 → 409 жана баланс
                    ӨЗГӨРМӨЙТ.

                    🧡 БАЛАНС ТЕРС БОЛУП КАЛБАЙТ: Service'те текшерүү +
                    маалымат базасында CHECK (balance >= 0). Конкурент
                    чыгарууларда да (тест 30 параллель withdraw).

                    🛡️ Жоопта `amount` ТЕГИН (+) көрүнөт: чыгарулган
                    100 деп жазылат, минус белгиси жок.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Акча чыгарылды, "
                    + "жаңы баланс кайтарылды",
                    content = @Content(schema = @Schema(implementation = MoneyOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Сумма жок/0/"
                    + "терс/4төн көп ондук бөлүктү",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Бу колдонуучунун "
                    + "эсеби ЖОК",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Эсептеги "
                    + "акча ЖЕТИШПЕЙТ (баланс өзгөрбөйт)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "415", description = "Content-Type "
                    + "application/json ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
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
    @Operation(
            summary = "Акча которуу (transfer)",
            description = """
                    Биздин эсебимден БАШКА эсепке акча которуу.

                    🧡 «МЕН КИММИН?» СУРООСУ:
                      • КАЙНАК (from) — ТОКЕНДЕН. Эч качан сүрөттөн
                        ЭМЕС. `?accountId=` же `{"fromAccountId":}` жазуу
                        мүмкүн ЭМЕС — алар кабыл алынбайт.
                      • КЕЙИНКИ (to) — `toAccountNumber` менен (18 цифра).
                        Бул АЧЫК маалымат: «ушул эсепке котору» — ушул
                        чыныгы которуунун мааниси.

                    🧡 200 (201 ЭМЕС): жаңы ресурс ЖАРАЛБАГАН, акча
                    көчтү.

                    🔴 409 — өз эсебиңе өзүң которууң («А → А») же
                    акча жетишпейт.
                    🔴 404 — кейинки эсеп жок, же өз эсебиң жок.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Акча которулду, "
                    + "эки эсептин жаңы абалы кайтарылды",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Сумма же "
                    + "эсеп номуру туура эмес (номур так 18 цифра "
                    + "болушу керек)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Кейинки эсеп же "
                    + "колдонуучунун өз эсеби ЖОК",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Акча жетишпейт "
                    + "же өз эсебиңе өзүң которуң",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "415", description = "Content-Type "
                    + "application/json ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
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
    @Operation(
            summary = "Менин операцияларым",
            description = """
                    Токендеги колдонуучунун операция тарыхы — бет
                    боюнча, фильтр менен.

                    🛡️ `?userId=` жазып да, башка колдонуучунун тарыхын
                    АЛА АЛБАЙСЫН: «мен киммин?» суроосу АЛДА ТОКЕНДЕН
                    чечилет (тест 13).

                    🟡 Ирет: createdAt DESC, id DESC (эң жаңа биринчи) —
                    ТАТИМДҮҮ (бир убакытта эки жазуу болсо да, ирет
                    ар дайым бирдей).

                    🔴 ЖООП БОШ БОЛСО ДА 200: `content: []`,
                    `totalElements: 0`. Бул ката ЭМЕС.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Бет "
                    + "кайтарылды (бос бет болсо да 200)"),
            @ApiResponse(responseCode = "400", description = "Фильтр "
                    + "параметри туура эмес: page<0, size<1, size>100, "
                    + "type бейтарап, from>to, minAmount>maxAmount",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Бу колдонуучунун "
                    + "эсеби ЖОК",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<TransactionResponse> getMyTransactions(
            @Parameter(description = """
                    Беттин номуру, 0ден башталат. Мисал: ?page=2
                    Бош болсо → 0. page < 0 болсо → 400.""",
                    example = "0", schema = @Schema(type = "integer",
                            minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) Integer page,
            @Parameter(description = """
                    Бир беттеги жазуулардын саны. 1..100.
                    Бош болсо → 20. 1den аз же 100ден көп болсо → 400
                    (DOS коргоо).""",
                    example = "20", schema = @Schema(type = "integer",
                            minimum = "1", maximum = "100",
                            defaultValue = "20"))
            @RequestParam(required = false) Integer size,
            @Parameter(description = """
                    Операция түрү. Кийинки 4түнүн бири: DEPOSIT,
                    WITHDRAW, TRANSFER_IN, TRANSFER_OUT.
                    🧡 ЧОҢ тамга МАЖБУРИЙ (case-sensitive): «deposit»
                    → 400. Бош болсо → фильтр ЖОК (баары).""",
                    example = "DEPOSIT",
                    schema = @Schema(type = "string",
                            allowableValues = {"DEPOSIT", "WITHDRAW",
                                    "TRANSFER_IN", "TRANSFER_OUT"}))
            @RequestParam(required = false) String type,
            @Parameter(description = """
                    Башлангы убакыт, ISO-8601 (UTC, «Z» менен).
                    Мисал: ?from=2026-09-01T00:00:00Z
                    Киргизилүү (баш тарабы кошо). Формат башка болсо →
                    400.""",
                    example = "2026-09-01T00:00:00Z",
                    schema = @Schema(type = "string", format = "date-time"))
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = """
                    Аякталган убакыт, ISO-8601 (UTC, «Z» менен).
                    Мисал: ?to=2026-09-30T23:59:59Z
                    Киргизилүү. from > to болсо → 400.""",
                    example = "2026-09-30T23:59:59Z",
                    schema = @Schema(type = "string", format = "date-time"))
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = """
                    Эң аз сумма, киргизилүү. Мисал: ?minAmount=100
                    minAmount > maxAmount болсо → 400.""",
                    example = "100")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = """
                    Эң көп сумма, киргизилүү. Мисал: ?maxAmount=1000""",
                    example = "1000")
            @RequestParam(required = false) BigDecimal maxAmount) {

        // 🔴 KIM? - tokeniden. Sýr ötkende "?userId=999" ýazylyp
        //    goýlanda hem biz ÖŇKI kollonuwçynyň taryhyny görýäris.
        Long currentUserId = SecurityUtils.getCurrentUserId();

        TransactionFilter filter =
                new TransactionFilter(page, size, type, from, to, minAmount, maxAmount);

        return transactionService.getMyTransactions(currentUserId, filter);
    }
}
