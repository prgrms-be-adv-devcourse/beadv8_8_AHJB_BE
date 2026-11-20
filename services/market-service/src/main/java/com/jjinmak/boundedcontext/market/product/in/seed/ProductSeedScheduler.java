package com.jjinmak.boundedcontext.market.product.in.seed;

import com.jjinmak.boundedcontext.market.product.app.ProductFacade;
import com.jjinmak.boundedcontext.market.product.app.ProductImageFacade;
import com.jjinmak.boundedcontext.market.product.domain.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

// 추천 확인용: 일정 간격으로 seed/products.json의 상품을 하나씩 실제 등록 흐름으로 등록한다.
// market.seed.enabled=true 일 때만 만들어지고, 판매자의 시드 상품이 maxCount에 도달하면 등록을 멈춘다.
@Slf4j
@Component
@ConditionalOnProperty(name = "market.seed.enabled", havingValue = "true")
@EnableConfigurationProperties(ProductSeedProperties.class)
public class ProductSeedScheduler {

    static final String SEED_FILE = "seed/products.json";

    private final ProductFacade productFacade;
    private final ProductImageFacade productImageFacade;
    private final ProductSeedProperties properties;
    private final List<ProductSeedData> seeds;
    private final Deque<ProductSeedData> queue = new ArrayDeque<>();

    public ProductSeedScheduler(ProductFacade productFacade, ProductImageFacade productImageFacade,
                                ProductSeedProperties properties) {
        if (properties.sellerId() == null) {
            throw new IllegalStateException("market.seed.seller-id 가 필요합니다.");
        }
        this.productFacade = productFacade;
        this.productImageFacade = productImageFacade;
        this.properties = properties;
        this.seeds = loadSeeds();
        log.info("상품 자동 등록 켜짐 sellerId={}, maxCount={}, 데이터 {}개",
                properties.sellerId(), properties.maxCount(), seeds.size());
    }

    @Scheduled(fixedDelayString = "${market.seed.interval:300000}",
               initialDelayString = "${market.seed.initial-delay:60000}")
    public void seed() {
        Long sellerId = properties.sellerId();
        try {
            long seeded = productFacade.countBySellerAndPolicyVersion(sellerId, ProductSeedData.POLICY_VERSION);
            if (seeded >= properties.maxCount()) {
                return;
            }

            ProductSeedData data = next();
            Long imageId = productImageFacade.upload(sellerId, readImage(data)).imageId();
            Product product = productFacade.createProduct(sellerId, data.toRequest(imageId));

            log.info("시드 상품 등록 productId={}, name={}, {}/{}",
                    product.getId(), data.name(), seeded + 1, properties.maxCount());
        } catch (Exception e) {
            // 한 번 실패해도 다음 주기에 다시 시도한다. (업로드만 되고 남은 이미지는 이미지 정리 스케줄러가 지운다)
            log.warn("시드 상품 등록 실패 sellerId={}", sellerId, e);
        }
    }

    // 전체를 섞어서 한 바퀴 다 쓴 뒤에 다시 섞는다. (같은 상품이 연달아 나오지 않게)
    private ProductSeedData next() {
        if (queue.isEmpty()) {
            List<ProductSeedData> shuffled = new ArrayList<>(seeds);
            Collections.shuffle(shuffled);
            queue.addAll(shuffled);
        }
        return queue.poll();
    }

    static List<ProductSeedData> loadSeeds() {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            List<ProductSeedData> seeds = JsonMapper.builder().build().readValue(in, new TypeReference<>() {});
            if (seeds.isEmpty()) {
                throw new IllegalStateException(SEED_FILE + " 에 상품이 없습니다.");
            }
            return List.copyOf(seeds);
        } catch (IOException e) {
            throw new UncheckedIOException(SEED_FILE + " 을 읽을 수 없습니다.", e);
        }
    }

    static SeedImageFile readImage(ProductSeedData data) {
        try (InputStream in = new ClassPathResource(data.imagePath()).getInputStream()) {
            return new SeedImageFile(data.category().name().toLowerCase() + ".png", in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(data.imagePath() + " 을 읽을 수 없습니다.", e);
        }
    }
}
