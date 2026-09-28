package com.minibank.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/**
 * created_at / updated_at таалары үчүн ЖАЙЫК ГАЙДАЛ (таблица жок).
 *
 * @MappedSuperclass = "башынан үлгүлөнгөн класс". Бул таалагыч өзү TABLE
 * ЭМЕС, бирок анын таалары бардык мурастар жана объекттерге көчөт.
 *
 * @EntityListeners(AuditingEntityListener.class) = Hibernate ушул тааларды
 * автоматтык толтурат:
 *   - createdAt -> INSERT учурунда (ApplicationAuditor, биз да колдонобуз)
 *   - updatedAt -> INSERT жана UPDATE учурунда
 *
 * Эмне керек? Бүгүн колдонуучунун timestamp'ын кол менен жазуунун кереги жок —
 * эс ана да унутуп калат, ал эса жасалган эмес.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
