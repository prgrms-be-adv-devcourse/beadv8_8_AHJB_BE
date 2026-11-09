package com.jjinmak.recommend.app;

import com.jjinmak.contracts.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendFacade {

    private final RecommendSyncUseCase recommendSyncUseCase;

    @Transactional
    public void syncProduct(ProductChangedEvent event){
        recommendSyncUseCase.syncProduct(event);
    }
}
