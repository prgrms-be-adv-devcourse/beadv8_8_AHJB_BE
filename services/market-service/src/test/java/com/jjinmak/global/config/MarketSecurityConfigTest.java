package com.jjinmak.global.config;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.app.support.AuctionRetrySupport;
import com.jjinmak.boundedcontext.market.auction.in.AuctionApiController;
import com.jjinmak.boundedcontext.market.product.app.ProductFacade;
import com.jjinmak.boundedcontext.market.product.app.ProductQnaFacade;
import com.jjinmak.boundedcontext.market.product.in.ProductApiController;
import com.jjinmak.boundedcontext.market.product.in.ProductQnaApiController;
import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.common.security.CustomAccessDeniedHandler;
import com.jjinmak.common.security.CustomAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 게이트웨이는 토큰이 없으면 회원 헤더 없이 그대로 넘긴다. 어떤 경로를 비로그인에게 열지는 이 설정이 정한다.
@WebMvcTest
class MarketSecurityConfigTest {

    // MarketApplication의 JPA 설정을 띄우지 않도록 필요한 빈만 올린다.
    @SpringBootConfiguration
    @EnableWebSecurity
    @Import({MarketSecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
            ProductApiController.class, ProductQnaApiController.class, AuctionApiController.class})
    static class TestConfig {
    }

    @Autowired MockMvc mockMvc;

    @MockitoBean ProductFacade productFacade;
    @MockitoBean ProductQnaFacade productQnaFacade;
    @MockitoBean AuctionFacade auctionFacade;
    @MockitoBean AuctionRetrySupport auctionRetrySupport;

    @Test
    void 비로그인도_상품_상세를_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk());

        then(productFacade).should().readDetail(null, 1L);
    }

    @Test
    void 로그인하면_회원_id로_상품_상세를_조회한다() throws Exception {
        mockMvc.perform(get("/api/v1/products/1").header(AuthHeaders.MEMBER_ID, "7"))
                .andExpect(status().isOk());

        then(productFacade).should().readDetail(7L, 1L);
    }

    @Test
    void 비로그인도_상품_QnA_목록을_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/products/1/qnas"))
                .andExpect(status().isOk());
    }

    @Test
    void 내_상품_목록은_비로그인이면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/products/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 상품_등록은_비로그인이면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void QnA_작성은_비로그인이면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/products/1/qnas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비로그인도_경매_상세를_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/1"))
                .andExpect(status().isOk());

        then(auctionFacade).should().detail(1L, null);
    }

    @Test
    void 비로그인도_경매_입찰_내역을_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/1/bids"))
                .andExpect(status().isOk());

        then(auctionFacade).should().bidHistory(1L, null, 0, 10);
    }

    @Test
    void 로그인하면_회원_id로_경매_상세를_조회한다() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/1").header(AuthHeaders.MEMBER_ID, "7"))
                .andExpect(status().isOk());

        then(auctionFacade).should().detail(1L, 7L);
    }

    @Test
    void 내_입찰_목록은_비로그인이면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/me/bids"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 입찰은_비로그인이면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/auctions/1/bids").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
