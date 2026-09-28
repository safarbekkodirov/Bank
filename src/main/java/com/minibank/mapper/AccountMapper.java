package com.minibank.mapper;

import org.springframework.stereotype.Component;

import com.minibank.dto.account.AccountResponse;
import com.minibank.entity.Account;

/**
 * Account Entity -> AccountResponse (Entity ≠ DTO чеги).
 *
 * 🧡 Эмне маанилүү?
 *   Account'тун ичинеде `user` бар — ал чыныгы User объектиси
 *   (анын ичинде passwordHash). Биз аны чыгаруучуга ӨТКӨЗБӨЙБҮЗ:
 *   бул жерде биз тандаган ТААЛАЛАРДЫ гана кошобуз.
 */
@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreatedAt()
        );
        // 🔴 account.getUser() -> ЧЫГАРЫЛБАЙТ
    }
}
