package com.jjinmak.boundedcontext.market.product.in.seed;

import com.jjinmak.boundedcontext.market.product.app.ProductFacade;
import com.jjinmak.boundedcontext.market.product.app.ProductImageFacade;
import com.jjinmak.boundedcontext.market.product.domain.Product;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductImageUploadResponseDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class ProductSeedSchedulerTest {

    private static final Long SELLER_ID = 100L;
    private static final Long IMAGE_ID = 7L;

    private final ProductFacade productFacade = mock(ProductFacade.class);
    private final ProductImageFacade productImageFacade = mock(ProductImageFacade.class);

    private ProductSeedScheduler 스케줄러(int maxCount) {
        return new ProductSeedScheduler(productFacade, productImageFacade,
                new ProductSeedProperties(true, SELLER_ID, maxCount));
    }

    @Test
    void 이미지를_올리고_그_이미지로_상품을_등록한다() {
        given(productFacade.countBySellerAndPolicyVersion(SELLER_ID, ProductSeedData.POLICY_VERSION)).willReturn(0L);
        given(productImageFacade.upload(eq(SELLER_ID), any())).willReturn(new ProductImageUploadResponseDto(IMAGE_ID, "url"));
        given(productFacade.createProduct(eq(SELLER_ID), any())).willReturn(mock(Product.class));

        스케줄러(300).seed();

        ArgumentCaptor<ProductCreateRequestDto> captor = ArgumentCaptor.forClass(ProductCreateRequestDto.class);
        then(productFacade).should().createProduct(eq(SELLER_ID), captor.capture());
        assertThat(captor.getValue().imageIds()).containsExactly(IMAGE_ID);
        assertThat(captor.getValue().policyVersion()).isEqualTo(ProductSeedData.POLICY_VERSION);
    }

    @Test
    void 시드_상품이_최대_개수만큼_쌓이면_등록하지_않는다() {
        given(productFacade.countBySellerAndPolicyVersion(SELLER_ID, ProductSeedData.POLICY_VERSION)).willReturn(300L);

        스케줄러(300).seed();

        then(productImageFacade).shouldHaveNoInteractions();
        then(productFacade).should(never()).createProduct(any(), any());
    }

    @Test
    void 등록에_실패해도_예외를_밖으로_던지지_않는다() {
        given(productFacade.countBySellerAndPolicyVersion(SELLER_ID, ProductSeedData.POLICY_VERSION)).willReturn(0L);
        given(productImageFacade.upload(eq(SELLER_ID), any())).willThrow(new IllegalStateException("업로드 실패"));

        assertThatCode(() -> 스케줄러(300).seed()).doesNotThrowAnyException();
        then(productFacade).should(never()).createProduct(any(), any());
    }

    @Test
    void 판매자_id가_없으면_켜지지_않는다() {
        assertThatThrownBy(() -> new ProductSeedScheduler(productFacade, productImageFacade,
                new ProductSeedProperties(true, null, 300)))
                .isInstanceOf(IllegalStateException.class);
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ProductFacade.class, () -> productFacade)
            .withBean(ProductImageFacade.class, () -> productImageFacade)
            .withUserConfiguration(ProductSeedScheduler.class);

    @Test
    void 설정이_없으면_스케줄러가_만들어지지_않는다() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(ProductSeedScheduler.class));
    }

    @Test
    void 설정으로_켜면_스케줄러가_만들어진다() {
        contextRunner
                .withPropertyValues("market.seed.enabled=true", "market.seed.seller-id=100")
                .run(context -> assertThat(context).hasSingleBean(ProductSeedScheduler.class));
    }
}
