package com.jjinmak.search.out.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

import static com.jjinmak.search.domain.ProductSearchDocument.INDEX_ALIAS;
import static com.jjinmak.search.domain.ProductSearchDocument.INDEX_NAME;

// 서비스가 뜰 때 검색 인덱스(product_search_v1)와 별칭(product_search)이 없으면 만든다.
// - 이미 있으면 건드리지 않는다. (매핑 변경은 새 버전 인덱스를 만들어 별칭을 옮기는 방식으로)
// - ES에 연결할 수 없으면 서비스 기동이 실패한다. (검색 서비스는 ES 없이는 동작할 수 없다)
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchIndexInitializer implements ApplicationRunner {

    private static final String SETTINGS_PATH = "elasticsearch/product-search-settings.json";
    private static final String MAPPINGS_PATH = "elasticsearch/product-search-mappings.json";

    private final ElasticsearchClient client;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (client.indices().existsAlias(a -> a.name(INDEX_ALIAS)).value()) {
            log.info("검색 인덱스 별칭이 이미 있음: {}", INDEX_ALIAS);
            return;
        }

        try (InputStream settings = new ClassPathResource(SETTINGS_PATH).getInputStream();
             InputStream mappings = new ClassPathResource(MAPPINGS_PATH).getInputStream()) {

            client.indices().create(c -> c
                    .index(INDEX_NAME)
                    .settings(s -> s.withJson(settings))
                    .mappings(m -> m.withJson(mappings))
                    .aliases(INDEX_ALIAS, a -> a.isWriteIndex(true)));

            log.info("검색 인덱스 생성: {} (별칭 {})", INDEX_NAME, INDEX_ALIAS);
        } catch (ElasticsearchException e) {
            // 서비스 여러 대가 동시에 뜨면 다른 쪽이 먼저 만들 수 있다.
            if ("resource_already_exists_exception".equals(e.error().type())) {
                log.info("다른 인스턴스가 먼저 검색 인덱스를 생성함: {}", INDEX_NAME);
                return;
            }
            throw e;
        }
    }
}
