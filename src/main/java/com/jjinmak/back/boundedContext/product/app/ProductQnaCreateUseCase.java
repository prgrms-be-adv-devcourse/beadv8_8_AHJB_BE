package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import com.jjinmak.back.boundedContext.product.domain.ProductQna;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaCreateRequestDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaDto;
import com.jjinmak.back.boundedContext.product.out.ProductMemberRepository;
import com.jjinmak.back.boundedContext.product.out.ProductQnaRepository;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductQnaCreateUseCase {

    private final ProductRepository productRepository;
    private final ProductMemberRepository productMemberRepository;
    private final ProductQnaRepository productQnaRepository;

    // parentId가 없으면 질문, 있으면 답글을 작성한다. 작성된 Q&A를 반환해서 화면에 바로 추가할 수 있게 한다.
    public ProductQnaDto createQna(Long writerId, Long productId, ProductQnaCreateRequestDto request){
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        ProductMember writer = productMemberRepository.findById(writerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        ProductQna qna;

        if (request.parentId() == null){
            qna = ProductQna.createQuestion(product, writer, request.content());
        } else {
            ProductQna parent = productQnaRepository.findById(request.parentId())
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.QNA_NOT_FOUND));

            qna = ProductQna.createReply(product, writer, parent, request.content());
        }

        productQnaRepository.save(qna);

        return ProductQnaDto.of(qna, product.getSeller().getId());
    }
}
