package com.jjinmak.boundedcontext.market.shared.timer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimingWheelTest {

    private static final long TICK = 100;
    private static final long START = 1_000_000;   // 임의의 시작 시각(ms)

    private TimingWheel<String> wheel() {
        return new TimingWheel<>(TICK, 8, START);   // 8칸 × 100ms = 한 바퀴 800ms
    }

    @Test
    @DisplayName("마감 시각 전에는 꺼내지 않고, 마감 시각이 지난 첫 advance에서 꺼낸다")
    void firesAtDeadline() {
        TimingWheel<String> wheel = wheel();
        wheel.schedule("a", START + 350);

        assertThat(wheel.advance(START + 300)).isEmpty();
        assertThat(wheel.advance(START + 399)).containsExactly("a");   // 350이 속한 칸(300~399)을 지날 때
        assertThat(wheel.size()).isZero();
    }

    @Test
    @DisplayName("이미 지난 시각으로 넣으면 다음 advance에서 바로 꺼낸다")
    void pastDeadlineFiresNext() {
        TimingWheel<String> wheel = wheel();
        wheel.advance(START + 500);
        wheel.schedule("late", START + 100);

        assertThat(wheel.advance(START + 600)).containsExactly("late");
    }

    @Test
    @DisplayName("같은 키를 더 늦은 시각으로 다시 넣으면(연장) 옛 시각에는 꺼내지 않고 새 시각에 꺼낸다")
    void rescheduleUsesLatestDeadline() {
        TimingWheel<String> wheel = wheel();
        wheel.schedule("a", START + 200);
        wheel.schedule("a", START + 500);

        assertThat(wheel.advance(START + 300)).isEmpty();
        assertThat(wheel.advance(START + 599)).containsExactly("a");
    }

    @Test
    @DisplayName("같은 키·같은 시각을 여러 번 넣어도 한 번만 꺼낸다")
    void duplicateScheduleFiresOnce() {
        TimingWheel<String> wheel = wheel();
        wheel.schedule("a", START + 200);
        wheel.schedule("a", START + 200);

        assertThat(wheel.advance(START + 1_000)).containsExactly("a");
        assertThat(wheel.advance(START + 2_000)).isEmpty();
    }

    @Test
    @DisplayName("같은 칸에 든 여러 키를 한 번에 꺼낸다 (같은 분 종료 1,000건)")
    void manyKeysInSameSlot() {
        TimingWheel<String> wheel = wheel();
        for (int i = 0; i < 1_000; i++) {
            wheel.schedule("k" + i, START + 250);
        }

        assertThat(wheel.advance(START + 299)).hasSize(1_000);
    }

    @Test
    @DisplayName("한 바퀴보다 먼 시각은 첫 바퀴에서 꺼내지 않고 시각이 지난 바퀴에서 꺼낸다")
    void beyondOneRotation() {
        TimingWheel<String> wheel = wheel();
        wheel.schedule("far", START + 1_250);   // 한 바퀴(800ms) 넘게 뒤

        assertThat(wheel.advance(START + 499)).isEmpty();   // 같은 칸(250 위치)을 첫 바퀴에 지나지만 아직 아님
        assertThat(wheel.advance(START + 1_200)).isEmpty();
        assertThat(wheel.advance(START + 1_299)).containsExactly("far");
    }

    @Test
    @DisplayName("advance가 한 바퀴 넘게 늦어져도 밀린 키를 모두 꺼낸다")
    void catchUpAfterLongPause() {
        TimingWheel<String> wheel = wheel();
        wheel.schedule("a", START + 150);
        wheel.schedule("b", START + 650);

        assertThat(wheel.advance(START + 5_000)).containsExactlyInAnyOrder("a", "b");
    }
}
