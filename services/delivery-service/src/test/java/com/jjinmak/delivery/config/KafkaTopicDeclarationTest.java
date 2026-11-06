package com.jjinmak.delivery.config;

import com.jjinmak.contracts.delivery.event.DeliveryEventTopics;
import com.jjinmak.delivery.in.DeliveryKafkaListener;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KafkaTopicDeclarationTest {

    @Autowired
    List<KafkaAdmin.NewTopics> newTopics;

    @Test
    @DisplayName("배송 서비스가 발행하는 토픽 이름 상수는 모두 토픽으로 선언된다")
    void deliveryTopics_areDeclared() {
        assertThat(declaredTopics()).containsAll(deliveryTopics());
    }

    @Test
    @DisplayName("배송 서비스가 구독하는 토픽마다 <토픽>-dlt 토픽이 선언된다")
    void subscribedTopics_haveDeadLetterTopics() {
        List<String> deadLetterTopics = subscribedTopics().stream()
                .map(topic -> topic + "-dlt")
                .toList();

        assertThat(deadLetterTopics).isNotEmpty();
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

    private static List<String> deliveryTopics() {
        return Arrays.stream(DeliveryEventTopics.class.getFields())
                .map(KafkaTopicDeclarationTest::constantValue)
                .toList();
    }

    private static List<String> subscribedTopics() {
        return Arrays.stream(DeliveryKafkaListener.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(KafkaListener.class))
                .filter(Objects::nonNull)
                .flatMap(listener -> Arrays.stream(listener.topics()))
                .distinct()
                .toList();
    }

    private static String constantValue(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
