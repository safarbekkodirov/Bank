package com.minibank.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.dto.admin.AdminAccountResponse;
import com.minibank.dto.admin.AdminUserResponse;
import com.minibank.dto.common.PageResponse;
import com.minibank.dto.transaction.TransactionFilter;
import com.minibank.dto.transaction.TransactionResponse;
import com.minibank.entity.Account;
import com.minibank.entity.User;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InvalidFilterException;
import com.minibank.exception.UserNotFoundException;
import com.minibank.mapper.AdminMapper;
import com.minibank.mapper.TransactionMapper;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;
import com.minibank.repository.UserRepository;

/**
 * ADMIN MAGLUMATLARY - diňe OKAÝAN (read-only, PHASE 8).
 *
 * ==========================================================================
 *  🔴 IŇ MÖHÜM EREJE: BU SERVICE NÄME "ADMIÑ" DIÝIP BILMEÝÄR!
 * ==========================================================================
 *  Biz bu ýerde role barlamak ÝOK. Näme üçin?
 *
 *     Ýol bir: role barlagy SERVICE-de ("if (!isAdmin) throw...").
 *     → ÝALŇYŞ. Sebäbi: bu diňe bir "gözcü" — gorag bu gapdan
 *       ötüp barylsa (mysal üçin köp controller, ýa-da täze bir
 *       ýerde bu service ulanylsa), gorag ýitýär. Gorag "bir ýerde"
 *       bolmaly, "her ýerde" bolmaly däl.
 *
 *     Ýol iki: role barlagy SECURITYConfig-de (hasRole("ADMIN")).
 *     → DOGRY. Sebäbi: bu ÝOLARYŇ GIRËŞI. Sözbaşydan öňürti
 *       Controller-e ýetip ýetmän, eger ADMIN däl bolsa - 403.
 *       Service-e girmek mümkün bolmaz, şonuň üçin service-de
 *       gaýtadan barlamagyň GEREGI ÝOK.
 *
 *  Şonuň üçin: Security = hasRole. Service = diňe köçürme.
 *  Eki gatlagyň biri üstüne biri: Security (kim) + Service (näme).
 *
 * ==========================================================================
 *  🟡 IKKI SECURITY MODEL ARAŞMAÝAR (18-njy bend):
 * ==========================================================================
 *     USER  -> /api/accounts/me/transactions
 *          -> "BENIŇ esebim" - esep ID-si TOKENDEN alynýar.
 *          -> TransactionService.getMyTransactions(userId, filter)
 *
 *     ADMIN -> /api/admin/accounts/{accountId}/transactions
 *          -> "ŞU esebiň taryhy" - esep ID-si URL-den Alynýar.
 *          -> AdminService.getAccountTransactions(accountId, filter)
 *
 *     Bu ikini BIR Service-de birleşdirsek - kimme haýsy esep
 *     görkezilýändigi anlaýsyz bolup gider. Bu - köp mümkin bolan
 *     ýalňyş (bir ýerde ID aldak, bir ýerde okadak). Şonuň üçin
 *     AÝRY metot. Reuse bar (Specification, filter, sort),
 *     ýöne güýçli meýdan (security) AÝRY.
 *
 * ==========================================================================
 *  🔴 BU FAZADA JAZMA ÝOK (26/27/28-njy bender):
 * ==========================================================================
 *     - deposit / withdraw / transfer üçin admin endpoint ÝOK
 *       (akça diňe esebiň öz eýesi tarapyndan hereketlenýär)
 *     - role üýtgetme ÝOK (privilege escalation howpy)
 *     - delete user / account ÝOK (ON DELETE RESTRICT)
 *
 *     Bu faza DIŇE OKAÝAN administrasiýa. Ýazyjak endpoint üçin
 *     ayratyn düşündiriş we ýana kada talap edilýär.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AdminMapper adminMapper;
    private final TransactionMapper transactionMapper;

    public AdminService(UserRepository userRepository,
                        AccountRepository accountRepository,
                        TransactionRepository transactionRepository,
                        AdminMapper adminMapper,
                        TransactionMapper transactionMapper) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.adminMapper = adminMapper;
        this.transactionMapper = transactionMapper;
    }

    // ======================================================================
    //  USERS
    // ======================================================================

    /**
     * BARLYK KOLLONUWCULAR - sahypa sahypa, gysgaýy gözleg bilen.
     *
     * @param page   sahypa (0-den)
     * @param size   sahypa göwrümi (1..100)
     * @param search "safar" ýaly - at ýa-da email boýunça (optional)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAllUsers(Integer page, Integer size, String search) {

        validatePagination(page, size);

        // 🧡 SORT: createdAt DESC, id DESC (PHASE 7-yň usuly - BIR
        //    standart). Kim "sort=passwordHash" diýip soralsa -
        //    Parameter däl, biz ynamasýarys (SQL injection goragy).
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        Page<User> result = userRepository.findAll(
                buildUserSpecification(search),
                PageRequest.of(page, size, sort));

        return PageResponse.of(result, adminMapper::toAdminUserResponse);
    }

    /**
     * Gözleg: fullName ýa-da email boýunça, harpuly DÖLDIRMEÝÄR.
     *
     * 🧡 NÄME ÜÇIN lower? "Safar" bilen gözleýärsin - "safar" da
     *     gutarýar. Sebäbi: howpsuzlyk birinji orunda, pastdan
     *     uly harply ulanyp gözlemek amaly köpüülöy. SQLite/PostgreSQL
     *     "ILIKE" diýip şeýle gözlegi gönüden-göni goldaýar.
     *
     * 🟡 SQLI GORAGY: ilike() FAYLANS, биз ýazýarys - ulanyjaýyn
     *     ýazgy bir ýere goşulýar, SQL-iň içine gönülde göçürülmeýär.
     */
    private Specification<User> buildUserSpecification(String search) {

        if (search == null || search.isBlank()) {
            return null;   // hili filter ýok - ählisi
        }

        String pattern = "%" + search.trim().toLowerCase() + "%";

        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
            ));
            return cb.and(predicates.toArray(
                    new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** Bir kollonuwcu - id boýunça. Ýok bolsa 404. */
    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(
                        "User with id " + id + " not found"));
        return adminMapper.toAdminUserResponse(user);
    }

    // ======================================================================
    //  ACCOUNTS
    // ======================================================================

    /**
     * BARLYK ESEPLER - ýa-da aýratyn bir ýeňişçiniň esebi.
     *
     * @param userId optional: "bu kollonuwçynyň esebi" (null = hemmesi)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminAccountResponse> getAllAccounts(
            Integer page, Integer size, Long userId) {

        validatePagination(page, size);

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        Specification<Account> spec = (userId == null)
                ? null
                : (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);

        // 🔴 findAllWithUser: JOIN FETCH - user maglumaty BIR soragda.
        //    Specification bilen işletsek, biz ýöne findAll(Spec, Pageable)
        //    ulanypdym - ol aýratyn sorag ýaratýar (join ýok).
        //    Şonuň üçin iki ýagdaýy aýry-aýry handle edýäris.
        Page<Account> result = (spec == null)
                ? accountRepository.findAllWithUser(PageRequest.of(page, size, sort))
                : accountRepository.findAll(spec, PageRequest.of(page, size, sort));

        return PageResponse.of(result, adminMapper::toAdminAccountResponse);
    }

    /** Bir esep - id boýunça (user maglumaty bilen). Ýok bolsa 404. */
    @Transactional(readOnly = true)
    public AdminAccountResponse getAccountById(Long id) {
        Account account = accountRepository.findByIdWithUser(id)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account with id " + id + " not found"));
        return adminMapper.toAdminAccountResponse(account);
    }

    // ======================================================================
    //  TRANSACTIONS (any esabyň taryhy)
    // ======================================================================

    /**
     * ESABYŇ TARHYHY - admin üçin, islendik esap.
     *
     * 🟡 ÖNKI SERVICE BILEN PARKI:
     *     TransactionService.getMyTransactions(userId, filter) -
     *     ol ESABYŇ ID-sini TOKENDEN alýar we özü tapýar.
     *     Bu ýerde admin esaby AÇYP BERÝÄR (URL-den). Ýagny:
     *         bir ýerde "benim esebim", ýokaryda "şu esep".
     *
     * 🧡 REUSE: sort we filter (TransactionFilter) - olaryň ýarysy
     *     ulanylýar. TransactionMapper - göni ulanylýar. Specification
     *     gural ýöne ýokaryda takyk ýazylan (esep id öňünden belli).
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAccountTransactions(
            Long accountId, TransactionFilter filter) {

        // (1) Esep bar bolsa? Ýoksa 404 (takyk jogap, "gatyşyk" däl).
        //     🧡 MÖHÜM: bu yzarlamak bilen emele gelýär. Sebäbi
        //     esep ýok bolsa aşakdaky Specification "0 ýazgy" gaýtaryp
        //     bilerdi - klient "200, ýazgy ýok" diýip gülümserdi,
        //     ýöne sebäbini anlamazdy. 404 - takyk.
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account with id " + accountId + " not found"));

        // (2) PHASE 7-yň filterini doly validasiýa et (bir kada!).
        validateTransactionFilter(filter);

        // (3) Specification - esap sherti MÖHÜM (esap id şu ýerde).
        Specification<com.minibank.entity.Transaction> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("account").get("id"), accountId));

            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"),
                        com.minibank.entity.TransactionType.valueOf(filter.type())));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        // (4) BIRMEŇEŞ tertip PHASE 7 bilen: createdAt DESC, id DESC.
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        Page<com.minibank.entity.Transaction> result = transactionRepository.findAll(
                spec, PageRequest.of(filter.page(), filter.size(), sort));

        return PageResponse.of(result, transactionMapper::toResponse);
    }

    // ======================================================================
    //  ORTAK VALIDASIÝA
    // ======================================================================

    /**
     * Sahypa parametrleri - USER hem, ADMIN üçin bir kada.
     *
     * 🧡 NÄME BU Service-de däl-de Controller-de näme däl?
     *     Service-de - sebäbi bu "içki köçürme": test, kelleýinji
     *     endpoint ýa-da klientiň içki kodu hem çagyryp bilýär.
     *     Her ýerde bir kada tutsak ol kada ÝOGSAÝAR.
     */
    private void validatePagination(Integer page, Integer size) {
        if (page < 0) {
            throw new InvalidFilterException("page must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new InvalidFilterException("size must be at least 1");
        }
        if (size > TransactionFilter.MAX_SIZE) {
            throw new InvalidFilterException(
                    "size must not be greater than " + TransactionFilter.MAX_SIZE);
        }
    }

    /**
     * Transaction filter - PHASE 7-daky bilen BIRMEŇEŞ kada.
     *
     * 🧡 BUL DUPLICATION DÄL - bu BILINTLI. Iki Service-de bir
     *     ýagdaýy köçürmek gowy däl - birini üýtgertseň, ikincisi
     *     galan (sirli: "bir kada ýerinde" däl bolýar). Ýöne şu
     *     ýerde ýönekeý 4 hat görkezilýär. Geljekki fazada
     *     olary BIR klassa çykarmak dogry bolar - emma indi
     *     şeýle abstraction talap edilmedi (42-njy bend).
     */
    private void validateTransactionFilter(TransactionFilter filter) {
        if (filter.page() < 0) {
            throw new InvalidFilterException("page must be greater than or equal to 0");
        }
        if (filter.size() < 1) {
            throw new InvalidFilterException("size must be at least 1");
        }
        if (filter.size() > TransactionFilter.MAX_SIZE) {
            throw new InvalidFilterException(
                    "size must not be greater than " + TransactionFilter.MAX_SIZE);
        }
        if (filter.type() != null) {
            try {
                com.minibank.entity.TransactionType.valueOf(filter.type());
            } catch (IllegalArgumentException ex) {
                throw new InvalidFilterException(
                        "Invalid transaction type: " + filter.type());
            }
        }
        if (filter.from() != null && filter.to() != null
                && filter.from().isAfter(filter.to())) {
            throw new InvalidFilterException("from must be before or equal to to");
        }
        if (filter.minAmount() != null && filter.maxAmount() != null
                && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            throw new InvalidFilterException(
                    "minAmount must be less than or equal to maxAmount");
        }
    }
}
