package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductQna;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaListResponseDto;
import com.jjinmak.back.boundedContext.product.out.ProductQnaRepository;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductQnaReadUseCase {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_POLLING_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductQnaRepository productQnaRepository;

    // 질문을 최신순으로 페이지 조회하고, 각 질문의 답글을 묶어서 반환한다.
    public ProductQnaListResponseDto readQnas(Long productId, int page, int size){
        Product product = findProduct(productId);
        Long sellerId = product.getSeller().getId();

        Slice<ProductQna> questions = productQnaRepository.findQuestions(
                product, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));

        List<Long> questionIds = questions.stream().map(ProductQna::getId).toList();

        // 답글은 한 번에 조회해서 질문별로 묶는다. (N+1 방지)
        Map<Long, List<ProductQnaDto>> repliesByQuestionId = questionIds.isEmpty()
                ? Map.of()
                : productQnaRepository.findReplies(questionIds).stream()
                        .collect(Collectors.groupingBy(
                                reply -> reply.getParent().getId(),
                                Collectors.mapping(reply -> ProductQnaDto.of(reply, sellerId), Collectors.toList())
                        ));

        List<ProductQnaDto> qnas = questions.stream()
                .map(question -> ProductQnaDto.of(
                        question, sellerId, repliesByQuestionId.getOrDefault(question.getId(), List.of())))
                .toList();

        return new ProductQnaListResponseDto(qnas, questions.hasNext(), productQnaRepository.findLatestId(product));
    }

    // 폴링용 : after 이후에 새로 작성된 질문/답글만 작성순으로 반환한다.
    public ProductQnaListResponseDto readNewQnas(Long productId, Long after){
        Product product = findProduct(productId);
        Long sellerId = product.getSeller().getId();

        List<ProductQna> newQnas = productQnaRepository.findCreatedAfter(
                product, after, PageRequest.of(0, MAX_POLLING_SIZE + 1));

        boolean hasNext = newQnas.size() > MAX_POLLING_SIZE;

        List<ProductQnaDto> qnas = newQnas.stream()
                .limit(MAX_POLLING_SIZE)
                .map(qna -> ProductQnaDto.of(qna, sellerId))
                .toList();

        Long latestQnaId = qnas.isEmpty() ? after : qnas.getLast().qnaId();

        return new ProductQnaListResponseDto(qnas, hasNext, latestQnaId);
    }

    private Product findProduct(Long productId){
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
