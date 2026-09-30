package com.minibank.mapper;

import org.springframework.stereotype.Component;

import com.minibank.dto.admin.AdminAccountResponse;
import com.minibank.dto.admin.AdminUserResponse;
import com.minibank.entity.Account;
import com.minibank.entity.User;

/**
 * Entity -> DTO которуучу (ADMIN, PHASE 8).
 *
 * 🧠 ЭМНЕ БУЛ AdminMapper БОЛУП ЖАТЫР? Бизде бар болган
 *     UserMapper жана AccountMapper бар (PHASE 3A/4). Алар
 *     колдонуучу эндпоинттери үчүн. Бул - admin үчүн, башка
 *     маалымат топтому, башка эреже (updatedAt да бар, эсеп
 *     менен бирге колдонуучу маалыматы).
 *     Бир класска баарын жыйноо - «курулман объекти» болчу.
 *
 * 🧡 БУЛ КЛАССТА БИЗНЕС-ЛОГИКА ЖОК - гана көчүрмө. Себеби:
 *     «эгер колдонуучу null болсо -> анда мына» деген жагдай
 *     маппердин эмес, Service тин иши.
 */
@Component
public class AdminMapper {

    /** User -> AdminUserResponse. 🔴 passwordHash КОШУЛБАЙТ. */
    public AdminUserResponse toAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    /**
     * Account -> AdminAccountResponse.
     *
     * 🟡 БУЛ ЖЕРДЕ user.getEmail() ЧАГЫРЫЛЫП БИЛЕР:
     *     account.getUser() LAZY, ал эмне Service де
     *     транзакция ИЧИНДЕ оулганда - Hibernate аны ошол
     *     бир сорогдан алат (биз AccountRepository де join fetch
     *     колдонобул). Эгер ал болбосо, LazyInitializationException -
     *     ошондуктан бул Service де жазуу керек, мапперде эмес.
     */
    public AdminAccountResponse toAdminAccountResponse(Account account) {
        return new AdminAccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getUser().getId(),
                account.getUser().getEmail(),
                account.getUser().getFullName(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
        // 🔴 account.getUser().getPasswordHash() -> ДАЙЫН ЖОК
    }
}
