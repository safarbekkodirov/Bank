package com.minibank.dto.admin;

import java.time.Instant;

import com.minibank.entity.Role;

/**
 * ADMIN gorunusi: user (PHASE 8).
 *
 * 🧡 "ADMINUser" diye at berdik sebebi bu DTO bary-yerde
 *    ulanylmayar. "UserResponse" (PHASE 3A) - her kollonuwcu
 *    ozinikini gorady. "AdminUserResponse" - ADMIN admin panelinde
 *    BASQALARDY goredy. Iki ertekchi ar BIRINCHI EMES - sebebi:
 *      1. Eki cheg ekilek: USER endpoint'i "men kimmin" jogap
 *         bermeli, ADMIN endpoint'i bolsa "kim bar" (Id, role,
 *         updatedAt - Administrator uchun kerek).
 *      2. KIRISHE CHEGI: UserResponse'ge updatedAt qoshsa,
 *         /api/users/me ham ogozgorosuz oset. Eki aeral - a
 *         ketse, bashasiga tegmez.
 *
 * 🔴 PASSWORD HASH BUL YERDE YOK, KOSULMAYDY, HECH KAÇAN.
 *    Eger bu yerde bir katatlik bolsa - admin parollarinin
 *    hashini jykyo chygarat edi. Biziŋ testler barlaydi.
 *
 * @param id        ichki nomur
 * @param fullName  aty
 * @param email     email (kichii tamga)
 * @param role      USER | ADMIN
 * @param createdAt kachan kattalgan
 * @param updatedAt kachan ozgergen
 */
public record AdminUserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        Instant createdAt,
        Instant updatedAt
) {
}
