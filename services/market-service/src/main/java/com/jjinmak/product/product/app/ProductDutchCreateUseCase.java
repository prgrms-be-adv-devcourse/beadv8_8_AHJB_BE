package com.jjinmak.product.product.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductAuctionTerms;
import com.jjinmak.product.product.domain.ProductMember;
import com.jjinmak.product.product.in.dto.ProductCreateRequestDto.Shipping;
import com.jjinmak.product.product.in.dto.ProductDutchCreateRequestDto;
import com.jjinmak.product.product.in.dto.ProductDutchCreateRequestDto.DutchTerms;
import com.jjinmak.product.product.out.ProductMemberRepository;
import com.jjinmak.product.product.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 관리자가 네덜란드식 이벤트 상품을 등록한다. 판매자는 관리자 본인이다.
@Service
@RequiredArgsConstructor
public class ProductDutchCreateUseCase {

    private final ProductRepository productRepository;
    private final ProductMemberRepository productMemberRepository;
    private final ProductImageAttachUseCase productImageAttachUseCase;
    private final ProductPublishChangedUseCase productPublishChangedUseCase;

    public Product createDutchProduct(Long adminId, ProductDutchCreateRequestDto request){
        // 관리자 여부는 컨트롤러의 ROLE_ADMIN 권한 검사에서 확인한다.
        ProductMember admin = productMemberRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        DutchTerms terms = request.auctionTerms();
        Shipping shipping = request.shipping();
        LocalDateTime now = LocalDateTime.now();

        Product product = Product.createDutch(
                admin, request.name(), request.category(),
                request.manufacturer(), request.manufacturerEtc(), request.description(),
                shipping.feeType(), shipping.customFee(), shipping.bundleAllowed(),
                now,
                ProductAuctionTerms.createDutch(
                        terms.startingPrice(), terms.floorPrice(),
                        terms.dropRatePercent(), terms.dropIntervalSec()
                ),
                request.openAt()
        );

        productRepository.save(product);

        // 업로드 API로 먼저 올린 이미지를 요청 순서대로 연결한다. (첫 번째가 대표 이미지)
        productImageAttachUseCase.attach(product, adminId, request.imageIds());

        // 등록 즉시 검색에 노출되도록 알린다. (오픈 전부터 검색·찜 가능)
        productPublishChangedUseCase.publish(product, ProductChangeType.CREATED);

        return product;
    }
}
