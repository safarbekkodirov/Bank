package com.minibank.dto.user;

import java.time.Instant;

import com.minibank.entity.Role;

/**
 * Колдонмонунун сырткы көрүнүшү.
 *
 * ⚠️ БУЛ ЖЕРДЕ ПАРОЛЬ ЖОК. Пароль, аны анык хэши да, эч качан
 * API аркылуу КАЙТАРЫЛБАЙТ. Бул маанилүү эреже: биз чейин "hash коюп,
 * кол бош кол менен өчүрүү" деп айтабыз.
 *
 * @param id        — DB'дин ички номуру
 * @param fullName  — көрүүнүш үчүн ат
 * @param email     — email (кичи тамга менен сакталган)
 * @param role      — USER же ADMIN
 * @param createdAt — качан катталган (Instant = убакыт + убакыт белгиси)
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        Instant createdAt
) {
}
