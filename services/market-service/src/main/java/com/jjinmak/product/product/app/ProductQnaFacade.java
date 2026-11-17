package com.jjinmak.product.product.app;

import com.jjinmak.product.product.in.dto.ProductQnaCreateRequestDto;
import com.jjinmak.product.product.in.dto.ProductQnaDto;
import com.jjinmak.product.product.in.dto.ProductQnaListResponseDto;
import com.jjinmak.product.product.in.dto.ProductQnaUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductQnaFacade {

    private final ProductQnaCreateUseCase productQnaCreateUseCase;
    private final ProductQnaReadUseCase productQnaReadUseCase;
    private final ProductQnaUpdateUseCase productQnaUpdateUseCase;
    private final ProductQnaDeleteUseCase productQnaDeleteUseCase;

    @Transactional
    public ProductQnaDto createQna(Long writerId, Long productId, ProductQnaCreateRequestDto request){
        return productQnaCreateUseCase.createQna(writerId, productId, request);
    }

    @Transactional(readOnly = true)
    public ProductQnaListResponseDto readQnas(Long productId, int page, int size){
        return productQnaReadUseCase.readQnas(productId, page, size);
    }

    @Transactional(readOnly = true)
    public ProductQnaListResponseDto readNewQnas(Long productId, Long after){
        return productQnaReadUseCase.readNewQnas(productId, after);
    }

    @Transactional
    public void updateQna(Long writerId, Long qnaId, ProductQnaUpdateRequestDto request){
        productQnaUpdateUseCase.updateQna(writerId, qnaId, request);
    }

    @Transactional
    public void deleteQna(Long writerId, Long qnaId){
        productQnaDeleteUseCase.deleteQna(writerId, qnaId);
    }
}
