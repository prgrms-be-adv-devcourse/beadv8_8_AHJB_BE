package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.recommend.domain.ProductEmbedding;
import com.jjinmak.recommend.domain.RecommendAuction;
import com.jjinmak.recommend.out.AuctionRow;
import com.jjinmak.recommend.out.ProductEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jjinmak.recommend.exception.RecommendErrorCode.EMBEDDING_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendAuctionUseCase {

    @Value("${spring.ai.openai.embedding.model}")
    private String model;

    private final RecommendSupport recommendSupport;
    private final ProductEmbeddingRepository embeddingRepository;

    public List<AuctionResponseDto> readRecommendedAuctionList(Long auctionId){
        RecommendAuction auction = recommendSupport.findAuctionById(auctionId);

        Long productId = auction.getProductId();

        ProductEmbedding embedding = embeddingRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(EMBEDDING_NOT_FOUND));

        List<AuctionRow> rows = embeddingRepository.findSimilarAuctions(productId, auctionId, model, 10);

        return rows.stream().map(AuctionResponseDto::from).toList();
    }
}
