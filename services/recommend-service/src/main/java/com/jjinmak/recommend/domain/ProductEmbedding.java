package com.jjinmak.recommend.domain;

import com.jjinmak.common.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class ProductEmbedding extends BaseManualIdAndTime {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private EmbeddingStatus status;

    @Column(nullable = false)
    private String textHash;

    private String model;

    @Column(nullable = false)
    private Integer retryCount;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime claimedAt;

    public ProductEmbedding(Long productId, String textHash){
        super(productId);
        this.status = EmbeddingStatus.PENDING;
        this.textHash = textHash;
        this.retryCount = 0;
        this.nextAttemptAt = LocalDateTime.now();
    }

    public void embedded(String model){
        this.model = model;
        this.status = EmbeddingStatus.READY;
    }

    public void reset(String textHash){
        this.textHash = textHash;
        this.status = EmbeddingStatus.PENDING;
    }
}
