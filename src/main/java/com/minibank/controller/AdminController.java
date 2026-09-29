package com.minibank.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minibank.dto.admin.AdminAccountResponse;
import com.minibank.dto.admin.AdminUserResponse;
import com.minibank.dto.common.PageResponse;
import com.minibank.dto.transaction.TransactionFilter;
import com.minibank.dto.transaction.TransactionResponse;
import com.minibank.service.AdminService;

/**
 * ADMIN API — diňe okaýan (read-only), PHASE 8.
 *
 * ==========================================================================
 *  🔴 BU CONTROLLER "ROLE_ADMIN" BOLMAÝAN ÝERE BARMALY DÄL
 * ==========================================================================
 *  Bu ýerde role barlagy ÝOK we bolmaly-da däl. Näme üçin?
 *
 *     Bir ýyldan yza öwrüniş: administrator "haýsy endpoint
 *     goragly?" diýip sorap, diňe bu faýly açyp seredi.
 *     Hiç haçan SecurityConfig açmaýar → gorag "görүнбейт" bolup
 *     кетат. Sekiz ay soň täze endpoint goşulup, hasRole
 *     UNUTULÝAR = köpçülik ýagdaýynda esep ýok, duýş ýok,
 *     we oňa islendik USER girip bilýär.
 *
 *     Şonuň üçin ÝALŇYŞ goragy: "/api/admin/**" -> hasRole("ADMIN")
 *     bir ýerde - SecurityConfig-de. Bu faýl ýalnýz "näme görkezýär"
 *     - "kim girýär" diýen soraga jogap DÄL.
 *
 * ==========================================================================
 *  🟡 NÄME BIR CHON CONTROLLER? (7-njy bend)
 * ==========================================================================
 *  Bu 5 endpoint - hemmesi ADMIN - bir faýlda. Sebäbi:
 *     olar ÄHLI bir audit zolakyny guralýar, olar diňe okaýar,
 *     olarda ýeke-täk ýagdaýy ýok. Olary bölseň - 3 faýl, 3
 *     import bloky, 3 konstruktor, ýöne 5 gysga metod.
 *     Bu - "overengineering". Onedir, aýratyn bir faýl dogry.
 *
 *     (PHASE 9-da hasa admin ýazmak başlansa - şonda bölmek
 *     GEREK bolýar: 3 controller, sebäbi olar sany bilen ösýär.)
 *
 * ==========================================================================
 *  🔴 BU FAZADA BU CONTROLLERDE NÄMÄ ÝOK (26/27/28):
 * ==========================================================================
 *     - POST/PUT/DELETE ÝOK. Hiç haçan. Deposit, role üýtgetme,
 *       ýok etme - ählisi bu ýerde ÝOK.
 *     - Mümkin bolan ýagdaýda ýok etmek gerek däp ýazylmadyk.
 *       Onu ýok etmek ýagdaýda bolsa, ýarym ýagdaý çykýar.
 *
 * ==========================================================================
 *  IDOR: BU YERDE "SENIŇ" DÄL, "ŞUNYŇ" DÜZGÜNI (19-njy bend)
 * ==========================================================================
 *     USER:  /api/accounts/me/transactions  -> "BENIŇ" (token)
 *     ADMIN: /api/admin/accounts/{id}/...   -> "ŞUNYŇ" (URL)
 *
 *     19-njy bende görkezilýär: USER A, B-niň esebini soraýar
 *     -> 403 (Security, Service-e ýetmeýär).
 *     ADMIN şol bir sowaly sorag edýär -> 200, sebäbi admin
 *     üçin bu Adalatly. Şu tapawut - RBAC-iň MANYSY.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ======================================================================
    //  USERS
    // ======================================================================

    /**
     * GET /api/admin/users — ähli kollonuwcular.
     *
     * ?page=0&size=20&search=safar
     *
     * 🔒 parol hash HECH QAÇAN çykmaýar (test barlaýar).
     */
    @GetMapping("/users")
    public PageResponse<AdminUserResponse> getAllUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        return adminService.getAllUsers(
                page == null ? 0 : page,
                size == null ? 20 : size,
                search);
    }

    /** GET /api/admin/users/{id} — bir kollonuwcu. Ýok bolsa 404. */
    @GetMapping("/users/{id}")
    public AdminUserResponse getUserById(@PathVariable Long id) {
        return adminService.getUserById(id);
    }

    // ======================================================================
    //  ACCOUNTS
    // ======================================================================

    /**
     * GET /api/admin/accounts — ähli es epler.
     *
     * ?page=0&size=20&userId=5  (userId ýok bolsa - hemmesi)
     */
    @GetMapping("/accounts")
    public PageResponse<AdminAccountResponse> getAllAccounts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long userId) {
        return adminService.getAllAccounts(
                page == null ? 0 : page,
                size == null ? 20 : size,
                userId);
    }

    /** GET /api/admin/accounts/{id} — bir esap (eýesi bilen). */
    @GetMapping("/accounts/{id}")
    public AdminAccountResponse getAccountById(@PathVariable Long id) {
        return adminService.getAccountById(id);
    }

    // ======================================================================
    //  TRANSACTIONS
    // ======================================================================

    /**
     * GET /api/admin/accounts/{accountId}/transactions — esabyň taryhy.
     *
     * ?page=0&size=20&type=TRANSFER_OUT&from=...&to=...
     *  &minAmount=100&maxAmount=1000
     *
     * 🟡 PHASE 7 BILEN PARKI: TransactionFilter we TransactionMapper
     *     göni ulanylýar - täzeden ýazyk ýok. Diňe esap ID-si
     *     URL-den gelýär (admin üçin anyk esap).
     */
    @GetMapping("/accounts/{accountId}/transactions")
    public PageResponse<TransactionResponse> getAccountTransactions(
            @PathVariable Long accountId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {

        TransactionFilter filter = new TransactionFilter(
                page, size, type, from, to, minAmount, maxAmount);
        return adminService.getAccountTransactions(accountId, filter);
    }
}
