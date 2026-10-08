package com.jjinmak.back.global.jpa.entity;

import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

/**
 * 모든 엔티티의 공통 규칙. 필드는 하위 클래스(BaseIdAndTime, BaseManualIdAndTime)가 가진다.
 */
@MappedSuperclass
public abstract class BaseEntity {

    public abstract Long getId();

    public abstract LocalDateTime getCreatedAt();

    public abstract LocalDateTime getUpdatedAt();
}