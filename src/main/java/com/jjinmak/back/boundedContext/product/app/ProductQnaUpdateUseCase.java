package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductQna;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaUpdateRequestDto;
import com.jjinmak.back.boundedContext.product.out.ProductQnaRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductQnaUpdateUseCase {

    private final ProductQnaRepository productQnaRepository;

    public void updateQna(Long writerId, Long qnaId, ProductQnaUpdateRequestDto request){
        ProductQna qna = productQnaRepository.findByIdAndDeletedAtIsNull(qnaId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.QNA_NOT_FOUND));

        qna.update(writerId, request.content());
    }
}
