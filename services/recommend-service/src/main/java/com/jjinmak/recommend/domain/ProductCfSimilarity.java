package com.jjinmak.recommend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@IdClass(ProductCfSimilarityId.class)
public class ProductCfSimilarity {

    @Id
    private Long productId;

    @Id
    private Long otherProductId;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private Integer coCount;

    @Column(nullable = false)
    private LocalDateTime computedAt;
}
