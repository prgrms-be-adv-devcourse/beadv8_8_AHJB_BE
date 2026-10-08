package com.jjinmak.back.global.jpa.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * id를 직접 지정하는 엔티티용 (다른 도메인 데이터를 같은 id로 복제할 때. 예: 회원 복제본)
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseManualIdAndTime extends BaseEntity implements Persistable<Long> {

    @Id
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected BaseManualIdAndTime(Long id) {
        this.id = id;
    }

    /**
     * 새 엔티티 판단 기준. 아직 저장되지 않았으면 createdAt이 null이다.
     */
    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}