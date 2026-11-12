package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.recommend.domain.EmbeddingStatus;
import com.jjinmak.recommend.domain.ProductEmbedding;
import com.jjinmak.recommend.domain.RecommendProduct;
import com.jjinmak.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;

import static com.jjinmak.recommend.exception.RecommendErrorCode.EMBEDDING_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.PRODUCT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendEmbeddingUseCase {

    private final TransactionTemplate transactionTemplate;
    private final RecommendProductRepository recommendProductRepository;
    @Value("${spring.ai.openai.embedding.model}")
    private String model;

    private final EmbeddingModel embeddingModel;
    private final ProductEmbeddingRepository embeddingRepository;

    public List<Long> findPendingEmbedding(){
        return embeddingRepository.findAllByStatusAndModel(EmbeddingStatus.PENDING, model);
    }

    public void requestEmbedding(Long productId){
        RecommendProduct product = recommendProductRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND));

        float[] embeddingResult = getEmbedding(product.createInputText());

        transactionTemplate.executeWithoutResult(status -> {
            ProductEmbedding embedding = embeddingRepository.findById(productId)
                    .orElseThrow(() -> new BusinessException(EMBEDDING_NOT_FOUND));
            embedding.embedded(model);
            embeddingRepository.updateEmbedding(productId, toVectorText(embeddingResult));
        });
    }

    private float[] getEmbedding(String text){
        EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of(text));
        return embeddingResponse.getResult().getOutput();
    }

    private String toVectorText(float[] embedding){
        double sum = 0;
        for (float value : embedding) {
            sum += (double) value * value;
        }
        double norm = Math.sqrt(sum);
        float[] normalized = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            normalized[i] = (float) (embedding[i] / norm);
        }
        return Arrays.toString(normalized);
    }
}
