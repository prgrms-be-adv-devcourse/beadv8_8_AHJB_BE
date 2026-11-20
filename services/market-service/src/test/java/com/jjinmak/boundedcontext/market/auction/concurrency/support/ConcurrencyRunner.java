package com.jjinmak.boundedcontext.market.auction.concurrency.support;

import com.jjinmak.common.exception.BusinessException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 여러 작업을 "같은 순간" 시작시키고 결과 코드를 모은다.
// 결과 코드: 성공 OK / 업무 예외 errorCode(AUCTION006 등) / 낙관적 락 충돌 CONFLICT / 그 밖의 예외 ERROR:클래스명
public final class ConcurrencyRunner {

    private ConcurrencyRunner() {
    }

    public record Task(String label, Runnable body) {
    }

    public record Outcome(String label, String code) {
    }

    public static List<Outcome> runTogether(List<Task> tasks) {
        int n = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        List<Outcome> outcomes = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            for (Task task : tasks) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        task.body().run();
                        outcomes.add(new Outcome(task.label(), "OK"));
                    } catch (BusinessException e) {
                        outcomes.add(new Outcome(task.label(), e.getErrorCode().getCode()));
                    } catch (OptimisticLockingFailureException e) {
                        outcomes.add(new Outcome(task.label(), "CONFLICT"));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        outcomes.add(new Outcome(task.label(), "ERROR:Interrupted"));
                    } catch (Throwable e) {
                        outcomes.add(new Outcome(task.label(), "ERROR:" + e.getClass().getSimpleName()));
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            if (!done.await(120, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 작업이 120초 안에 끝나지 않음");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return new ArrayList<>(outcomes);
    }

    public static Map<String, Integer> countByCode(List<Outcome> outcomes) {
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        outcomes.forEach(o -> counts.computeIfAbsent(o.code(), k -> new AtomicInteger()).incrementAndGet());
        Map<String, Integer> result = new java.util.TreeMap<>();
        counts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public static long count(List<Outcome> outcomes, String label, String code) {
        return outcomes.stream().filter(o -> o.label().equals(label) && o.code().equals(code)).count();
    }

    public static long count(List<Outcome> outcomes, String code) {
        return outcomes.stream().filter(o -> o.code().equals(code)).count();
    }

    public static boolean hasUnexpectedError(List<Outcome> outcomes) {
        return outcomes.stream().anyMatch(o -> o.code().startsWith("ERROR:"));
    }
}
