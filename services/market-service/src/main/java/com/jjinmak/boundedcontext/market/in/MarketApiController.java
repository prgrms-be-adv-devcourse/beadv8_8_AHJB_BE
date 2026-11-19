package com.jjinmak.boundedcontext.market.in;

import com.jjinmak.boundedcontext.market.app.MarketFacade;
import com.jjinmak.boundedcontext.market.in.dto.MarketRelistResponseDto;
import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

// 상품과 경매를 함께 바꾸는 API. 경로는 프론트·게이트웨이와 맞춰 상품 아래에 둔다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class MarketApiController {

    private final MarketFacade marketFacade;

    // 유찰되었거나 미결제로 무효가 된 상품을 다음 회차 경매로 바로 연다. (판매자 본인, 영국식만)
    @PreAuthorize("hasAuthority('PERM_SELL')")
    @PostMapping("/{productId}/rounds")
    public ResponseEntity<RsData<MarketRelistResponseDto>> relist(@AuthenticationPrincipal AuthPrincipal principal,
                                                                  @PathVariable Long productId) {
        MarketRelistResponseDto response = marketFacade.relist(principal.memberId(), productId, LocalDateTime.now());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }
}
