package com.jjinmak.search.in;

import com.jjinmak.common.rsData.RsData;
import com.jjinmak.search.app.ProductSearchUseCase;
import com.jjinmak.search.in.dto.ProductSearchListResponseDto;
import com.jjinmak.search.in.dto.ProductSearchRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class SearchApiController {

    private final ProductSearchUseCase productSearchUseCase;

    // 상품 검색 (비로그인 조회 가능). 조건은 모두 쿼리 스트링으로 받는다.
    // 예) /api/v1/search?keyword=플스&category=CONSOLE&status=IN_PROGRESS&sort=LATEST&page=0&size=20
    @GetMapping
    public ResponseEntity<RsData<ProductSearchListResponseDto>> search(@Valid @ModelAttribute ProductSearchRequestDto request) {
        ProductSearchListResponseDto response = productSearchUseCase.search(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }
}
