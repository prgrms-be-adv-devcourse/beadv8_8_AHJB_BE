package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.domain.Payment;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 같은 주문 그룹의 "첫" 결제 요청이 동시에 오면 둘 다 Payment 가 없다고 보고 새로 만들려 한다.
 * (결제 요청은 Payment 행을 FOR UPDATE 로 잠그지만, 아직 없는 행은 잠글 수 없다)
 * 늦은 쪽의 INSERT 는 먼저 만든 쪽이 커밋할 때까지 기다렸다가 유니크 제약에 걸려 DataIntegrityViolationException 이 난다.
 *
 * 이 예외가 났다는 것은 먼저 만든 쪽이 이미 커밋했다는 뜻이므로, 한 번 더 실행하면 그 Payment 를 잠그고 읽어
 * 같은 결과(PAID 나 같은 결제창)를 돌려준다. 돈은 유니크 제약이 지켰고, 여기서는 500 대신 멱등 응답이 나가게만 한다.
 *
 * 제약 위반은 트랜잭션을 못 쓰게 만들므로, @Transactional 메서드 바깥(파사드를 부르는 쪽)에서 이걸로 감싼다.
 * Payment 의 주문 그룹 유니크 제약이 아닌 위반은 코드 버그나 스키마 불일치이므로 그대로 던진다.
 */
@Slf4j
@Component
public class PaymentCreateRaceSupport {

    // 주문 그룹당 Payment 는 하나. idempotency_key 도 주문 그룹 id 로 만들므로 둘 중 어느 제약이 먼저 걸릴지는 DB 가 정한다
    private static final Set<String> RACE_CONSTRAINTS = Set.of(Payment.UK_ORDER_GROUP, Payment.UK_IDEMPOTENCY);

    public <T> T retryOnCreateRace(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException e) {
            if (!isCreateRace(e)) {
                throw e;
            }
            log.info("같은 주문 그룹의 결제 요청이 동시에 들어옴. 먼저 만들어진 결제를 읽어 다시 처리");
            return action.get();      // 다시 실패하면 그대로 올린다 (같은 제약에 두 번 걸릴 수는 없다)
        }
    }

    private static boolean isCreateRace(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && RACE_CONSTRAINTS.contains(name.toLowerCase(Locale.ROOT));
            }
        }
        return false;
    }
}
