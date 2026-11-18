package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductDetailDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductMyListResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductUpcomingListResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductUpdateRequestDto;
import com.jjinmak.back.shared.auction.event.AuctionFailedEvent;
import com.jjinmak.back.shared.auction.event.AuctionStartedEvent;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.product.dto.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductSyncMemberUseCase productSyncMemberUseCase;
    private final ProductCreateUseCase productCreateUseCase;
    private final ProductUpdateUseCase productUpdateUseCase;
    private final ProductDeleteUseCase productDeleteUseCase;
    private final ProductRequestAuctionUseCase productRequestAuctionUseCase;
    private final ProductReadSummaryUseCase productReadSummaryUseCase;
    private final ProductReadDetailUseCase productReadDetailUseCase;
    private final ProductReadUpcomingUseCase productReadUpcomingUseCase;
    private final ProductStartAuctionUseCase productStartAuctionUseCase;
    private final ProductAuctionResultUseCase productAuctionResultUseCase;
    private final ProductReadMineUseCase productReadMineUseCase;

    @Transactional
    public ProductMember syncMember(MemberDto memberDto){
        return productSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public Product createProduct(Long sellerId, ProductCreateRequestDto request){
        return productCreateUseCase.createProduct(sellerId, request);
    }

    @Transactional
    public Product updateProduct(Long sellerId, Long productId, ProductUpdateRequestDto request){
        return productUpdateUseCase.updateProduct(sellerId, productId, request);
    }

    @Transactional
    public void deleteProduct(Long sellerId, Long productId){
        productDeleteUseCase.deleteProduct(sellerId, productId);
    }

    @Transactional(readOnly = true)
    public List<Long> findAuctionRequestTargetIds(LocalDateTime now){
        return productRequestAuctionUseCase.findTargetIds(now);
    }

    // 상품마다 별도 트랜잭션으로 처리해서, 하나가 실패해도 다른 상품은 경매로 넘어간다.
    @Transactional
    public void requestAuction(Long productId, LocalDateTime now){
        productRequestAuctionUseCase.requestAuction(productId, now);
    }

    @Transactional(readOnly = true)
    public ProductSummaryDto getProductSummary(Long productId){
        return productReadSummaryUseCase.getProductSummary(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getProductSummaries(Collection<Long> productIds){
        return productReadSummaryUseCase.getProductSummaries(productIds);
    }

    @Transactional(readOnly = true)
    public ProductUpcomingListResponseDto readUpcoming(String category, int page, int size){
        return productReadUpcomingUseCase.readUpcoming(category, page, size);
    }

    @Transactional(readOnly = true)
    public ProductMyListResponseDto readMine(Long sellerId){
        return productReadMineUseCase.readMine(sellerId);
    }

    @Transactional(readOnly = true)
    public ProductDetailDto readDetail(Long memberId, Long productId){
        return productReadDetailUseCase.readDetail(memberId, productId);
    }

    @Transactional
    public void startAuction(AuctionStartedEvent event){
        productStartAuctionUseCase.startAuction(event);
    }

    @Transactional
    public void markAuctionSold(AuctionWonEvent event){
        productAuctionResultUseCase.markSold(event);
    }

    @Transactional
    public void markAuctionFailed(AuctionFailedEvent event){
        productAuctionResultUseCase.markFailed(event);
    }
}
