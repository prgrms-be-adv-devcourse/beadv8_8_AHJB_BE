package com.jjinmak.back.global.kafka;

import com.jjinmak.back.shared.delivery.event.DeliveryEventTopics;
import com.jjinmak.back.shared.order.event.OrderEventTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KafkaTopicDeclarationTest {

    @Autowired
    List<KafkaAdmin.NewTopics> newTopics;

    @Test
    @DisplayName("주문·배송 토픽 이름 상수는 모두 토픽으로 선언된다")
    void eventTopics_areDeclared() {
        assertThat(declaredTopics()).containsAll(eventTopics());
    }

    @Test
    @DisplayName("주문·배송 토픽마다 <토픽>-dlt 토픽이 선언된다")
    void eventTopics_haveDeadLetterTopics() {
        List<String> deadLetterTopics = eventTopics().stream()
                .map(topic -> topic + "-dlt")
                .toList();

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

    private static List<String> eventTopics() {
        return Stream.of(OrderEventTopics.class, DeliveryEventTopics.class)
                .flatMap(topics -> Arrays.stream(topics.getFields()))
                .map(KafkaTopicDeclarationTest::constantValue)
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
