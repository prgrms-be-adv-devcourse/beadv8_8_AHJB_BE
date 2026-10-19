package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    // 판매자 미등록, 정지/차단은 PERM_SELL 권한 검사(COMMON003)로 처리하므로 PRODUCT002, 003은 사용하지 않는다.
    PRODUCT_NOT_FOUND("PRODUCT001", "존재하지 않는 상품입니다.", HttpStatus.NOT_FOUND),
    POLICY_NOT_AGREED("PRODUCT004", "판매 정책에 동의하지 않았거나 정책 버전이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_INSTANT_WIN_PRICE("PRODUCT005", "즉시낙찰가는 시작가보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    DIRECT_INPUT_REQUIRED("PRODUCT006", "직접 입력 값이 누락되었습니다.", HttpStatus.BAD_REQUEST),
    NOT_PRODUCT_OWNER("PRODUCT007", "본인이 등록한 상품만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN),
    NOT_EDITABLE("PRODUCT008", "경매 시작 예정 시각이 지난 상품은 수정/삭제할 수 없습니다.", HttpStatus.BAD_REQUEST),
    QNA_NOT_FOUND("PRODUCT009", "존재하지 않는 Q&A입니다.", HttpStatus.NOT_FOUND),
    NOT_QNA_WRITER("PRODUCT010", "본인이 작성한 Q&A만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN),
    INVALID_QNA_PARENT("PRODUCT011", "해당 상품의 질문에만 답글을 작성할 수 있습니다.", HttpStatus.BAD_REQUEST),
    ONLY_SELLER_CAN_REPLY("PRODUCT012", "답글은 상품 판매자만 작성할 수 있습니다.", HttpStatus.FORBIDDEN),
    ALREADY_WISHED("PRODUCT013", "이미 찜한 상품입니다.", HttpStatus.BAD_REQUEST),
    WISH_NOT_FOUND("PRODUCT014", "찜하지 않은 상품입니다.", HttpStatus.NOT_FOUND),
    CANNOT_WISH_OWN_PRODUCT("PRODUCT015", "본인이 등록한 상품은 찜할 수 없습니다.", HttpStatus.BAD_REQUEST),
    INVALID_CUSTOM_END_AT("PRODUCT016", "종료 시각은 경매 시작 후 1일부터 7일 사이로 지정해야 합니다.", HttpStatus.BAD_REQUEST)
    ;
    private final String code;
    private final String message;
    private final HttpStatus status;
}
