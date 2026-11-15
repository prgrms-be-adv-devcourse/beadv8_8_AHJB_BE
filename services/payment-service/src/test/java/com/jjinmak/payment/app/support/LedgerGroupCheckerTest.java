package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.domain.enums.WalletTransactionType;
import com.jjinmak.payment.out.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.jjinmak.payment.domain.enums.MismatchType.LEDGER_GROUP_UNBALANCED;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.CHARGE_CANCEL;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 원장 대사 검사: 거래 그룹의 금액 합 == 0.
 * 합이 0 이 아닌 그룹을 고르는 것은 DB 쿼리이고, 여기서는 조회 조건과 결과를 불일치로 옮기는 부분을 본다.
 */
class LedgerGroupCheckerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 11, 14);

    private LedgerGroupChecker checker;

    private long groupCount;
    private final List<WalletTransactionRepository.GroupSum> unbalanced = new ArrayList<>();
    // 조회할 때 넘어온 조건
    private final List<Collection<WalletTransactionType>> askedExcludedTypes = new ArrayList<>();
    private final List<LocalDateTime> askedStartAts = new ArrayList<>();
    private final List<LocalDateTime> askedEndAts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        WalletTransactionRepository repository = mock(WalletTransactionRepository.class);
        when(repository.countGroupsCreatedBetween(any(), any(), any())).thenAnswer(invocation -> {
            askedExcludedTypes.add(invocation.getArgument(0));
            askedStartAts.add(invocation.getArgument(1));
            askedEndAts.add(invocation.getArgument(2));
            return groupCount;
        });
        when(repository.findUnbalancedGroupsCreatedBetween(any(), any(), any())).thenAnswer(invocation -> {
            askedExcludedTypes.add(invocation.getArgument(0));
            askedStartAts.add(invocation.getArgument(1));
            askedEndAts.add(invocation.getArgument(2));
            return new ArrayList<>(unbalanced);
        });
        checker = new LedgerGroupChecker(repository);
    }

    private static WalletTransactionRepository.GroupSum group(String groupId, long total) {
        return new WalletTransactionRepository.GroupSum() {
            @Override
            public String getGroupId() {
                return groupId;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    @Test
    @DisplayName("합이 0 이 아닌 그룹이 없으면 일치. 비교 건수는 대상일에 기록이 생긴 그룹 수")
    void matched() {
        groupCount = 40;

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(40);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("대상일 0시부터 다음 날 0시 전까지를 보고, 한 줄만 남는 충전 · 충전 취소 · 출금은 뺀다")
    void queriesTargetDateWithoutExternalTypes() {
        checker.check(TARGET_DATE);

        assertThat(askedStartAts).containsExactly(TARGET_DATE.atStartOfDay(), TARGET_DATE.atStartOfDay());
        assertThat(askedEndAts).containsExactly(TARGET_DATE.plusDays(1).atStartOfDay(), TARGET_DATE.plusDays(1).atStartOfDay());
        assertThat(askedExcludedTypes).hasSize(2);
        assertThat(askedExcludedTypes.get(0)).containsExactly(CHARGE, CHARGE_CANCEL, WITHDRAWAL);
        assertThat(askedExcludedTypes.get(1)).containsExactly(CHARGE, CHARGE_CANCEL, WITHDRAWAL);
    }

    @Test
    @DisplayName("합이 0 이 아닌 그룹은 그룹마다 불일치 1건. 기준은 0, 우리 값은 그룹의 금액 합")
    void mismatched() {
        groupCount = 40;
        unbalanced.add(group("3f2a9c1e-0000-4000-8000-000000000001", -30_000));
        unbalanced.add(group("3f2a9c1e-0000-4000-8000-000000000002", 500));

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(40);
        assertThat(result.mismatches()).hasSize(2);

        DetectedMismatchDto first = result.mismatches().get(0);
        assertThat(first.type()).isEqualTo(LEDGER_GROUP_UNBALANCED);
        assertThat(first.targetType()).isEqualTo(MismatchTargetType.LEDGER_GROUP);
        assertThat(first.targetKey()).isEqualTo("3f2a9c1e-0000-4000-8000-000000000001");
        assertThat(first.pgOrderId()).isNull();
        assertThat(first.expectedAmount()).isEqualTo(0L);
        assertThat(first.actualAmount()).isEqualTo(-30_000L);
        assertThat(first.detail()).contains("-30000");

        assertThat(result.mismatches().get(1).actualAmount()).isEqualTo(500L);
    }
}
