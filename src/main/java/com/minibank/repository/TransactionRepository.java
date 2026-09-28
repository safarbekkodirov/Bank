package com.minibank.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minibank.entity.Transaction;

/**
 * Transaction Entity менен transactions таблицасын байланыштырган класс.
 *
 * 🧠 Бул репозиторий «ЖУРНАЛ» — аны окууга гана. Биз бул жерден
 *     эсептин балансын ОЧУРТПАЙБЫЗ (баланс = accounts.balance,
 *     эсептин өзүндө). Журнал — көрсөткөн, эсеп — чыныгы.
 *
 * 🧡 PHASE 5те биз ЭЧ КАЧАН "тарых" эндпоинтин ЖАЗБАЙБЫЗ
 *     (эң тапшырманын чеги) — бирок репозиторий методун АЛДЫНАН
 *     даярдап коёбуз. Кийин (PHASE 6+) энд көрүнүш керек болгондо,
 *     Controller жазып, бул методду чакырса болот — логика кайта
 *     жазылбайт.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Эсептин операциялары, ЭҢ ЖАҢЫСЫ БАШТА (түшүндүрмө «биз көрсөткөн»).
     *
     * 🧠 Spring Data МЕТОД АТЫН ОКУП, САРТТАН SQL ЖАЗБАЙТ:
     *     findBy + AccountId          -> WHERE account_id = ?
     *     OrderByCreatedAtDesc        -> ORDER BY created_at DESC
     *
     * 🧡 Индексти КЕРЕК ЭМЕС жазуу керек эмес: V1'де
     *     ix_transactions_account_id_created_at МЫНДАЙ ЭЛЕ индекс
     *     (account_id, created_at DESC) бар — биздин сортулгоо
     *     аны СЫРТЫНДЫ КОЛДОНАЛЫ.
     */
    List<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);
}