package com.jjinmak.boundedcontext.market.auction.concurrency.support;

import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

// 커밋 후(AFTER_COMMIT) 실제로 보내려 한 이벤트 수. KafkaTemplate은 테스트 지원 클래스에서 mock이다.
public final class KafkaSendCounter {

    private KafkaSendCounter() {
    }

    public static long count(KafkaTemplate<String, Object> kafkaTemplate, String topic, Long auctionId) {
        return Mockito.mockingDetails(kafkaTemplate).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("send"))
                .filter(inv -> inv.getArguments().length >= 2
                        && topic.equals(inv.getArguments()[0])
                        && String.valueOf(auctionId).equals(String.valueOf(inv.getArguments()[1])))
                .count();
    }
}
