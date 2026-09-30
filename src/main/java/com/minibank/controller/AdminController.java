package com.minibank.controller;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.dto.admin.AdminAccountResponse;
import com.minibank.dto.admin.AdminUserResponse;
import com.minibank.dto.common.ApiError;
import com.minibank.dto.common.PageResponse;
import com.minibank.dto.transaction.TransactionFilter;
import com.minibank.dto.transaction.TransactionResponse;
import com.minibank.service.AdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * ADMIN API — гана окуйт (read-only), PHASE 8.
 *
 * ==========================================================================
 *  🔴 БУЛ CONTROLLER «ROLE_ADMIN» БОЛБОГОН ЖЕРГЕ БАРБАШЫ КЕРЕК
 * ==========================================================================
 *  Бул жерде role текшерүү ЖОК жана болбош да керек. Эмне үчүн?
 *
 *     Бир жылдан кийин өзгөрүү: administrator «кайсы endpoint
 *     корголонгон?» деп сурап, гана ушул файлды ачып карайт.
 *     Эч качан SecurityConfig ачпайт -> коргоо «көрүнбөйт» болуп
 *     кетет. Сегиз айдан кийин жаңы endpoint кошулуп, hasRole
 *     УНУТУЛУП КАЛАТ = көпчүлүк абалда эсеп жок, ой жок,
 *     жана ага каалаган USER кире алат.
 *
 *     Ошондуктан ТУУРА коргоо: «/api/admin/**» -> hasRole(«ADMIN»)
 *     бир жерде - SecurityConfig де. Бул файл гана «эмнени көрсөтөт»
 *     - «ким кирет» деген сурууга ЖООП ЭМЕС.
 *
 * ==========================================================================
 *  🟡 ЭМНЕ БИР ЧОҢ CONTROLLER? (7-инчи пункт)
 * ==========================================================================
 *  Бул 5 endpoint - бардыгы ADMIN - бир файлда. Себеби:
 *     алар БААРЫ бир текшеруу чүңгүн курат, алар гана окуйт,
 *     аларда өзгөчө абал ЖОК. Аларды бөлсө - 3 файл, 3
 *     import блоку, 3 конструктор, бирок 5 кыска метод.
 *     Бул - «overengineering». Ошондуктан, айрым файл туура.
 *
 *     (Эгер PHASE 9 да админ ЖАЗУУ баштаса - ошондо бөлүү керек:
 *     3 controller, себеби алар саны менен өсөт.)
 *
 * ==========================================================================
 *  🔴 БУЛ ФАЗАДА БУЛ CONTROLLER ДЕ НЭМЕ ЖОК (26/27/28):
 * ==========================================================================
 *     - POST/PUT/DELETE ЖОК. Дайын жок. Deposit, role өзгөртүү,
 *       өчүрүү - баары бул жерде ЖОК.
 *     - Мүмкүн болгон абалда өчүрүү керек деп ЖАЗЫЛБАГАН.
 *       Аны керексиз кылуу - жарым абал чыгат.
 *
 * ==========================================================================
 *  IDOR: БУЛ ЖЕРДЕ «СЕНИҢ» ЭМЕС, «УШУНУҢ» ЭРЕЖЕСИ (19-инчи пункт)
 * ==========================================================================
 *     USER:  /api/accounts/me/transactions  -> «МЕНИҢ» (токен)
 *     ADMIN: /api/admin/accounts/{id}/...   -> «УШУНУҢ» (URL)
 *
 *     19-инчи пунктта көрсөтүлгөн: USER A, B нин эсебин сурап
 *     жатат -> 403 (Security, Service ге жетпейт).
 *     ADMIN ошол эле суроону жасайт -> 200, себеби admin
 *     үчүн бул АДИЛАТТУУ. Бул айырма - RBAC тин МАНЫСЫ.
 */
@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = """
        🔒 АДМИН API — ROLE_ADMIN ТАЛАП КЫЛАДЫ (`hasRole("ADMIN")`
        SecurityConfig'те). 🔴 ГАНА ОКУЙТ: POST/PUT/DELETE ЖОК.

        Роль кандай иштейт:
          • ROLE_ADMIN  → 200 (уруксат)
          • ROLE_USER   → 403 (маалымат СЫЗБАЙТ)
          • токенсиз   → 401

        🧡 Роль ТОКЕНДЕН ЭМЕС, маалымат базасынан окулат. Ошондуктан
        базадаги рөл өзгөрсө, токен алыш керек — эски токен эски
        укугун сактап калат (тест 8, 9).""")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ======================================================================
    //  USERS
    // ======================================================================

    /**
     * GET /api/admin/users — бардык колдонуучулар.
     *
     * ?page=0&size=20&search=safar
     *
     * 🔒 парол хэш ДАЙЫН чыкпайт (тест барлайт).
     */
    @GetMapping("/users")
    @Operation(
            summary = "Бардык колдонуучулар (админ)",
            description = """
                    🔒 ROLE_ADMIN гана. USER → 403, токенсиз → 401.

                    🧡 `?search=` — аты же эл. почтасы боюнча издөө
                    (бош болсо — баары).

                    🛡️ Пароль хэш ДАЙЫН ЧЫГПАЙТ (тест 15, 20).

                    🟡 Ирет: createdAt DESC, id DESC.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Бет "
                    + "кайтарылды"),
            @ApiResponse(responseCode = "400", description = "page<0, "
                    + "size<1 же size>100",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Роль ROLE_ADMIN "
                    + "ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<AdminUserResponse> getAllUsers(
            @Parameter(description = "Бет номуру (0ден башталат). Бош → 0.",
                    example = "0", schema = @Schema(type = "integer",
                            minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Бет көлөмү (1..100). Бош → 20.",
                    example = "20", schema = @Schema(type = "integer",
                            minimum = "1", maximum = "100",
                            defaultValue = "20"))
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Ат же эл. почтасы боюнча издөө. "
                    + "Бош болсо — баары.", example = "safar")
            @RequestParam(required = false) String search) {
        return adminService.getAllUsers(
                page == null ? 0 : page,
                size == null ? 20 : size,
                search);
    }

    /** GET /api/admin/users/{id} — бир колдонуучу. Жок болсо 404. */
    @GetMapping("/users/{id}")
    @Operation(
            summary = "Бир колдонуучу (админ)",
            description = """
                    🔒 ROLE_ADMIN гана. USER → 403, токенсиз → 401.

                    🛡️ Пароль хэш ЖОК.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Колдонуучу табылды",
                    content = @Content(schema = @Schema(
                            implementation = AdminUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "ID сан ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Роль ROLE_ADMIN "
                    + "ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Мындай колдонуучу "
                    + "ЖОК",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AdminUserResponse getUserById(
            @Parameter(description = "Колдонуучунун ички номуру.",
                    example = "1", required = true)
            @PathVariable Long id) {
        return adminService.getUserById(id);
    }

    // ======================================================================
    //  ACCOUNTS
    // ======================================================================

    /**
     * GET /api/admin/accounts — бардык эсептер.
     *
     * ?page=0&size=20&userId=5  (userId жок болсо - баары)
     */
    @GetMapping("/accounts")
    @Operation(
            summary = "Бардык эсептер (админ)",
            description = """
                    🔒 ROLE_ADMIN гана. USER → 403, токенсиз → 401.

                    🧡 `?userId=` — бир колдонуучунун эсебин гана
                    көрсөтөт. Жок болсо — баары.

                    🛡️ Пароль хэш ЖОК.

                    🟡 Ирет: createdAt DESC, id DESC.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Бет "
                    + "кайтарылды"),
            @ApiResponse(responseCode = "400", description = "page<0, "
                    + "size<1 же size>100, userId сан эмес",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Роль ROLE_ADMIN "
                    + "ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<AdminAccountResponse> getAllAccounts(
            @Parameter(description = "Бет номуру (0ден башталат). Бош → 0.",
                    example = "0", schema = @Schema(type = "integer",
                            minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Бет көлөмү (1..100). Бош → 20.",
                    example = "20", schema = @Schema(type = "integer",
                            minimum = "1", maximum = "100",
                            defaultValue = "20"))
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Бир колдонуучунун эсептерин "
                    + "көрсөтүү. Жок болсо — баары эсептер.", example = "5")
            @RequestParam(required = false) Long userId) {
        return adminService.getAllAccounts(
                page == null ? 0 : page,
                size == null ? 20 : size,
                userId);
    }

    /** GET /api/admin/accounts/{id} — бир эсеп (ээси менен). */
    @GetMapping("/accounts/{id}")
    @Operation(
            summary = "Бир эсеп (админ)",
            description = """
                    🔒 ROLE_ADMIN гана. USER → 403, токенсиз → 401.

                    🧡 Жоопта ээсинин маалыматы бар (`userEmail`,
                    `userFullName`) — админ үчүн «бул кимдики?» суроосу
                    маанилүү. Пароль хэш ЖОК.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Эсеп табылды",
                    content = @Content(schema = @Schema(
                            implementation = AdminAccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "ID сан ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Роль ROLE_ADMIN "
                    + "ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Мындай эсеп ЖОК",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AdminAccountResponse getAccountById(
            @Parameter(description = "Эсептин ички номуру.", example = "1",
                    required = true)
            @PathVariable Long id) {
        return adminService.getAccountById(id);
    }

    // ======================================================================
    //  TRANSACTIONS
    // ======================================================================

    /**
     * GET /api/admin/accounts/{accountId}/transactions — эсептин тарыхы.
     *
     * ?page=0&size=20&type=TRANSFER_OUT&from=...&to=...
     *  &minAmount=100&maxAmount=1000
     *
     * 🟡 PHASE 7 МЕНЕН ПАРКЫ: TransactionFilter жана TransactionMapper
     *     түз колдонулат - жаңыдан жазылбайт. Гана эсеп ID си
     *     URL дан келет (admin үчүн анык эсеп).
     */
    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(
            summary = "Эсептин операциялары (админ)",
            description = """
                    🔒 ROLE_ADMIN гана. USER → 403, токенсиз → 401.

                    🧡 ЭМНЕ USER ЭНДПОИНТИНЕН БАШКАЧА?
                      USER : /api/accounts/me/transactions → «МЕНИҢ»
                             эсеп (ID токенден)
                      ADMIN: ушул жер → «УШУНУҢ» эсеп (ID URL'ден)
                      Ошондуктан админ үчүн бул адил: башкалардын
                      эсебин көрүү АДИЛАТТУУ.

                    🧡 Фильтр параметрлери колдонуучунун
                    `/me/transactions` менен БИРДЕШ:
                    page, size, type, from, to, minAmount, maxAmount.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Бет "
                    + "кайтарылды"),
            @ApiResponse(responseCode = "400", description = "Фильтр "
                    + "параметри туура эмес (page<0, size<1, size>100, "
                    + "type бейтарап, from>to, minAmount>maxAmount)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен жок же "
                    + "жарасyz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Роль ROLE_ADMIN "
                    + "ЭМЕС",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Мындай эсеп ЖОК",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<TransactionResponse> getAccountTransactions(
            @Parameter(description = "Эсептин ички номуру (URL'ден).",
                    example = "1", required = true)
            @PathVariable Long accountId,
            @Parameter(description = "Бет номуру (0ден башталат). Бош → 0.",
                    example = "0", schema = @Schema(type = "integer",
                            minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Бет көлөмү (1..100). Бош → 20.",
                    example = "20", schema = @Schema(type = "integer",
                            minimum = "1", maximum = "100",
                            defaultValue = "20"))
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Операция түрү: DEPOSIT, WITHDRAW, "
                    + "TRANSFER_IN, TRANSFER_OUT (ЧОҢ тамга). Бош → "
                    + "баары.", example = "TRANSFER_OUT",
                    schema = @Schema(type = "string",
                            allowableValues = {"DEPOSIT", "WITHDRAW",
                                    "TRANSFER_IN", "TRANSFER_OUT"}))
            @RequestParam(required = false) String type,
            @Parameter(description = "Башлангы убакыт, ISO-8601, киргизилүү.",
                    example = "2026-09-01T00:00:00Z",
                    schema = @Schema(type = "string", format = "date-time"))
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Аякталган убакыт, ISO-8601, "
                    + "киргизилүү.", example = "2026-09-30T23:59:59Z",
                    schema = @Schema(type = "string", format = "date-time"))
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Эң аз сумма, киргизилүү.",
                    example = "100")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Эң көп сумма, киргизилүү.",
                    example = "1000")
            @RequestParam(required = false) BigDecimal maxAmount) {

        TransactionFilter filter = new TransactionFilter(
                page, size, type, from, to, minAmount, maxAmount);
        return adminService.getAccountTransactions(accountId, filter);
    }
}
