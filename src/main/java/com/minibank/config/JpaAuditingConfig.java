package com.minibank.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing күйгүзөт: createdAt жана updatedAt тааларын
 * Hibernate автоматтык толтуруш үчүн (@CreatedDate / @LastModifiedDate).
 *
 * Бул бир жолу күйгүзүлөт — бүт колдонмо үчүн.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
