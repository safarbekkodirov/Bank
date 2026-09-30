package com.minibank.service;

import java.time.Instant;
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
 * ADMIN МААЛЫМАТТАРЫ - гана ОКУЙТ (read-only, PHASE 8).
 *
 * ==========================================================================
 *  🔴 ЭҢ МАНИЛҮҮ ЭРЕЖЕ: БУЛ SERVICE «АДМИН» ДЕП БИЛБЕЙТ!
 * ==========================================================================
 *  Биз бул жерде role текшербеймиз. Эмне үчүн?
 *
 *     Бир жол: role текшерүү SERVICE де («if (!isAdmin) throw...»).
 *         -> ЖАЛГАН. Себеби: бул гана бир «күзөтчү» - коргоо бул
 *       эшиктен өтүп кетсе (мисалы көп controller, же жаңы
 *       жерде бул service колдонулсо), коргоо ЖОГОЛУП КЕТЕТ.
 *       Коргоо «бир жерде» болушу керек, «ар жерде» эмес.
 *
 *     Экинчи жол: role текшерүү SECURITYConfig де (hasRole(«ADMIN»)).
 *     -> ТУУРА. Себеби: бул ЖОЛДУН КИРИШИ. Сөздөн мурда
 *       Controller ге жетпейт, эгер ADMIN эмес болсо - 403.
 *       Service кириши МҮМКҮН ЭМЕС, ошондуктан service де
 *       кайра текшерүүнүн КЕРЕГИ ЖОК.
 *
 *  Ошондуктан: Security = hasRole. Service = гана көчүрмө.
 *  Эки каттамдын бири үстүнө бири: Security (ким) + Service (эмне).
 *
 * ==========================================================================
 *  🟡 ЭКИ КООПСУЗДУК МОДЕЛИ АРАЛАШПАЙТ (18-инчи пункт):
 * ==========================================================================
 *     USER  -> /api/accounts/me/transactions
 *          -> «МЕНИҢ эсебим» - эсеп ID си ТОКЕНДЕН алынат.
 *          -> TransactionService.getMyTransactions(userId, filter)
 *
 *     ADMIN -> /api/admin/accounts/{accountId}/transactions
 *          -> «УШУ эсептин тарыхы» - эсеп ID си URL ден алынат.
 *          -> AdminService.getAccountTransactions(accountId, filter)
 *
 *     Эки бирин БИР Service де бириктирсек - кимге кайсы эсеп
 *     көрсөтүлүп жатканы түшүнүксүз болуп кетти. Бул - көп
 *     мүмкүн болгон ката (бир жерде ID алдык, бир жерде окуйбуз).
 *     Ошондуктан АЙРЫ метод. Reuse бар (Specification, filter, sort),
 *     бирок күчтүү талаа (коопсуздук) АЙРЫ.
 *
 * ==========================================================================
 *  🔴 БУЛ ФАЗАДА ЖАЗУУ ЖОК (26/27/28-инчи пункттар):
 * ==========================================================================
 *     - deposit / withdraw / transfer үчүн admin endpoint ЖОК
 *       (акча гана эсебинин өз ээси тарабынан жылдырылат)
 *     - role өзгөртүү ЖОК (privilege escalation коркунучу)
 *     - delete user / account ЖОК (ON DELETE RESTRICT)
 *
 *     Бул фаза ГАНА ОКУЙТ administration. Жазыла турган endpoint
 *     үчүн айрым ой жүгүртүү жана кошумча эреже талап кылат.
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
     * БАРДЫК КОЛДОНУУЧУЛАР - бет боюнча, кыска издөө менен.
     *
     * @param page   бет (0 ден)
     * @param size   бет көлөмү (1..100)
     * @param search «safar» сыяктуу - ат же email боюнча (милдеттүү эмес)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAllUsers(Integer page, Integer size, String search) {

        validatePagination(page, size);

        // 🧡 СОРТ: createdAt DESC, id DESC (PHASE 7 нин ыкмасы - БИР
        //    стандарт). Ким «sort=passwordHash» деп соросо -
        //    Параметр эмес, биз ишенбейбиз (SQL injection коргоосу).
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        Page<User> result = userRepository.findAll(
                buildUserSpecification(search),
                PageRequest.of(page, size, sort));

        return PageResponse.of(result, adminMapper::toAdminUserResponse);
    }

    /**
     * Издөө: fullName же email боюнча, тамгасыз КАЙТАРБАЙТ.
     *
     * 🧡 ЭМНЕ УЧУН lower? «Safar» деп издесең - «safar» да
     *     табылат. Себеби: коопсуздук биринчи орунда, тамгасыз
     *     издөө көп колдонулат. PostgreSQL «ILIKE» деп мындай
     *     издөөнү түз да колдойт.
     *
     * 🟡 SQLI КОРГООСУ: ilike() ПАРАМЕТР АЛМАЙТ, биз жазабыз -
     *     колдонуучунун жазуусу бир жерге кошулат, SQL ичине
     *     түз которулбайт.
     */
    private Specification<User> buildUserSpecification(String search) {

        if (search == null || search.isBlank()) {
            return null;   // эч кандай фильтр жок - баары
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

    /** Бир колдонуучу - id боюнча. Жок болсо 404. */
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
     * БАРДЫК ЭСЕПТЕР - же бир колдонуучунун эсеби.
     *
     * @param userId милдеттүү эмес: «бул колдонуучунун эсеби» (null = баары)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminAccountResponse> getAllAccounts(
            Integer page, Integer size, Long userId) {

        validatePagination(page, size);

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        Specification<Account> spec = (userId == null)
                ? null
                : (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);

        // 🔴 findAllWithUser: JOIN FETCH - колдонуучу маалыматы БИР
        //    сорогда. Specification менен иштесек, биз гана
        //    findAll(Spec, Pageable) колдонгон болчу - ал айрым
        //    сорог түзөт (join жок). Ошондуктан эки абалды
        //    айры-айры иштейбиз.
        Page<Account> result = (spec == null)
                ? accountRepository.findAllWithUser(PageRequest.of(page, size, sort))
                : accountRepository.findAll(spec, PageRequest.of(page, size, sort));

        return PageResponse.of(result, adminMapper::toAdminAccountResponse);
    }

    /** Бир эсеп - id боюнча (колдонуучу маалыматы менен). Жок болсо 404. */
    @Transactional(readOnly = true)
    public AdminAccountResponse getAccountById(Long id) {
        Account account = accountRepository.findByIdWithUser(id)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account with id " + id + " not found"));
        return adminMapper.toAdminAccountResponse(account);
    }

    // ======================================================================
    //  TRANSACTIONS (кайсы эсептин тарыхы болсо)
    // ======================================================================

    /**
     * ЭСЕПТИН ТАРЫХЫ - admin үчүн, кайсы эсеп болсо.
     *
     * 🟡 МУРУНКУ SERVICE МЕНЕН ПАРКЫ:
     *     TransactionService.getMyTransactions(userId, filter) -
     *     ал ЭСЕП ИД СИН ТОКЕНДЕН алат жана өзү табат.
     *     Бул жерде admin эсепти АЧЫП БЕРЕТ (URL ден). Демек:
     *         бир жерде «менин эсебим», жогоруда «ушул эсеп».
     *
     * 🧡 REUSE: sort жана filter (TransactionFilter) - алардын
     *     бир бөлүгү колдонулат. TransactionMapper - түз колдонулат.
     *     Specification эрежеси болсо жогоруда так жазылган
     *     (эсеп id алдыннан белгилүү).
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAccountTransactions(
            Long accountId, TransactionFilter filter) {

        // (1) Эсеп барбы? Жок болсо 404 (так жооп, «амалдаш эмес»).
        //     🧡 МАНИЛҮҮ: бул текшерүү менен иштейт. Себеби
        //     эсеп жок болсо төмөнкү Specification «0 жазуу» кайтарар
        //     эле - клиент «200, жазуу жок» деп күлүмдөп, бирок
        //     себебин түшүнбөгөн болор эле. 404 - так.
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account with id " + accountId + " not found"));

        // (2) PHASE 7 нин фильтрин толук текшер (бир эреже!).
        validateTransactionFilter(filter);

        // (3) Specification - эсеп шарты МАНИЛҮҮ (эсеп id ушул жерде).
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

        // (4) БИРДЕШ ирет PHASE 7 менен: createdAt DESC, id DESC.
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        Page<com.minibank.entity.Transaction> result = transactionRepository.findAll(
                spec, PageRequest.of(filter.page(), filter.size(), sort));

        return PageResponse.of(result, transactionMapper::toResponse);
    }

    // ======================================================================
    //  ЖАЛПЫ ТЕКШЕРҮҮ
    // ======================================================================

    /**
     * Бет параметрлери - USER жана ADMIN үчүн бир эреже.
     *
     * 🧡 ЭМНЕ БУЛ Service де эмес, Controller де эмес?
     *     Service де - себеби бул «ички көчүрмө»: тест,
     *     кийинки endpoint же колдонуучунун ички коду да
     *     чакыра алат. Ар жерде бир эреже турса, ал эреже
     *     ЖОК болуп калат.
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
     * Transaction фильтри - PHASE 7 менен БИРДЕШ эреже.
     *
     * 🧡 БУЛ КОШУМЧА ЭМЕС - бул ТАСИЛДҮҮ. Эки Service де бир
     *     абалды көчүрүү жакшы эмес - бирин өзгөртсөң, экинчиси
     *     калат (жашыруун: «бир жердеги эреже» эмес болуп калат).
     *     Бирок бул жерде жөнөкөй 4 кат көрсөтүлгөн. Кийинки
     *     фазада аларды БИР класска чыгаруу туура болот - бирок
     *     азыр мындай abstraction талап кылынбады (42-инчи пункт).
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
