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

import com.minibank.dto.common.PageResponse;
import com.minibank.dto.transaction.TransactionFilter;
import com.minibank.dto.transaction.TransactionResponse;
import com.minibank.entity.Transaction;
import com.minibank.entity.TransactionType;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InvalidFilterException;
import com.minibank.mapper.TransactionMapper;
import com.minibank.repository.AccountRepository;
import com.minibank.repository.TransactionRepository;

/**
 * ТАРЫХ - колдонуучунун өз банк операцияларынын жазуулары.
 *
 * 🧠 ЭМНЕ БУЛ MoneyService/TransferService ИЧИНДЕ ЭМЕС?
 *     PHASE 5те биз «AccountService = эсеп, MoneyService = акча»
 *     деп бөлгөнбүз. Бул - үчүнчү тартип: TransactionService = ТАРЫХ.
 *     Бул да ошол ДҮЙӨНЧҮ: бир файл = бир максат берет.
 *     MoneyService жана TransferService БУЛ ЖЕРДЕ ӨЗГӨРҮЛБӨЙТ - алар
 *     өз жазуу ишин кылышат, бул болсо гана ОКУЙТ.
 *
 * 🧡 БУЛ КЛАСС ТЕКШЕКСИЗ (read-only):
 *     Жазуу жок, update жок, delete жок. Гана SELECT.
 *     Ошондуктан @Transactional(readOnly = true) - бул Hibernate ке
 *     «бул транзакцияда эч кандай жазуу жок» деп айтат, ошондуктан
 *     ал dirty-checking (өзгөрүү текшерүүсү) ӨЧҮРӨТ. Натыйжада
 *     тарых көрсөтүү ЫЛТЫМДЫГЫ ЖОШОРОО (анык өзгөрүү текшерүү жүрбейт).
 */
@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              TransactionMapper transactionMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    /**
     * МЕНИҢ ТАРЫХЫМ - бет боюнча.
     *
     * 🧡 МААНИЛҮҮ ТАРТИП (task 10):
     *     1. таанылган колдонуучунун id нин алуу (ТОКЕНДЕН, URL ден эмес)
     *     2. анын эсебинин id син табуу (эсеп жок болсо -> 404)
     *     3. Specification куруу (type/date/amount шарттары)
     *     4. DB дан бетти алуу (LIMIT/OFFSET базада)
     *     5. Entity -> DTO
     *
     * @param userId  ТОКЕНДЕН келген колдонуучу
     * @param filter  соруунун шарттары (кеминде бир талаа null болбош керек)
     * @return беттин DTO көрүнүшү
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getMyTransactions(
            Long userId, TransactionFilter filter) {

        validateFilter(filter);

        // (2) ЭСЕП. ID нин гана алабыз (эсепсиз окуу) - себеби
        //     биз эсептин объектин, башкача айтканда балансын,
        //     керек эмес.
        //     Бул PHASE 6 дагы ыкма: «ID ал -> FOR UPDATE менен ач».
        //     Бул жерде FOR UPDATE да керек эмес - биз жазып жатпайбыз.
        Long accountId = accountRepository.findIdByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No bank account found for the current user"));

        // (3-4) БАЗАДАН АЛ. Specification -> WHERE, Pageable ->
        //       ORDER BY + LIMIT/OFFSET. Баары SQL де болот,
        //       Java эстуне бардык жазуу тартылбайт.
        Page<Transaction> page = transactionRepository.findAll(
                buildSpecification(accountId, filter),
                buildPageable(filter));

        // (5) Entity -> DTO
        return PageResponse.of(page, transactionMapper::toResponse);
    }

    /**
     * БАЗА ШАРТТАРЫ - ар бири бар болсо кошулат.
     *
     * 🧠 ЭМНЕ БУЛ Specification?
     *     Бул «программа үчүн WHERE» жазуунун жолу. Биз гана
     *     шарттарды санап, ошол тизмени курабыз; Spring аларды
     *     бириктирип бир WHERE жасайт. Мүмкүн болгон жагдайдын
     *     САНЫ БИРДЕШ БОЛБОЙТ - 16 метод керек эмес.
     *
     * 🔴 БАА ШАРТ БИР WHERE ДЕ БИРЛЕШТИРИЛЕТ: biz.account.id = ?
     *     Бул - коопсуздуктун НЕГИЗИ: эсеп ID шарты БАР болгондо,
     *     башка колдонуучунун жазуусу дайын келбейт (task 18/19/25).
     */
    private Specification<Transaction> buildSpecification(
            Long accountId, TransactionFilter filter) {

        // 🧡 List жок, бирок ТИЗМЕ: ар бир шарт «мындай болсо ушул
        //    абалды кош» деп кеңеш берет, бирок бул буйрук эмес.
        //    Ошондуктан баштап List.new даярданабыз.
        return (root, query, cb) -> {

            List<jakarta.persistence.criteria.Predicate> predicates =
                    new ArrayList<>();

            // 🔴 ЭСЕП ШАРТЫ - дайын биринчи жана милдеттүү.
            predicates.add(cb.equal(root.get("account").get("id"), accountId));

            // TYPE
            if (filter.type() != null) {
                TransactionType type = parseType(filter.type());
                predicates.add(cb.equal(root.get("type"), type));
            }

            // УБАКЫТ АРАЛЫГЫ
            //   🧡 greaterThanOrEqualTo / lessThanOrEqualTo = СИРӨ
            //   бирдейлик: 09-01T00:00:00Z дан баштаган тарыхта
            //   «from» жарайт. Эгер катуу (>) колдонсо, баштапкы
            //   күнүн өзү алынып кетти - клиент ката жооп алмак эле.
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"), filter.to()));
            }

            // СУММА АРАЛЫГЫ (башка чектөө, бирок ошол логика)
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("amount"), filter.maxAmount()));
            }

            return cb.and(predicates.toArray(
                    new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     *  ORDER BY created_at DESC, id DESC  +  LIMIT/OFFSET.
     *
     * 🧡 ТАРТИП ТУРАКТЫЛЫГЫ - эмне үчүн id кошулат? (task 8)
     *     Гана «created_at DESC» жазса, бир нече жазуу бир убакта
     *     түзүлсө (мисалы тесттер баары бир убакта жазат), алардын
     *     ирети КАНДАЙ БОЛУП КАЛГАНЫ БЕЛГИСИЗ. Натыйжада:
     *         page=0 -> 20 жазуу
     *         page=1 -> 20 жазуу, бирок алардын БАЗЫ 1-инчи
     *                    беттеги жазуулар менен КАЙТАЛАНЫП кетиши мүмкүн!
     *     Же тескерисинче - бир жазуу ЭКИ бетте көрүнүп кетиши мүмкүн.
     *     Бул - чыныгы AJAX pagination КАТАСЫ жана чыныгы көп колдонулат.
     *     «id DESC» кошуу жазууларды АНИКТАШТЫРАДЫ: ар бир айлампада
     *     ирет бирдей болот. id жогорулаган сан, дайын өзгөрбөйт -
     *     ошондуктан ал эң ишенимдүү «экинчи тартип» (tiebreaker).
     *
     * 🔴 КЛИЕНТ СОРТ КОЛДОНБАЙТ (task 7). Pageable ны БИЗ түзтөбүз.
     *     Себеби: клиент «sort=passwordHash» деп сура алымду эмес
     *     (бу SQLге кандай айланышын текшерүү керек - injection).
     */
    private Pageable buildPageable(TransactionFilter filter) {
        Sort sort = Sort.by(
                Sort.Direction.DESC, "createdAt",   // 1-инчи белги: убакыт
                "id");                                // 2-инчи: жаралыш саны
        return PageRequest.of(
                filter.page(),
                filter.size(),
                sort);
    }

    /**
     *  КЛИЕНТТИН СОРУУСУ ТУУРА МЫ? (task 24)
     *
     * 🧡 ЭМНЕ БУЛ ЖЕРДЕ (жана Controller де ЭМЕС)?
     *     Controller да @RequestParam гана «баарын алып бер» деп айтат.
     *     ЭРЕЖЕ БИЗДИК SERVICE ДЕ. Себеби: бул Service ке келген
     *     жагдай HTTP гана эмес - тест, кийинки endpoint же
     *     клиенттин ички коду да чакыра алат. Ар жерде бир эреже
     *     турса, ал эреже ЖОК болуп калат - жана ката ушул
     *     жерден келет.
     *
     * @throws InvalidFilterException -> 400
     */
    private void validateFilter(TransactionFilter filter) {

        // PAGE
        if (filter.page() < 0) {
            throw new InvalidFilterException(
                    "page must be greater than or equal to 0");
        }

        // SIZE
        if (filter.size() < 1) {
            throw new InvalidFilterException("size must be at least 1");
        }
        if (filter.size() > TransactionFilter.MAX_SIZE) {
            throw new InvalidFilterException(
                    "size must not be greater than " + TransactionFilter.MAX_SIZE);
        }

        // TYPE
        if (filter.type() != null) {
            parseType(filter.type());   // туура эмес болсо 500 катасы жазылган
        }

        // УБАКЫТ: from > to болмбы
        if (filter.from() != null && filter.to() != null
                && filter.from().isAfter(filter.to())) {
            throw new InvalidFilterException("from must be before or equal to to");
        }

        // СУММА: minAmount > maxAmount болмбы
        if (filter.minAmount() != null && filter.maxAmount() != null
                && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            throw new InvalidFilterException(
                    "minAmount must be less than or equal to maxAmount");
        }
    }

    /**
     *  «DEPOSIT» -> TransactionType.DEPOSIT
     *
     * 🔴 ЭМНЕ БУЛ ЖЕРДЕ (кичине):
     *     Ушул жерде ката БАРЫЛСА 500 -> GlobalException
     *     Handler дин Exception -> 500. Ошондуктан биз катаны
     *     «кыска» иштин жанында ТУТАБЫЗ жана өз катабызды
     *     (InvalidFilterException -> 400) чыгарабыз.
     *
     * 🧡 ЭМНЕ ЖОК КЫЛБАЙБЫЗ: «case-insensitive» издөө. Клиент
     *     «deposit» жазса -> 400. Себеби: биздин контракт чоң
     *     тамга = эң бекем форма. Өзгөчө абалга тийүү керек болсо,
     *     ошол жерде кошобуз - азыр эмес.
     */
    private TransactionType parseType(String rawType) {
        try {
            return TransactionType.valueOf(rawType);
        } catch (IllegalArgumentException ex) {
            throw new InvalidFilterException(
                    "Invalid transaction type: " + rawType
                            + ". Allowed: " + allowedTypes());
        }
    }

    /** Клиент үчүн ачык руксат берилген түрлөр. */
    private String allowedTypes() {
        StringBuilder sb = new StringBuilder();
        for (TransactionType type : TransactionType.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(type.name());
        }
        return sb.toString();
    }
}
