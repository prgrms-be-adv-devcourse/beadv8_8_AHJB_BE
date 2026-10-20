package com.jjinmak.back.boundedContext.product.in.dto;

import com.jjinmak.back.boundedContext.product.domain.*;
import com.jjinmak.back.shared.review.dto.SellerRatingDto;

import java.time.LocalDateTime;
import java.util.List;

// 상품 페이지 정보. 현재가/입찰 수 등 실시간 경매 정보는 auctionId로 경매 상세를 따로 조회한다.
public record ProductDetailDto(
        Long productId,
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

    public record AuctionTerms(
            Long startingPrice,
            Long instantWinPrice,
            AuctionDuration duration,
            LocalDateTime customEndAt
    ) {
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
                new AuctionTerms(
                        terms.getStartPrice(),
                        terms.getInstantWinPrice(),
                        terms.getDuration(),
                        terms.getCustomEndAt()
                ),
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
