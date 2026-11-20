package com.jjinmak.boundedcontext.market.product.in.seed;

import com.jjinmak.boundedcontext.market.product.domain.ProductAuctionTerms;
import com.jjinmak.boundedcontext.market.product.domain.ProductImageFormat;
import com.jjinmak.boundedcontext.market.product.domain.ShippingFeeType;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.contracts.product.domain.Manufacturer;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// 운영에서 등록하다 실패하지 않도록, 시드 데이터 전체를 실제 등록 규칙으로 미리 검사한다.
class ProductSeedDataTest {

    private static final Long IMAGE_ID = 1L;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final List<ProductSeedData> seeds = ProductSeedScheduler.loadSeeds();

    @Test
    void 모든_시드_상품이_등록_요청_검증을_통과한다() {
        assertThat(seeds).hasSizeGreaterThan(100);

        for (ProductSeedData seed : seeds) {
            ProductCreateRequestDto request = seed.toRequest(IMAGE_ID);
            assertThat(validator.validate(request)).as(seed.name()).isEmpty();
        }
    }

    @Test
    void 모든_시드_상품이_경매_조건_규칙을_통과한다() {
        LocalDateTime scheduledAt = LocalDateTime.now();

        for (ProductSeedData seed : seeds) {
            assertThatCode(() -> ProductAuctionTerms.create(seed.startingPrice(), seed.instantWinPrice(),
                    seed.duration(), null, scheduledAt))
                    .as(seed.name()).doesNotThrowAnyException();
        }
    }

    @Test
    void 제조사가_기타일_때만_직접_입력_이름이_있다() {
        for (ProductSeedData seed : seeds) {
            boolean hasEtcName = seed.manufacturerEtc() != null && !seed.manufacturerEtc().isBlank();
            assertThat(hasEtcName).as(seed.name()).isEqualTo(seed.manufacturer() == Manufacturer.ETC);
        }
    }

    @Test
    void 배송비가_있으면_직접_입력_배송이고_없으면_무료배송이다() {
        ProductSeedData seed = seeds.getFirst();
        ProductSeedData paid = new ProductSeedData(seed.name(), seed.category(), seed.manufacturer(),
                seed.manufacturerEtc(), seed.description(), seed.startingPrice(), seed.instantWinPrice(),
                seed.duration(), 4000L);
        ProductSeedData free = new ProductSeedData(seed.name(), seed.category(), seed.manufacturer(),
                seed.manufacturerEtc(), seed.description(), seed.startingPrice(), seed.instantWinPrice(),
                seed.duration(), null);

        assertThat(paid.toRequest(IMAGE_ID).shipping().feeType()).isEqualTo(ShippingFeeType.CUSTOM);
        assertThat(paid.toRequest(IMAGE_ID).shipping().customFee()).isEqualTo(4000L);
        assertThat(free.toRequest(IMAGE_ID).shipping().feeType()).isEqualTo(ShippingFeeType.FREE);
    }

    @Test
    void 시드_상품은_SEED_정책_버전으로_등록된다() {
        assertThat(seeds.getFirst().toRequest(IMAGE_ID).policyVersion()).isEqualTo(ProductSeedData.POLICY_VERSION);
        assertThat(seeds.getFirst().toRequest(IMAGE_ID).imageIds()).containsExactly(IMAGE_ID);
    }

    @Test
    void 모든_카테고리의_샘플_이미지가_업로드_가능한_형식이다() {
        for (ProductSeedData seed : seeds) {
            byte[] bytes = ProductSeedScheduler.readImage(seed).getBytes();
            assertThat(ProductImageFormat.detect(bytes)).as(seed.imagePath()).contains(ProductImageFormat.PNG);
        }
    }
}
