package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.domain.PendingInteraction;
import com.jjinmak.recommend.domain.ProductEmbedding;
import com.jjinmak.recommend.domain.RecommendAuction;
import com.jjinmak.recommend.domain.RecommendProduct;
import com.jjinmak.recommend.out.repository.PendingInteractionRepository;
import com.jjinmak.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.repository.RecommendAuctionRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.List;

import static com.jjinmak.recommend.domain.AuctionStatus.ENDED;
import static com.jjinmak.recommend.exception.RecommendErrorCode.*;

@Service
@RequiredArgsConstructor
public class RecommendSyncUseCase {

    private final RecommendProductRepository recommendProductRepository;
    private final RecommendAuctionRepository recommendAuctionRepository;
    private final ProductEmbeddingRepository embeddingRepository;
    private final PendingInteractionRepository pendingInteractionRepository;
    private final RecommendSupport recommendSupport;

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

            String inputText = product.createInputText();
            String textHash = encryptSHA256(inputText);

            embeddingRepository.save(new ProductEmbedding(productId, textHash));
        }
        else {
            product.sync(
                    event.version(), event.sellerId(),
                    event.productName(), event.category(),
                    event.manufacturer(), event.thumbnailUrl()
            );

            ProductEmbedding embedding = embeddingRepository.findById(productId)
                    .orElseThrow(() -> new BusinessException(EMBEDDING_NOT_FOUND));

            String inputText = product.createInputText();
            String textHash = encryptSHA256(inputText);

            if (!textHash.equals(embedding.getTextHash()))
                embedding.reset(textHash);
        }

        if (event.changeType() == ProductChangeType.DELETED){
            product.delete();
        }
    }

    public void startAuction(AuctionStartedEvent event){
        Long auctionId = event.auctionId();
        Long productId = event.productId();

        recommendAuctionRepository.save(new RecommendAuction(
                auctionId, event.productId(),
                event.instantWinPrice(), event.startPrice(),
                event.startAt(), event.endAt()
        ));

        List<PendingInteraction> pendingInteractions = pendingInteractionRepository.findAllByProductIdOrderByOccurredAt(productId);

        for (PendingInteraction pendingInteraction : pendingInteractions){
            recommendSupport.updateWish(
                    pendingInteraction.getMemberId(), productId, auctionId,
                    pendingInteraction.getChangeType(), pendingInteraction.getOccurredAt()
            );
            pendingInteractionRepository.delete(pendingInteraction);
        }
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

    private String encryptSHA256(String s){
        String res = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(s.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            res = hexString.toString();

        } catch (Exception e) {
            throw new BusinessException(HASH_FAIL);
        }
        return res;
    }
}
