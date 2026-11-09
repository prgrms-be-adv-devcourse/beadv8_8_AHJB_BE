package com.jjinmak.search.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.LocalDateTime;

// 상품 검색 문서 (상품 1개 = 문서 1개, 문서 id = 상품 id)
// - 상품 서비스(product.changed)와 경매 서비스(auction.*) 이벤트로 채운다.
// - 인덱스·매핑은 resources/elasticsearch/*.json 이 기준이고, 서비스가 뜰 때 ProductSearchIndexInitializer가 만든다.
//   (Spring Data 자동 생성은 별칭을 다루지 못해서 끈다)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 매핑이 strict라서 Spring Data의 클래스 이름 필드(_class)는 저장하지 않는다.
@Document(indexName = ProductSearchDocument.INDEX_ALIAS, createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class ProductSearchDocument {

    // 앱은 항상 별칭으로 읽고 쓴다. 매핑을 바꿀 땐 v2를 만들고 별칭만 옮긴다.
    public static final String INDEX_ALIAS = "product_search";
    public static final String INDEX_NAME = "product_search_v1";

    @Id
    private Long productId;

    // ── 검색어로 찾는 필드 (nori 분석) ──
    @Field(type = FieldType.Text)
    private String productName;

    // 제조사 이름 ("닌텐도", 기타면 직접 입력한 이름) – 제조사 이름으로도 검색되게 한다.
    @Field(type = FieldType.Text)
    private String manufacturerName;

    // ── 조건으로 거르는 필드 (정확히 일치) ──
    @Field(type = FieldType.Keyword)
    private String category;        // ProductCategory.name()

    @Field(type = FieldType.Keyword)
    private String manufacturer;    // Manufacturer.name()

    @Field(type = FieldType.Keyword)
    private ProductSearchStatus status;

    // ── 정렬·범위 조건 필드 ──
    @Field(type = FieldType.Long)
    private Long startPrice;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime registeredAt;         // 최신순

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime auctionScheduledAt;   // 시작 임박순

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime auctionStartAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime auctionEndAt;         // 마감 임박순, 최근 종료순

    // ── 화면 표시용 (검색·정렬에 안 씀) ──
    @Field(type = FieldType.Long, index = false)
    private Long sellerId;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Long, index = false)
    private Long instantWinPrice;

    @Field(type = FieldType.Long, index = false)
    private Long auctionId;

    @Field(type = FieldType.Long, index = false)
    private Long winningPrice;

    // ── 동기화 관리용 ──
    // 상품 이벤트의 버전. 이보다 작은 버전의 이벤트는 무시한다.
    @Field(type = FieldType.Long)
    private Long productVersion;

    // 상품이 삭제되면 문서를 지우지 않고 표시만 한다. (늦게 온 이벤트로 되살아나는 것 방지)
    @Field(type = FieldType.Boolean)
    private boolean deleted;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime syncedAt;             // 마지막으로 이벤트를 반영한 시각
}
