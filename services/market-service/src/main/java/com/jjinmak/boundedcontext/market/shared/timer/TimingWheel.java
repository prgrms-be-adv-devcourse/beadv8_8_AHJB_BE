package com.jjinmak.boundedcontext.market.shared.timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 해시 타이밍 휠 (#548 4차, market 공통 부품).
 *
 * <p>시간을 tickMs 단위 칸으로 나눈 고리(ring)다. 키를 마감 시각이 속한 칸에 넣어 두고, {@link #advance}가 지난 칸들을 돌며
 * 마감 시각이 지난 키를 꺼낸다. 칸마다 확인하므로 키가 많아도 "지금 끝나는 것"만 본다.
 *
 * <ul>
 *   <li>같은 키를 다른 시각으로 다시 넣으면 새 시각만 유효하다(예전 칸에 남은 항목은 방문할 때 버린다)</li>
 *   <li>이미 지난 시각으로 넣으면 다음 advance에서 바로 꺼낸다</li>
 *   <li>고리 한 바퀴보다 먼 시각은 같은 칸에 남아 있다가 시각이 지난 바퀴에서 꺼낸다(마감 시각으로 비교)</li>
 *   <li>advance 호출이 늦어져도 밀린 칸을 모두 돈다(한 바퀴 이상 밀리면 모든 칸을 한 번씩)</li>
 * </ul>
 *
 * <p>스프링·DB에 의존하지 않는 순수 자료구조다. 메서드는 synchronized(등록 스레드와 tick 스레드가 다름, 경합은 작다).
 */
public class TimingWheel<K> {

    private final long tickMs;
    private final List<Set<K>> slots;
    private final Map<K, Entry> entries = new HashMap<>();
    private long lastTick;   // 완전히 지나간 마지막 칸. 지금 칸은 다음 advance에서도 다시 본다

    public TimingWheel(long tickMs, int wheelSize, long startMillis) {
        if (tickMs <= 0 || wheelSize <= 0) {
            throw new IllegalArgumentException("tickMs, wheelSize는 양수여야 한다");
        }
        this.tickMs = tickMs;
        this.slots = new ArrayList<>(wheelSize);
        for (int i = 0; i < wheelSize; i++) {
            slots.add(new LinkedHashSet<>());
        }
        this.lastTick = startMillis / tickMs - 1;
    }

    /** 키를 deadlineMillis에 꺼내도록 예약한다. 같은 키·같은 시각이면 아무것도 하지 않는다. */
    public synchronized void schedule(K key, long deadlineMillis) {
        Entry current = entries.get(key);
        if (current != null && current.deadline() == deadlineMillis) {
            return;
        }
        long tick = Math.max(deadlineMillis / tickMs, lastTick + 1);   // 이미 지난 시각 → 지금 칸
        entries.put(key, new Entry(deadlineMillis, tick));
        slots.get(index(tick)).add(key);
    }

    /** nowMillis까지 지난 칸을 돌며 마감 시각이 지난 키를 꺼낸다(꺼낸 키는 휠에서 빠진다). */
    public synchronized List<K> advance(long nowMillis) {
        long nowTick = nowMillis / tickMs;
        if (nowTick <= lastTick) {
            return List.of();
        }
        long steps = Math.min(nowTick - lastTick, slots.size());
        List<K> due = new ArrayList<>();
        for (long t = lastTick + 1; t <= lastTick + steps; t++) {
            int slotIndex = index(t);
            Iterator<K> it = slots.get(slotIndex).iterator();
            while (it.hasNext()) {
                K key = it.next();
                Entry entry = entries.get(key);
                if (entry == null || index(entry.tick()) != slotIndex) {
                    it.remove();   // 이미 꺼냈거나 다른 칸으로 다시 예약된 키
                } else if (entry.deadline() <= nowMillis) {
                    due.add(key);
                    entries.remove(key);
                    it.remove();
                }
                // 그 밖: 같은 칸이지만 한 바퀴 뒤의 시각 → 남겨 둔다
            }
        }
        lastTick = nowTick - 1;   // 지금 칸은 아직 진행 중 → 다음 advance에서 다시 본다
        return due;
    }

    public synchronized int size() {
        return entries.size();
    }

    private int index(long tick) {
        return (int) Math.floorMod(tick, (long) slots.size());
    }

    private record Entry(long deadline, long tick) {
    }
}
