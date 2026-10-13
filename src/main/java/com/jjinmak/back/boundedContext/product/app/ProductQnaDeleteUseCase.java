package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductQna;
import com.jjinmak.back.boundedContext.product.out.ProductQnaRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductQnaDeleteUseCase {

    private final ProductQnaRepository productQnaRepository;

    // 소프트 삭제. 질문이 삭제돼도 답글은 남고, 질문 자리는 "삭제된 Q&A"로 표시된다.
    public void deleteQna(Long writerId, Long qnaId){
        ProductQna qna = productQnaRepository.findByIdAndDeletedAtIsNull(qnaId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.QNA_NOT_FOUND));

        qna.delete(writerId, LocalDateTime.now());
    }
}
