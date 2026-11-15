package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.LedgerCheckResult;

import java.time.LocalDate;

/**
 * 원장 대사의 검사 하나. "원장과 잔액 · 홀딩 · 충전 상태가 이 규칙대로 맞는가" 를 확인한다.
 *
 * 구현체를 스프링 빈으로 등록하면 ReconciliationLedgerUseCase 가 모아서 차례로 실행한다.
 * 검사를 추가할 때 유스케이스는 고치지 않는다.
 *
 * 구현 규칙
 *  - DB 는 읽기만 한다. 불일치를 발견해도 돈이나 상태를 고치지 않고 결과로만 돌려준다
 *  - 트랜잭션을 직접 열지 않는다. 파사드가 연 읽기 전용 트랜잭션 안에서 다른 검사와 같은 시점의 데이터를 본다
 *  - 한 검사에서 같은 대상(targetType, targetKey)의 불일치는 1건만 만든다 (저장할 때 유니크 제약에 걸린다)
 *  - 확인하지 못한 경우(조회 실패 등)는 불일치로 만들지 말고 예외를 던진다. 그 실행은 FAILED 로 기록된다
 */
public interface LedgerInvariantChecker {

    /**
     * @param targetDate 대사 대상일. 그날 생긴 기록을 보는 검사만 쓰고, 현재 상태를 보는 검사(잔액 등)는 쓰지 않는다
     */
    LedgerCheckResult check(LocalDate targetDate);
}
