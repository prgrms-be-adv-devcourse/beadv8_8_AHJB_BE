package com.jjinmak.back.boundedContext.wallet.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements ErrorCode {

    // ===== 지갑 (Wallet) =====
    WALLET_NOT_FOUND("WALLET001", "존재하지 않는 지갑입니다.", HttpStatus.NOT_FOUND),
    SYSTEM_WALLET_NOT_FOUND("WALLET002", "시스템 지갑(ESCROW/FEE)이 존재하지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    WALLET_FORBIDDEN("WALLET003", "지갑에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),
    WALLET_ALREADY_EXISTS("WALLET004", "이미 지갑이 존재하는 회원입니다.", HttpStatus.BAD_REQUEST),
    INVALID_SYSTEM_WALLET_TYPE("WALLET005", "USER 타입은 시스템 지갑이 될 수 없습니다.", HttpStatus.BAD_REQUEST),

    // ===== 지갑 상태 (WalletStatus) =====
    WALLET_FROZEN("WALLET101", "동결된 지갑입니다.", HttpStatus.FORBIDDEN),
    WALLET_CLOSED("WALLET102", "해지된 지갑입니다.", HttpStatus.FORBIDDEN),
    WALLET_NOT_CHARGEABLE("WALLET103", "충전할 수 없는 상태의 지갑입니다.", HttpStatus.FORBIDDEN),
    WALLET_NOT_SPENDABLE("WALLET104", "결제·출금할 수 없는 상태의 지갑입니다.", HttpStatus.FORBIDDEN),
    WALLET_NOT_DEPOSITABLE("WALLET105", "입금할 수 없는 상태의 지갑입니다.", HttpStatus.FORBIDDEN),
    INVALID_WALLET_STATUS_TRANSITION("WALLET106", "허용되지 않는 지갑 상태 변경입니다.", HttpStatus.BAD_REQUEST),

    // ===== 잔액 / 금액 =====
    INSUFFICIENT_BALANCE("WALLET201", "잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT("WALLET202", "금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_FEE_AMOUNT("WALLET203", "수수료는 0 이상, 홀딩 금액 이하여야 합니다.", HttpStatus.BAD_REQUEST),

    // ===== 홀딩 (Holding) =====
    HOLDING_NOT_FOUND("WALLET301", "존재하지 않는 홀딩입니다.", HttpStatus.NOT_FOUND),
    HOLDING_ALREADY_EXISTS("WALLET302", "이미 홀딩이 존재하는 주문입니다.", HttpStatus.BAD_REQUEST),
    HOLDING_NOT_HELD("WALLET303", "HELD 상태의 홀딩만 처리할 수 있습니다.", HttpStatus.BAD_REQUEST),
    HOLDING_ALREADY_RELEASED("WALLET304", "이미 정산(구매 확정)된 홀딩입니다.", HttpStatus.BAD_REQUEST),
    HOLDING_ALREADY_REFUNDED("WALLET305", "이미 환불된 홀딩입니다.", HttpStatus.BAD_REQUEST),
    SAME_BUYER_AND_SELLER("WALLET306", "구매자와 판매자 지갑이 같을 수 없습니다.", HttpStatus.BAD_REQUEST),

    // ===== 원장 (WalletTransaction) =====
    TRANSACTION_NOT_FOUND("WALLET401", "존재하지 않는 거래 내역입니다.", HttpStatus.NOT_FOUND),
    DUPLICATE_TRANSACTION("WALLET402", "이미 처리된 요청입니다. (idempotencyKey 중복)", HttpStatus.CONFLICT),
    INVALID_TRANSACTION_AMOUNT("WALLET403", "원장 금액은 0일 수 없습니다.", HttpStatus.BAD_REQUEST),
    TRANSACTION_GROUP_NOT_BALANCED("WALLET404", "거래 그룹의 금액 합이 0이 아닙니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // ===== 동시성 =====
    WALLET_CONCURRENT_MODIFICATION("WALLET501", "지갑이 동시에 수정되었습니다. 다시 시도해 주세요.", HttpStatus.CONFLICT)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
