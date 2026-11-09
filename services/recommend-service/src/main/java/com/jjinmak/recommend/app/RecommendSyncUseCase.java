package com.jjinmak.recommend.app;

import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.domain.RecommendProduct;
import com.jjinmak.recommend.out.RecommendProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendSyncUseCase {

    private final RecommendProductRepository recommendProductRepository;

    public void syncProduct(ProductChangedEvent event){
        Long productId = event.productId();

        RecommendProduct product = recommendProductRepository.findById(productId)
                .orElse(null);

        if (product != null && event.version() < product.getVersion()){
            return;
        }

        if (product == null){
            product = recommendProductRepository.save(new RecommendProduct(
                    event.productId(), event.version(), event.sellerId(),
                    event.productName(), event.category(),
                    event.manufacturer(), event.thumbnailUrl()
            ));
        }
        else {
            product.sync(
                    event.version(), event.sellerId(),
                    event.productName(), event.category(),
                    event.manufacturer(), event.thumbnailUrl()
            );
        }

        if (event.changeType() == ProductChangeType.DELETED){
            product.delete();
        }
    }
}
