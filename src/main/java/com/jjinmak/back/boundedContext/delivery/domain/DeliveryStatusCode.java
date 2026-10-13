package com.jjinmak.back.boundedContext.delivery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeliveryStatusCode {
    UNKNOWN("상태를 알 수 없음"),
    INFORMATION_RECEIVED("운송장 정보 접수"),
    AT_PICKUP("집하 장소에 있음"),
    IN_TRANSIT("이동 중"),
    OUT_FOR_DELIVERY("배송 출발"),
    ATTEMPT_FAIL("배송 시도 실패"),
    DELIVERED("배송 완료"),
    AVAILABLE_FOR_PICKUP("지정 장소에서 수령 가능"),
    EXCEPTION("확인이 필요한 문제 발생")
    ;

    private final String message;
}
