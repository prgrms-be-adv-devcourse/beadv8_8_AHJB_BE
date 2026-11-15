package com.jjinmak.recommend.app;

import com.jjinmak.recommend.domain.InteractionType;
import com.jjinmak.recommend.out.repository.ProductCfSimilarityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendCfSimilarityUseCase {

    private static final long LOCK_KEY = "product_cf_similarity".hashCode();
    private static final int MIN_CO_COUNT = 2;

    private final ProductCfSimilarityRepository cfSimilarityRepository;

    public void refreshCfSimilarity(){
        if (!cfSimilarityRepository.tryLock(LOCK_KEY))
            return;

        cfSimilarityRepository.deleteAllInBatch();
        cfSimilarityRepository.insertCfSimilarities(
                InteractionType.WIN.getWeight(),
                InteractionType.BID.getWeight(),
                InteractionType.WISH.getWeight(),
                MIN_CO_COUNT
        );
    }
}
