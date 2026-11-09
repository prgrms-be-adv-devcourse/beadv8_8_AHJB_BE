package com.jjinmak.search.domain;

import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;

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

    // ES 낙관적 잠금용 (타입만 보고 Spring Data가 채우며, 문서에 저장되지 않음)
    // 읽은 뒤 다른 쪽이 먼저 저장했으면 저장이 실패한다.
    private SeqNoPrimaryTerm seqNoPrimaryTerm;

    // 아직 아무 이벤트도 반영되지 않은 빈 문서
    public static ProductSearchDocument empty(Long productId) {
        ProductSearchDocument document = new ProductSearchDocument();
        document.productId = productId;
        return document;
    }

    // 처음 저장하는 문서인지 (ES에서 읽어온 문서면 seqNo가 있다)
    public boolean isNew() {
        return seqNoPrimaryTerm == null;
    }

    // ── 상품 이벤트: 상품 정보만 고친다 (경매 정보는 건드리지 않음) ──

    // 저장된 버전보다 오래된 이벤트면 무시하고 false를 돌려준다.
    // 같은 버전은 반영한다. (이미지만 바꾼 수정은 상품 버전이 그대로다)
    public boolean applyProductChange(ProductChangedEvent event, LocalDateTime now) {
        if (productVersion != null && event.version() < productVersion) {
            return false;
        }

        this.productVersion = event.version();
        this.sellerId = event.sellerId();
        this.productName = event.productName();
        this.category = event.category();
        this.manufacturer = event.manufacturer();
        this.manufacturerName = event.manufacturerName();
        this.startPrice = event.startPrice();
        this.instantWinPrice = event.instantWinPrice();
        this.auctionScheduledAt = event.auctionScheduledAt();
        this.thumbnailUrl = event.thumbnailUrl();

        // 등록 시각은 처음 받은 상품 이벤트(보통 CREATED)의 시각으로 정한다.
        if (registeredAt == null) {
            this.registeredAt = event.occurredAt();
        }
        // 경매 이벤트가 먼저 와서 상태가 이미 있으면 그대로 둔다.
        if (status == null) {
            this.status = ProductSearchStatus.SCHEDULED;
        }
        if (event.changeType() == ProductChangeType.DELETED) {
            this.deleted = true;
        }
        this.syncedAt = now;
        return true;
    }

    // ── 경매 이벤트: 경매 정보만 고친다 (상품 서비스의 ProductAuctionState와 같은 규칙) ──

    // 경매가 시작되면 진행 중으로 바꾼다. 상태는 시작 전일 때만 바꾼다.
    // (낙찰/무산 이벤트가 먼저 왔으면 상태는 그대로 두고 시각만 채운다)
    public boolean applyAuctionStarted(AuctionStartedEvent event, LocalDateTime now) {
        if (isOtherAuction(event.auctionId())) {
            return false;
        }
        this.auctionId = event.auctionId();
        this.auctionStartAt = event.startAt();
        this.auctionEndAt = event.endAt();
        if (status == null || status == ProductSearchStatus.SCHEDULED) {
            this.status = ProductSearchStatus.IN_PROGRESS;
        }
        this.syncedAt = now;
        return true;
    }

    // 낙찰. 이미 끝난 경매(낙찰/무산)면 무시한다.
    public boolean applyAuctionWon(AuctionWonEvent event, LocalDateTime now) {
        if (isOtherAuction(event.auctionId()) || isEnded()) {
            return false;
        }
        this.auctionId = event.auctionId();
        this.status = ProductSearchStatus.SOLD;
        this.winningPrice = event.winningPrice();
        if (auctionEndAt == null) {
            this.auctionEndAt = event.winAt();
        }
        this.syncedAt = now;
        return true;
    }

    // 무산(유찰·미결제·환불). 낙찰 후 미결제·환불도 있어서 SOLD에서도 바뀐다. 이미 무산이면 무시한다.
    public boolean applyAuctionFailed(AuctionFailedEvent event, LocalDateTime now) {
        if (isOtherAuction(event.auctionId()) || status == ProductSearchStatus.FAILED) {
            return false;
        }
        this.auctionId = event.auctionId();
        this.status = ProductSearchStatus.FAILED;
        if (auctionEndAt == null) {
            this.auctionEndAt = event.failedAt();
        }
        this.syncedAt = now;
        return true;
    }

    // 다른 경매의 이벤트면 무시한다. (경매 id가 아직 없으면 받아들인다)
    private boolean isOtherAuction(Long eventAuctionId) {
        return auctionId != null && !auctionId.equals(eventAuctionId);
    }

    private boolean isEnded() {
        return status == ProductSearchStatus.SOLD || status == ProductSearchStatus.FAILED;
    }
}
