package com.jjinmak.recommend.config;

import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicDeclarationTest extends RecommendIntegrationTestSupport {

    @Autowired
    List<KafkaAdmin.NewTopics> newTopics;

    @Test
    @DisplayName("추천 서비스가 구독하는 토픽마다 <토픽>-dlt 토픽이 선언된다")
    void subscribedTopics_haveDeadLetterTopics() {
        List<String> deadLetterTopics = subscribedTopics().stream()
                .map(topic -> topic + "-dlt")
                .toList();

        assertThat(deadLetterTopics).containsExactlyInAnyOrder(
                "product.changed-dlt", "auction.started-dlt", "auction.won-dlt", "auction.failed-dlt",
                "auction.bid-succeed-dlt");
        assertThat(declaredTopics()).containsAll(deadLetterTopics);
    }

    @Test
    @DisplayName("같은 이름의 토픽을 두 번 선언하지 않는다")
    void declaredTopics_haveNoDuplicates() {
        assertThat(declaredTopics()).doesNotHaveDuplicates();
    }

    private List<String> declaredTopics() {
        return newTopics.stream()
                .flatMap(topics -> topicsOf(topics).stream())
                .map(NewTopic::name)
                .toList();
    }

    private static Collection<NewTopic> topicsOf(KafkaAdmin.NewTopics newTopics) {
        return ReflectionTestUtils.invokeMethod(newTopics, "getNewTopics");
    }

    private static List<String> subscribedTopics() {
        return Arrays.stream(RecommendKafkaListener.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(KafkaListener.class))
                .filter(Objects::nonNull)
                .flatMap(listener -> Arrays.stream(listener.topics()))
                .distinct()
                .toList();
    }
}
