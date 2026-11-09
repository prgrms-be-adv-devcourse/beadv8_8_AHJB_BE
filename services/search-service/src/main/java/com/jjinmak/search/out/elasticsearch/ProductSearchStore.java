package com.jjinmak.search.out.elasticsearch;

import com.jjinmak.search.domain.ProductSearchDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.jjinmak.search.domain.ProductSearchDocument.INDEX_ALIAS;

// 검색 문서 저장소. 읽고 → 고치고 → 저장하는 사이에 다른 이벤트가 먼저 저장하면 저장이 실패한다.
// (실패하면 Kafka 에러 처리기가 이벤트를 다시 처리한다)
@Component
@RequiredArgsConstructor
public class ProductSearchStore {

    private static final IndexCoordinates INDEX = IndexCoordinates.of(INDEX_ALIAS);

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
}
