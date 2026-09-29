package com.minibank.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.minibank.entity.Transaction;

/**
 * Transaction Entity menen transactions tablitsasyny baglanshyrýan klas.
 *
 * 🧠 Bu repositorij "JURNAL" - ony okuga gana. Biz bu ýerde esabyň
 *     balansyny OCHURTMAÝARYS (balans = accounts.balance). Žurnal -
 *     görkezýän, esap - hakyky.
 *
 * 🟢 PHASE 7: transaction history üçin JpaSpecificationExecutor.
 *
 * 🧠 EMNE Specification, EMNE DÜZGÜNLI METOD DÄL?
 *     Düzgünli metod (findByAccountId...) diňe BIR sorag üçin gowy.
 *     Emma biz 4 sany ÄTBIEK filteri soraýarys:
 *         type, from/to (sagat), minAmount/maxAmount.
 *     Her birini goşsaýyn -> 2^4 = 16 metod. Bu ÝALŇYŞ: 16 metoduň
 *     her biri öz-özüňe barlanmagy gerek, we 16-njy ýalňyş ýüze
 *     çykanda ol ýuwulýar. Specification bolsa BIR funksiýa: her
 *     köp sanaw hökmany: "şu şerti goş, şu şerti goşma".
 *
 * 🔴 EMNE ÝOGSAÝARYS? Arbitrary sort. Klient sort=passwordHash diýip
 *     aňsat bilmerdi - Pageable BIZ KURÝARYS, klient däl.
 *
 * 🟢 DB PAGINATION: Page + Pageable -> LIMIT/OFFSET bazada.
 *     Ýazgylaryň hemmesi Java memory-e çekilmeýär (task 27).
 */
@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>,
                JpaSpecificationExecutor<Transaction> {

    /**
     * Esabyň operasiýalary, EŇ ŽAŇY SY BASHA (tushendirme "biz görkezýän").
     *
     * 🧡 PHASE 5te bu metod taýýar goýulýardy. Indi biz ony
     *    tarih üçin ulanýarys; emma PHASE 7ninyň esasy ýoly -
     *    findAll(Specification, Pageable) aşakda.
     *
     * 🧡 Индексти KEREK EMES jazyw kerek däl: V1de
     *    ix_transactions_account_id_created_at şeýle emel indeks
     *    bar (account_id, created_at DESC).
     */
    List<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * 🧪 TEST KÖMEKÇISI - ýazgynyň created_at wagtyny el bilen goýmak.
     *
     * 🟡 NÄME ÜÇIN BU GEREK?
     *     Transaction entity @CreatedDate (Spring auditing) ulanýar -
     *     ol wagt YZYLYNDAN gelýär we Java arkaly üýtgedilmeýär.
     *     Emma "wagt boýunça filter" we "birmeňzeş wagt -> id DESC"
     *     testleri üçin bize ýazgylaryň WAGTNI saýlamak gerek.
     *     Şonuň üçin test kömekçisi hökmünde ýörite metod.
     *
     * 🔴 ÖNÜMLIK KODYNY GOLLANDYRMAÝARYS:
     *     Bu metod diňe testiň ýagdaýlary üçin. Öndürimçilikde
     *     "žurnal ýazgysynyň wagtyny üýtgetmek" = ýazgy üýtgetmek
     *     = append-only düzgüniniň bozulmagy.
     *
     * @Modifying + @Transactional -> JPQL UPDATE hökmany (SELECT däl,
     *     sebäbi biz ýazmaly ýa: JPQL UPDATE diňe @Modifying bilen
     *     ýerine ýetirilýär).
     */
    @Modifying
    @Query("update Transaction t set t.createdAt = :createdAt where t.id = :id")
    void updateCreatedAt(@Param("id") Long id, @Param("createdAt") java.time.Instant createdAt);
}
