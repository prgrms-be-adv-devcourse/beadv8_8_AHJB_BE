package com.jjinmak.ai.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
import com.jjinmak.ai.recommend.app.RecommendEmbeddingUseCase;
import com.jjinmak.ai.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.ai.recommend.app.dto.SearchRequestDto;
import com.jjinmak.ai.recommend.app.dto.SearchResponseDto;
import com.jjinmak.ai.recommend.domain.SearchConditions;
import com.jjinmak.ai.recommend.out.llm.RecommendSearchLlmClient;
import com.jjinmak.ai.recommend.out.llm.SearchConditionResult;
import com.jjinmak.ai.recommend.out.repository.InteractionRepository;
import com.jjinmak.ai.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.ai.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.ai.recommend.out.row.SearchAuctionRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendSearchUseCase {

    static final int MAX_QUERY_LENGTH = 100;
    static final String GUIDANCE_MESSAGE = "경매 찾기만 도와드릴 수 있어요. 찾는 상품이나 가격 조건을 말씀해 주세요.";
    static final String NO_RESULT_SUMMARY = "조건에 맞는 진행 중 경매가 없어요.";

    @Value("${spring.ai.openai.embedding.model}")
    private String model;

    private final RecommendSearchRateLimiter rateLimiter;
    private final RecommendSearchLlmClient llmClient;
    private final RecommendEmbeddingUseCase embeddingUseCase;
    private final RecommendProductRepository recommendProductRepository;
    private final ProductEmbeddingRepository embeddingRepository;
    private final InteractionRepository interactionRepository;

    public SearchResponseDto search(Long memberId, SearchRequestDto request){
        validate(request);
        rateLimiter.check(memberId);

        String question = request.query().strip();
        SearchConditions previous = request.conditions();

        SearchConditionResult extracted = extractConditions(question, previous);

        if (extracted != null && !extracted.recommendation())
            return new SearchResponseDto(previous, GUIDANCE_MESSAGE, List.of());

        SearchConditions conditions = extracted != null ? extracted.toConditions() : previous;
        String queryText = conditions != null ? conditions.createQueryText() : question;

        List<SearchAuctionRow> rows = findAuctions(memberId, conditions, queryText);

        String summary = rows.isEmpty() ? NO_RESULT_SUMMARY : summarize(question, conditions, rows);

        return new SearchResponseDto(conditions, summary, rows.stream().map(AuctionResponseDto::from).toList());
    }

    private void validate(SearchRequestDto request){
        String query = request.query();
        if (query == null || query.isBlank() || query.strip().length() > MAX_QUERY_LENGTH)
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
    }

    private SearchConditionResult extractConditions(String question, SearchConditions previous){
        try {
            return llmClient.extractConditions(
                    question, previous,
                    recommendProductRepository.findDistinctCategories(),
                    recommendProductRepository.findDistinctManufacturers()
            );
        } catch (RuntimeException e) {
            log.warn("검색 조건 정하기 실패. 앞 조건을 그대로 씁니다. question={}", question, e);
            return null;
        }
    }

    private List<SearchAuctionRow> findAuctions(Long memberId, SearchConditions conditions, String queryText){
        SearchConditions filters = conditions != null ? conditions : SearchConditions.none();

        String vectorText = queryText == null ? null : embedQuery(queryText);

        if (vectorText == null)
            return interactionRepository.findPopularAuctionsByConditions(
                    memberId, filters.maxPrice(), filters.minPrice(), filters.category(), filters.manufacturer()
            );

        return embeddingRepository.findAuctionsBySimilarity(
                memberId, model, filters.maxPrice(), filters.minPrice(),
                filters.category(), filters.manufacturer(), vectorText
        );
    }

    private String embedQuery(String queryText){
        try {
            return embeddingUseCase.embedToVectorText(queryText);
        } catch (RuntimeException e) {
            log.warn("질의 임베딩 실패. 인기 순으로 고릅니다. queryText={}", queryText, e);
            return null;
        }
    }

    private String summarize(String question, SearchConditions conditions, List<SearchAuctionRow> rows){
        try {
            return llmClient.summarize(question, conditions, rows);
        } catch (RuntimeException e) {
            log.warn("요약 실패. 요약 없이 응답합니다. question={}", question, e);
            return null;
        }
    }
}
