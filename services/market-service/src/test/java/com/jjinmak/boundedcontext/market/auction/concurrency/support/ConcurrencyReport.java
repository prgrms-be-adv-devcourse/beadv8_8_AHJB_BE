package com.jjinmak.boundedcontext.market.auction.concurrency.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 시나리오별 결과를 build/concurrency-results/{lockLabel}.md 에 표로 남긴다.
// lockLabel: -PlockLabel=v0-baseline (Gradle concurrencyTest 태스크가 시스템 속성으로 넘긴다)
public final class ConcurrencyReport {

    private static final List<String> ROWS = new ArrayList<>();
    private static boolean headerWritten = false;

    private ConcurrencyReport() {
    }

    public static synchronized void record(String id, String scenario, int rounds, int violatedRounds, String detail) {
        ROWS.add(String.format("| %s | %s | %d | %d/%d | %s |", id, scenario, rounds, violatedRounds, rounds, detail));
        System.out.printf("[동시성] %s %s: 위반 %d/%d, %s%n", id, scenario, violatedRounds, rounds, detail);
    }

    // 기록이 없으면 파일을 쓰지 않는다 (빈 실행이 이전 결과를 지우지 않게)
    public static synchronized void flush() {
        if (ROWS.isEmpty()) {
            return;
        }
        String label = System.getProperty("lockLabel", "local");
        Path dir = Path.of(System.getProperty("concurrency.resultDir", "build/concurrency-results"));
        Path file = dir.resolve(label + ".md");
        try {
            Files.createDirectories(dir);
            List<String> lines = new ArrayList<>();
            if (!headerWritten && !Files.exists(file)) {
                lines.add("# 동시성 정합성 결과 (" + label + ", PostgreSQL 17 Testcontainers)");
                lines.add("");
                lines.add("| ID | 시나리오 | 회차 | 위반 회차 | 결과 |");
                lines.add("|---|---|---|---|---|");
            }
            headerWritten = true;
            lines.add("<!-- " + LocalDateTime.now() + " -->");
            lines.addAll(ROWS);
            Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            ROWS.clear();
        } catch (IOException e) {
            System.err.println("동시성 결과 파일 쓰기 실패: " + e.getMessage());
        }
    }
}
