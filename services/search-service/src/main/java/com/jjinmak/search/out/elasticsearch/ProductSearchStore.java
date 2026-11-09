package com.jjinmak.search.out.elasticsearch;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.jjinmak.search.domain.ProductSearchCondition;
import com.jjinmak.search.domain.ProductSearchDocument;
import com.jjinmak.search.domain.ProductSearchResult;
import com.jjinmak.search.domain.ProductSearchSort;
import com.jjinmak.search.domain.ProductSearchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.jjinmak.search.domain.ProductSearchDocument.INDEX_ALIAS;

// 검색 문서 저장소.
// - 저장: 읽고 → 고치고 → 저장하는 사이에 다른 이벤트가 먼저 저장하면 저장이 실패한다.
//   (실패하면 Kafka 에러 처리기가 이벤트를 다시 처리한다)
// - 검색: 검색어는 점수를 매겨 찾고(must), 나머지 조건은 점수 없이 거르기만 한다(filter).
@Component
@RequiredArgsConstructor
public class ProductSearchStore {

    private static final IndexCoordinates INDEX = IndexCoordinates.of(INDEX_ALIAS);

    // ES는 기본적으로 앞에서부터 10,000개까지만 페이지로 넘겨볼 수 있다. (index.max_result_window)
    private static final long MAX_RESULT_WINDOW = 10_000;

    // 검색어가 상품명에서 맞으면 제조사명에서 맞을 때보다 점수를 3배 준다.
    private static final List<String> KEYWORD_FIELDS = List.of("productName^3", "manufacturerName");

    private final ElasticsearchOperations operations;

    public Optional<ProductSearchDocument> findById(Long productId) {
        return Optional.ofNullable(operations.get(String.valueOf(productId), ProductSearchDocument.class, INDEX));
    }

    public void save(ProductSearchDocument document) {
        IndexQueryBuilder builder = new IndexQueryBuilder()
                .withId(String.valueOf(document.getProductId()))
                .withObject(document);

        if (document.isNew()) {
            // 새 문서: 그 사이 다른 이벤트가 먼저 만들었으면 덮어쓰지 않고 실패한다.
            builder.withOpType(IndexQuery.OpType.CREATE);
        } else {
            // 기존 문서: 내가 읽은 뒤 바뀌었으면 실패한다.
            builder.withSeqNoPrimaryTerm(document.getSeqNoPrimaryTerm());
        }

        operations.index(builder.build(), INDEX);
    }

    public ProductSearchResult search(ProductSearchCondition condition) {
        // 너무 뒤 페이지는 ES가 에러를 내므로 빈 결과로 돌려준다.
        if (condition.offset() + condition.size() > MAX_RESULT_WINDOW) {
            return ProductSearchResult.empty();
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(buildQuery(condition))
                .withSort(buildSort(condition))
                .withPageable(PageRequest.of(condition.page(), condition.size()))
                .withTrackTotalHits(true)   // 10,000개가 넘어도 전체 개수를 정확히 센다
                .build();

        SearchHits<ProductSearchDocument> hits = operations.search(query, ProductSearchDocument.class, INDEX);

        List<ProductSearchDocument> documents = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        return new ProductSearchResult(documents, hits.getTotalHits());
    }

    // ── 찾기(must) + 거르기(filter) ──
    private Query buildQuery(ProductSearchCondition condition) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        // 검색어: 상품명·제조사명에서 찾고 얼마나 잘 맞는지 점수를 매긴다.
        // 검색할 때 동의어 분석기가 돌아서 "플스"로도 "플레이스테이션"이 찾아진다.
        if (condition.hasKeyword()) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(condition.keyword())
                    .fields(KEYWORD_FIELDS)));
        }

        // 항상 거른다: 삭제된 상품, 아직 상품 정보가 없는 문서(경매 이벤트만 먼저 온 경우)
        bool.filter(f -> f.term(t -> t.field("deleted").value(false)));
        bool.filter(f -> f.exists(e -> e.field("productName")));

        // 값이 있을 때만 거른다.
        if (condition.category() != null) {
            bool.filter(f -> f.term(t -> t.field("category").value(condition.category().name())));
        }
        if (condition.manufacturer() != null) {
            bool.filter(f -> f.term(t -> t.field("manufacturer").value(condition.manufacturer().name())));
        }
        if (condition.status() != null) {
            bool.filter(f -> f.term(t -> t.field("status").value(condition.status().name())));
        }
        if (condition.minPrice() != null || condition.maxPrice() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> {
                n.field("startPrice");
                if (condition.minPrice() != null) {
                    n.gte(condition.minPrice().doubleValue());
                }
                if (condition.maxPrice() != null) {
                    n.lte(condition.maxPrice().doubleValue());
                }
                return n;
            })));
        }

        // 마감 임박순은 진행 중인 경매끼리만 비교한다.
        // (이미 끝난 경매는 마감 시각이 과거라서 맨 앞에 와버리기 때문)
        if (condition.sort() == ProductSearchSort.ENDING_SOON) {
            bool.filter(f -> f.term(t -> t.field("status").value(ProductSearchStatus.IN_PROGRESS.name())));
        }

        return bool.build()._toQuery();
    }

    // ── 정렬 ──
    // 정렬 값이 같으면 순서가 매번 바뀌어 페이지를 넘길 때 중복·누락이 생기므로, 마지막에 상품 id로 한 번 더 정렬한다.
    private List<SortOptions> buildSort(ProductSearchCondition condition) {
        SortOptions primary = switch (condition.sort()) {
            case RELEVANCE -> SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
            case LATEST -> fieldSort("registeredAt", SortOrder.Desc);
            case ENDING_SOON -> fieldSort("auctionEndAt", SortOrder.Asc);
            case PRICE_ASC -> fieldSort("startPrice", SortOrder.Asc);
            case PRICE_DESC -> fieldSort("startPrice", SortOrder.Desc);
        };
        return List.of(primary, fieldSort("productId", SortOrder.Desc));
    }

    // 값이 없는 문서(예: 경매 시작 전이라 마감 시각이 없음)는 맨 뒤로 보낸다.
    private SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(s -> s.field(f -> f.field(field).order(order).missing("_last")));
    }
}
