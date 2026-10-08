package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductSyncMemberUseCase productSyncMemberUseCase;
    private final ProductCreateUseCase productCreateUseCase;
    private final ProductRequestAuctionUseCase productRequestAuctionUseCase;

    @Transactional
    public ProductMember syncMember(MemberDto memberDto){
        return productSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public Product createProduct(Long sellerId, ProductCreateRequestDto request){
        return productCreateUseCase.createProduct(sellerId, request);
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
}
