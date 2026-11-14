package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.recommend.domain.*;
import com.jjinmak.recommend.out.row.AuctionRow;
import com.jjinmak.recommend.out.repository.InteractionRepository;
import com.jjinmak.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.row.InteractionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jjinmak.recommend.exception.RecommendErrorCode.EMBEDDING_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendAuctionUseCase {

    @Value("${spring.ai.openai.embedding.model}")
    private String model;

    private final RecommendSupport recommendSupport;
    private final ProductEmbeddingRepository embeddingRepository;
    private final InteractionRepository interactionRepository;

    public List<AuctionResponseDto> readRecommendedAuctionList(Long auctionId){
        RecommendAuction auction = recommendSupport.findAuctionById(auctionId);

        Long productId = auction.getProductId();

        if (!embeddingRepository.existsById(productId))
            throw new BusinessException(EMBEDDING_NOT_FOUND);

        List<AuctionRow> rows = embeddingRepository.findSimilarAuctions(productId, auctionId, model, 10);

        return rows.stream().map(AuctionResponseDto::from).toList();
    }

    public List<AuctionResponseDto> readPopularAuctionList(){

        List<AuctionRow> rows = interactionRepository.findPopularAuctions();

        return rows.stream().map(AuctionResponseDto::from).toList();
    }

    public List<AuctionResponseDto> readPersonalRecommendedAuctionList(Long memberId){
        List<AuctionRow> rows = findPersonalAuctions(memberId);

        if (rows.size() < 10)
            rows = fillWithPopularAuctions(memberId, rows);

        return rows.stream().map(AuctionResponseDto::from).toList();
    }

    private List<AuctionRow> findPersonalAuctions(Long memberId){
        List<InteractionRow> interactions = interactionRepository.findRecentInteractions(memberId, model);

        if (interactions.isEmpty())
            return List.of();

        LocalDateTime now = LocalDateTime.now();

        List<Long> productIds = interactions.stream()
                .map(InteractionRow::productId)
                .toList();

        List<Double> weights = interactions.stream()
                .map(row -> InteractionType.valueOf(row.type()).weightAt(row.lastOccurredAt(), now))
                .toList();

        return embeddingRepository.findPersonalAuctions(
                memberId, model, toArrayText(productIds), toArrayText(weights)
        );
    }

    private String toArrayText(List<?> values){
        return values.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "{", "}"));
    }

    private List<AuctionRow> fillWithPopularAuctions(Long memberId, List<AuctionRow> rows){
        Set<Long> auctionIds = rows.stream()
                .map(AuctionRow::auctionId)
                .collect(Collectors.toSet());

        List<AuctionRow> popularRows = interactionRepository.findPopularAuctionsForMember(memberId).stream()
                .filter(row -> !auctionIds.contains(row.auctionId()))
                .toList();

        return Stream.concat(rows.stream(), popularRows.stream())
                .limit(10)
                .toList();
    }
}
