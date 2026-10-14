package com.jjinmak.back.shared.delivery.domain;

import com.jjinmak.back.global.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

import static com.jjinmak.back.global.exception.CommonErrorCode.BAD_REQUEST;

@Getter
@RequiredArgsConstructor
public enum CarrierType {
    CJ("대한통운", "kr.cjlogistics"),
    LOTTE("롯데택배", "kr.lotte"),
    EPOST("우체국택배", "kr.epost"),
    HANJIN("한진택배", "kr.hanjin"),
    LOGEN("로젠택배", "kr.logen"),
    KDEXP("경동택배", "kr.kdexp"),
    HDEXP("합동택배", "kr.hdexp"),
    DAESIN("대신택배", "kr.daesin"),
    ILYANG("일양로지스", "kr.ilyanglogis"),
    KUNYOUNG("건영택배", "kr.kunyoung"),
    CHUNIL("천일택배", "kr.chunilps"),
    HONAM("한서호남택배", "kr.honamlogis"),
    COUPANG("쿠팡 로지스틱스 서비스", "kr.coupangls"),
    CU_POST("CU 편의점택배", "kr.cupost"),
    GS_POSTBOX("GS Postbox", "kr.cvsnet"),
    HOMEPICK("홈픽", "kr.homepick"),
    GOODSTOLUCK("굿투럭", "kr.goodstoluck"),
    DELIBOX("딜리박스", "kr.delibox"),
    YONGMA("용마로지스", "kr.yongmalogis"),
    TODAY_PICKUP("오늘의픽업", "kr.todaypickup"),
    TODAY("투데이", "kr.today"),
    LX_PANTOS("LX 판토스", "kr.epantos"),
    CWAY("CWAY (Woori Express)", "kr.cway"),
    SLX("SLX", "kr.slx"),
    LTL("LTL", "kr.ltl"),
    HPL("HPL (한의사랑택배)", "kr.hanips")
    ;

    private final String korean;
    private final String carrierCode;

    public static CarrierType fromCode(String code){
        return Arrays.stream(CarrierType.values())
                .filter(carrier -> carrier.getCarrierCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(BAD_REQUEST));
    }
}
