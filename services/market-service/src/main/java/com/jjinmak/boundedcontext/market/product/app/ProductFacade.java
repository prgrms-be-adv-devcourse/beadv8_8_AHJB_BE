package com.jjinmak.boundedcontext.market.product.app;

import com.jjinmak.boundedcontext.market.product.domain.Product;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductAuctionOpenDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductDetailDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductDutchCreateRequestDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductMyListResponseDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductRelistDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductUpcomingListResponseDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductUpdateRequestDto;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductCreateUseCase productCreateUseCase;
    private final ProductDutchCreateUseCase productDutchCreateUseCase;
    private final ProductUpdateUseCase productUpdateUseCase;
    private final ProductDeleteUseCase productDeleteUseCase;
    private final ProductRequestAuctionUseCase productRequestAuctionUseCase;
    private final ProductReadSummaryUseCase productReadSummaryUseCase;
    private final ProductReadDetailUseCase productReadDetailUseCase;
    private final ProductReadUpcomingUseCase productReadUpcomingUseCase;
    private final ProductStartAuctionUseCase productStartAuctionUseCase;
    private final ProductRelistUseCase productRelistUseCase;
    private final ProductReadMineUseCase productReadMineUseCase;

    @Transactional
    public Product createProduct(Long sellerId, ProductCreateRequestDto request){
        return productCreateUseCase.createProduct(sellerId, request);
    }

    @Transactional
    public Product createDutchProduct(Long adminId, ProductDutchCreateRequestDto request){
        return productDutchCreateUseCase.createDutchProduct(adminId, request);
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

    // market 경매 시작 흐름(MarketAuctionOpenUseCase)의 트랜잭션에 참여한다.
    @Transactional
    public Optional<ProductAuctionOpenDto> prepareAuctionOpen(Long productId, LocalDateTime now){
        return productRequestAuctionUseCase.prepareAuctionOpen(productId, now);
    }

    @Transactional
    public void startAuction(Long productId, Long auctionId){
        productStartAuctionUseCase.startAuction(productId, auctionId);
    }

    // market 재등록 흐름(MarketRelistUseCase)의 트랜잭션에 참여한다.
    @Transactional
    public ProductRelistDto relist(Long sellerId, Long productId, LocalDateTime now){
        return productRelistUseCase.relist(sellerId, productId, now);
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
}
