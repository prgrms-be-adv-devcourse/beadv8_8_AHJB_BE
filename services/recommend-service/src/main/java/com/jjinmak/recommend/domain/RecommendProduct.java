package com.jjinmak.recommend.domain;

import com.jjinmak.common.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class RecommendProduct extends BaseManualIdAndTime {

    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String manufacturer;

    private String thumbnailUrl;

    @Column(nullable = false)
    private Boolean deleted;

    public RecommendProduct(
            Long productId, Long version, Long sellerId,
            String productName, String category,
            String manufacturer, String thumbnailUrl
    ){
        super(productId);
        this.version = version;
        this.sellerId = sellerId;
        this.productName = productName;
        this.category = category;
        this.manufacturer = manufacturer;
        this.thumbnailUrl = thumbnailUrl;
        this.deleted = false;
    }

    public void sync(Long version, Long sellerId,
                     String productName, String category,
                     String manufacturer, String thumbnailUrl
    ){
        this.version = version;
        this.sellerId = sellerId;
        this.productName = productName;
        this.category = category;
        this.manufacturer = manufacturer;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void delete(){
        this.deleted = true;
    }
}
