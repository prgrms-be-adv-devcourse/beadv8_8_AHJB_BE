package com.jjinmak.back.boundedContext.refund.domain;

public enum RefundReason { // 환불 사유 text보단 enum으로 선택지 주기
    NOT_AS_DESCRIBED,    // 상품 설명과 다름 (예: "미개봉"이라 했는데 개봉품)
    MISSING_COMPONENTS,  // 구성품 누락 (예: 박스 안 지도·설명서 없음)
    DAMAGED,             // 파손·하자 (예: 배송 중 깨짐, 기스)
    NOT_WORKING,         // 작동 불량 (예: 게임기 전원 안 들어옴, 팩 인식 안 됨)
    COUNTERFEIT,         // 가품 의심 (예: 복제 팩)
    OTHER                // 기타 (detail에 직접 작성)
}



