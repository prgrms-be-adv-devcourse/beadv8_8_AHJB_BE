package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.domain.RecommendAuction;
import com.jjinmak.recommend.domain.RecommendProduct;
import com.jjinmak.recommend.out.RecommendAuctionRepository;
import com.jjinmak.recommend.out.RecommendProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.recommend.domain.AuctionStatus.ENDED;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendSyncUseCase {

    private final RecommendProductRepository recommendProductRepository;
    private final RecommendAuctionRepository recommendAuctionRepository;

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

    public void startAuction(AuctionStartedEvent event){
        recommendAuctionRepository.save(new RecommendAuction(
                event.auctionId(), event.productId(),
                event.instantWinPrice(), event.startPrice(),
                event.startAt(), event.endAt()
        ));
    }

    public void winAuction(AuctionWonEvent event){
        Long auctionId = event.auctionId();

        RecommendAuction auction = recommendAuctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_NOT_FOUND));

        auction.won(event.winningPrice(), event.winAt());
    }

    public void failAuction(AuctionFailedEvent event){
        Long auctionId = event.auctionId();

        RecommendAuction auction = recommendAuctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_NOT_FOUND));

        if (auction.getStatus() == ENDED)
            return;

        auction.fail(event.failedAt());
    }
}
