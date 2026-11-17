package com.jjinmak.boundedcontext.product.product.in.dto;

import com.jjinmak.contracts.product.domain.Manufacturer;
import com.jjinmak.contracts.product.domain.ProductCategory;
import com.jjinmak.boundedcontext.product.product.domain.*;
import com.jjinmak.contracts.review.dto.SellerRatingDto;

import java.time.LocalDateTime;
import java.util.List;

// 상품 페이지 정보. 현재가/입찰 수 등 실시간 경매 정보는 auctionId로 경매 상세를 따로 조회한다.
public record ProductDetailDto(
        Long productId,
        AuctionType auctionType, // ENGLISH | DUTCH (DUTCH면 관리자가 여는 이벤트 상품)
        Long auctionId,         // 현재 회차의 경매 id (경매 시작 전이면 null)
        int round,
        String name,
        ProductCategory category,
        Manufacturer manufacturer,
        String manufacturerEtc,
        String description,
        List<String> imageUrls,
        Shipping shipping,
        AuctionTerms auctionTerms,
        LocalDateTime auctionScheduledAt,
        Seller seller,
        int wishCount,
        boolean wished,         // 로그인 회원이 찜했는지
        boolean isMine,         // 로그인 회원이 판매자인지
        LocalDateTime createdAt
) {
    public record Shipping(
            ShippingFeeType feeType,
            Long customFee,     // 직접입력일 때만 값이 있음
            boolean bundleAllowed,
            Long fee            // 실제 배송비
    ) {
    }

    // 경매 방식에 맞지 않는 값은 null이다.
    // - 영국식: instantWinPrice, duration, customEndAt
    // - 네덜란드식: dropRatePercent, dropIntervalSec, dropAmount
    //   최저가는 공개하지 않는다. (알면 최저가까지 기다리기만 하면 되어서 경매 의미가 없어진다)
    public record AuctionTerms(
            AuctionType auctionType,
            Long startingPrice,
            Long instantWinPrice,
            AuctionDuration duration,
            LocalDateTime customEndAt,
            Integer dropRatePercent,
            Integer dropIntervalSec,
            Long dropAmount         // 한 번에 내려가는 금액
    ) {
        static AuctionTerms from(ProductAuctionTerms terms) {
            if (terms.isDutch()) {
                return new AuctionTerms(
                        AuctionType.DUTCH, terms.getStartPrice(),
                        null, null, null,
                        terms.getDropRatePercent(), terms.getDropIntervalSec(), terms.dutchDropAmount());
            }
            return new AuctionTerms(
                    AuctionType.ENGLISH, terms.getStartPrice(),
                    terms.getInstantWinPrice(), terms.getDuration(), terms.getCustomEndAt(),
                    null, null, null);
        }
    }

    public record Seller(
            Long memberId,
            String nickname,
            Double ratingAvg,   // 받은 후기가 없으면 null
            long reviewCount
    ) {
    }

    public static ProductDetailDto of(Product product, List<String> imageUrls, SellerRatingDto sellerRating,
                                      boolean wished, boolean isMine) {
        ProductAuctionTerms terms = product.getAuctionTerms();
        ProductMember seller = product.getSeller();

        return new ProductDetailDto(
                product.getId(),
                terms.getAuctionType(),
                product.getCurrentAuctionId(),
                product.getRound(),
                product.getName(),
                product.getCategory(),
                product.getManufacturer(),
                product.getManufacturerEtc(),
                product.getDescription(),
                imageUrls,
                new Shipping(
                        product.getShippingFeeType(),
                        product.getCustomShippingFee(),
                        product.isBundleShipping(),
                        product.getShippingFee()
                ),
                AuctionTerms.from(terms),
                product.getAuctionScheduledAt(),
                new Seller(
                        seller.getId(),
                        seller.getNickname(),
                        sellerRating.ratingAvg(),
                        sellerRating.reviewCount()
                ),
                product.getWishCount(),
                wished,
                isMine,
                product.getCreatedAt()
        );
    }
}
