package com.minibank.service;

import java.math.BigDecimal;
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
 * TARHYH - kollonuwcynyň öz bank operasiýalarynyň ýazgylary.
 *
 *  EMNE BU MoneyService/TransferService ICINDE EMES?
 *     PHASE 5te biz "AccountService = esap, MoneyService = akça"
 *     diýip böledik. Bu - üçünji nobat: TransactionService = TARHYH.
 *     Bu hem meňzeş DÜZGÜN: bir faýl = bir uçun soňagat berýär.
 *     MoneyService we TransferService BUÝADA ÜÝTGEÝIS ÝOK - olar
 *     öz ýazuw işini geçirýär, bu bolsa diňe OKAÝAR.
 *
 *  BU KLAS TEKPEDIYI DÖWLETSIZ (read-only):
 *     Ýazma ýok, update ýok, delete ýok. Diňe SELECT.
 *     Şonuň üçin @Transactional(readOnly = true) - bu Hibernate-a
 *     "bu tranzaksiýada hiç näbelli ýazma ýok" diýip aýdýar, şonuň
 *     üçin ol dirty-checking (üýtgeşik barlagy) ÖÇÜRÝÄR. Netijede
 *     taryh görkezmek HAS ULTY (takyýa aýratynlyk yzarlanmaýar).
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
     * MENIŇ TARHYHYM - sahypa sahypa.
     *
     *  MAÝLY TARTIB (task 10):
     *     1. authenticated user id al (TOKENDEN, URL-den däl)
     *     2. onuň esabyň id-sini tap (esap ýoksa -> 404)
     *     3. Specification gur (type/date/amount şertleri)
     *     4. DB-den sahypany çek (LIMIT/OFFSET bazada)
     *     5. Entity -> DTO
     *
     *  @param userId  TOKENDEN gelen kollonuwçy
     *  @param filter  soragyň shertleri (azyndan bir meýdan null bolmaly däl)
     * @return sahypanyň DTO görnüşi
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getMyTransactions(
            Long userId, TransactionFilter filter) {

        validateFilter(filter);

        // (2) ESAP. ID-ni diňe alýarys (esapsyz okaýyş) - sebäbi
        //     biz esabyň obýegini, ýagny balansyny, gerek däl.
        //     Bu PHASE 6daky usul: "ID al -> FOR UPDATE bilen aç".
        //     Bu ýerde FOR UPDATE hem gerek däl - biz ýazmaýarys.
        Long accountId = accountRepository.findIdByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No bank account found for the current user"));

        // (3-4) BAZADA çek. Specification -> WHERE, Pageable ->
        //       ORDER BY + LIMIT/OFFSET. Hemmesi SQL-de bolýar,
        //       Java memory-e ähli ýazgy çekilmeýär.
        Page<Transaction> page = transactionRepository.findAll(
                buildSpecification(accountId, filter),
                buildPageable(filter));

        // (5) Entity -> DTO
        return PageResponse.of(page, transactionMapper::toResponse);
    }

    /**
     *  BAZA ŞERTLERI - her biri ýaly ýogsaýyn goşulýar.
     *
     *  🧠 NÄME BU Specification?
     *     Bu "programma üçin WHERE" ýazmagyň ýoly. Biz ýöne
     *     hökmleri sanap, shol sanawy gurýarys; Spring olary birleşdirip
     *     bir WHERE emele getirýär. Mümkin bolan ýetjek ýagdaý
     *     ŞOL BILEN BIRMEŇEŞ bolmadyk - 16 sany metod gerek däl.
     *
     *  🔴 HEMME ŞERT BIR SANY WHERE-de ÝYGNANÝAR: biz.account.id = ?
     *     Bu - howpsuzlygyň ESASY: esap ID şerti BARSA, başga
     *     kollonuwçynyň ýazgysy hiç haçan gelmeýär (task 18/19/25).
     */
    private Specification<Transaction> buildSpecification(
            Long accountId, TransactionFilter filter) {

        // 🧡 List ýok, ýöne LIST: her bir şert "şeýle bolsa şu ýagdaýy
        //    goş" diýip maslahat berýär, emmesi hökm däl. Şonuň üçin
        //    başda List.new taýýarlanýar.
        return (root, query, cb) -> {

            List<jakarta.persistence.criteria.Predicate> predicates =
                    new ArrayList<>();

            // 🔴 ESAP ŞERTI - hemişe birinji we hökmany.
            predicates.add(cb.equal(root.get("account").get("id"), accountId));

            // TYPE
            if (filter.type() != null) {
                TransactionType type = parseType(filter.type());
                predicates.add(cb.equal(root.get("type"), type));
            }

            // WAGT ARALYGY
            //   🧡 greaterThanOrEqualTo / lessThanOrEqualTo = SERT
            //   deňlik: 09-01T00:00:00Z den başlaýan taryhda
            //   "from" gowy gelýär. Eger strict (>) ulanylsaydy,
            //   düýbäniň özüni aýyrardy - klient gowy ýalňyş alardy.
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"), filter.to()));
            }

            // SUMA ARALYGY (şekilli çägi, lekin meňzeş logika)
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
     *  🧡 STABLE SORT - näme üçin id goşulýar? (task 8)
     *     Diňe "created_at DESC" ýazsaýdy, birnäçe ýazgy bir wagtda
     *     döredilse (mysal üçin testler ähli bir pursatda ýazýar),
     *     olaryň tertibi ANYŇ BOLMAÝAR. Netijede:
     *         page=0 -> 20 ýazgy
     *         page=1 -> 20 ýazgy, emma olaryň BAZILARY 1-nji
     *                    sahypadaky ýazgylar bilen GATYŞYP biler!
     *     Ýa-da tersine - bir ýazgy IKI sahypada görünüp biler.
     *     Bu - hakyky AJAX pagination HATASI we hakyky köp ulanylýar.
     *     "id DESC" goşmak ýazgylary DETERMINISTIK edýär: her çalşykda
     *     tertip birmeňeş. id ýokarýan san, hiç haçan üýtgemeýär -
     *     şonuň üçin ol iň ygtybarly "ikinj tertip" (tiebreaker).
     *
     *  🔴 KLIENT SORT ULANMAÝAR (task 7). Pageable-ni BIZ döredýäris.
     *     Sebäbi: klient "sort=passwordHash" diýip aňsat bilmeli däl
     *     (bu nähili SQL'e öwrüljekdigini barlamak gerek - injection).
     */
    private Pageable buildPageable(TransactionFilter filter) {
        Sort sort = Sort.by(
                Sort.Direction.DESC, "createdAt",   // 1-nji aýratynlyk: wagty
                "id");                                // 2-nji: emele geliş sany
        return PageRequest.of(
                filter.page(),
                filter.size(),
                sort);
    }

    /**
     *  KLIENTIŇ SORAGY DOGRY MY? (task 24)
     *
     *  🧡 NÄME BUÝARDA (we Controller-de näme DÄL)?
     *     Controller-de @RequestParam diňe "baryňy alyp ber" diýär.
     *     DÜZGÜN BIZDIK SERVICE-DEDIR. Sebäbi: bu Service-e kelýän
     *     ýagdaý diňe HTTP däl - test, kelleýinji endpoint, ýa-da
     *     klientiň içki kodu hem çagyryp bilýär. Her ýerde bir
     *     kada tutsak, ol kada ýOGSAÝAR - we ýalňyş şu gapdan gelýär.
     *
     *  @throws InvalidFilterException -> 400
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
            parseType(filter.type());   // nädogry bolsa 500 ḥökmünde ýatýar
        }

        // WAGT: from > to bolmaly däl
        if (filter.from() != null && filter.to() != null
                && filter.from().isAfter(filter.to())) {
            throw new InvalidFilterException("from must be before or equal to to");
        }

        // SUMA: minAmount > maxAmount bolmaly däl
        if (filter.minAmount() != null && filter.maxAmount() != null
                && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            throw new InvalidFilterException(
                    "minAmount must be less than or equal to maxAmount");
        }
    }

    /**
     *  "DEPOSIT" -> TransactionType.DEPOSIT
     *
     *  🔴 NÄME BUÝARDA (myşgyr):
     *     Üns ber: bu hökm ANY ýerde ýalňyş bolsa 500 -> GlobalException
     *     Handler-iň Exception -> 500. Şonuň üçin biz ýalňyşy
     *     "gysga" hereketiň ýanynda TUTÝARYS we öz ýalňyşymyzy
     *     (InvalidFilterException -> 400) ýaýýarys.
     *
     *  🧡 NÄME ÝOGSAÝARYS: "case-insensitive" gözleg. Klient
     *     "deposit" ýazsa -> 400. Sebäbi: biziň kontrakt ýokary
     *     harp = iň ykjam görnüş. Aýratyn ýagdaýa degmek islesek,
     *     ony nobatda goşýarys - şimdilik däl.
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

    /** Klient üçin aýdyň rugsat berlen görnüşler. */
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
