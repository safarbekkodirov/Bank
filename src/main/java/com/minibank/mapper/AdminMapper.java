package com.minibank.mapper;

import org.springframework.stereotype.Component;

import com.minibank.dto.admin.AdminAccountResponse;
import com.minibank.dto.admin.AdminUserResponse;
import com.minibank.dto.account.AccountResponse;
import com.minibank.dto.user.UserResponse;
import com.minibank.entity.Account;
import com.minibank.entity.User;

/**
 * Entity -> DTO gocurucu (ADMIN, PHASE 8).
 *
 * 🧠 EMNE BU AdminMapper BOLEK? Bizde bar bolan
 *     UserMapper va AccountMapper bar (PHASE 3A/4). Olar
 *     kollonuwcu endpoint'leri uchun. Bu - admin uchun, basga
 *     maa lumat toplumy, basga qoida (updatedAt ham bar, user
 *     maglumaty esep bilen birga).
 *     Bir klasa hemme narsany jamlamak - "god object" bolardy.
 *
 * 🧡 BU KLASDA BIZNES-LOGIKA YOK - diňe köçürme. Sebäbi:
 *     "eger user null bolsa -> shunda şoňa" diýen ýagdaý
 *     mapperiň däl-de, Service-iň işi.
 */
@Component
public class AdminMapper {

    /** User -> AdminUserResponse. 🔴 passwordHash KOSULMAÝAR. */
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
     * 🟡 BU YERDE user.getEmail() ÇAGYRYLYP BILERY:
     *     account.getUser() LAZY bolup, servisde tranzaksiýa
     *     ICHIŇDE okalsa - Hibernate ony shol bir soragdan alady
     *     (biz AccountRepository'da join fetch ulanyýarys).
     *     Eger ol ýok bolsa, LazyInitializationException - şonuň
     *     üçin Service-de ýazmak hökmany, mapper-de däl.
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
        // 🔴 account.getUser().getPasswordHash() -> HECH QAÇAN
    }
}
