package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import com.jjinmak.back.boundedContext.product.in.dto.AuctionDuration;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto.AuctionTerms;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto.Shipping;
import com.jjinmak.back.boundedContext.product.out.ProductMemberRepository;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import com.jjinmak.back.shared.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductCreateUseCase {

    private final ProductRepository productRepository;
    private final ProductMemberRepository productMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Product createProduct(Long sellerId, ProductCreateRequestDto request){
        // TODO: 판매자 등록 여부(PRODUCT002), 계정 정지/차단(PRODUCT003) 검사는 회원 컨텍스트 구현 후 추가
        ProductMember seller = productMemberRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        // TODO: 정책 저장 방식이 정해지면 현재 정책 버전과 일치하는지도 검사
        if (!request.policyAgreed()){
            throw new BusinessException(ProductErrorCode.POLICY_NOT_AGREED);
        }

        AuctionTerms auctionTerms = request.auctionTerms();
        validateAuctionTerms(auctionTerms);

        Shipping shipping = request.shipping();

        Product product = Product.create(
                seller, request.name(), request.category(),
                request.manufacturer(), request.manufacturerEtc(), request.description(),
                shipping.feeType(), shipping.customFee(), shipping.bundleAllowed(),
                request.policyVersion(), LocalDateTime.now()
        );

        productRepository.save(product);

        AuctionDuration duration = auctionTerms.duration();

        eventPublisher.publishEvent(new ProductRegisteredEvent(
                product.getId(),
                seller.getId(),
                auctionTerms.startingPrice(),
                auctionTerms.instantWinPrice(),
                duration.getDays(),
                duration == AuctionDuration.CUSTOM ? auctionTerms.customEndAt() : null
        ));

        return product;
    }

    private void validateAuctionTerms(AuctionTerms auctionTerms){
        Long instantWinPrice = auctionTerms.instantWinPrice();

        if (instantWinPrice != null && instantWinPrice <= auctionTerms.startingPrice()){
            throw new BusinessException(ProductErrorCode.INVALID_INSTANT_WIN_PRICE);
        }

        if (auctionTerms.duration() == AuctionDuration.CUSTOM && auctionTerms.customEndAt() == null){
            throw new BusinessException(ProductErrorCode.DIRECT_INPUT_REQUIRED);
        }
    }
}
