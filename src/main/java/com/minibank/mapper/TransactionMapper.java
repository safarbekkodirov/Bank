package com.minibank.mapper;

import org.springframework.stereotype.Component;

import com.minibank.dto.transaction.TransactionResponse;
import com.minibank.entity.Transaction;

/**
 * Transaction Entity -> TransactionResponse (Entity != DTO çegi).
 *
 *  Bu klasda BIZNES-LOGIKA ÝOK - diňe göçürme. Sebäbi: mapperiň
 *  bir ýönegy "meňzeş" bolmaly. Eger muňa ýagdaýy goşsaýdy
 *  (mysal üçin "type=DEPOSIT bolsa description ýaz"), ol nobat
 *  ölçeg üçin ýerine ýetirilýär we testlenmeýär galyberdi.
 *
 *  @Component - Spring bu klasy awtomatiki barlap, service-ä
 *  birnäçe (singleton) nusga hökmünde berýär.
 */
@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                // 🔴 getType() -> TransactionType enum. DTO-da String
                //    islenýär (klient üçin). name() - enum-dan ady alyp
                //    bize "DEPOSIT" diýen tekst berýär. null howpsuz:
                //    DB CHECK (type NOT NULL) - null gelmeýär.
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
        // 🔴 transaction.getAccount() -> CHYKARYLMAÝAR (uýduryjy)
    }
}
