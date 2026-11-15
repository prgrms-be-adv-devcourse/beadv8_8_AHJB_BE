package com.jjinmak.recommend.app;

import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.repository.RecommendMemberRepository;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RecommendMemberSyncTest extends RecommendIntegrationTestSupport {

    static final long MEMBER_ID = 7L;
    static final UUID MEMBER_UUID = UUID.fromString("7a1c0e52-3b4d-4f6a-9c8e-1d2f3a4b5c6d");

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

    @Autowired
    RecommendMemberRepository recommendMemberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        recommendMemberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원 생성 이벤트를 받으면 회원 id를 PK로 하는 복사본에 이벤트의 uuid와 닉네임을 저장한다")
    void created_savesCopy() {
        recommendKafkaListener.handle(created("구매자"));

        List<MemberRow> members = members();
        assertThat(members).hasSize(1);
        assertThat(members.getFirst().id()).isEqualTo(MEMBER_ID);
        assertThat(members.getFirst().uuid()).isEqualTo(MEMBER_UUID);
        assertThat(members.getFirst().nickname()).isEqualTo("구매자");
        assertThat(members.getFirst().createdAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 회원 생성 이벤트를 다시 받아도 실패하지 않고 복사본은 하나로 남는다")
    void redeliveredCreated_keepsOneCopy() {
        recommendKafkaListener.handle(created("구매자"));
        MemberRow saved = members().getFirst();

        assertThatCode(() -> recommendKafkaListener.handle(created("구매자")))
                .doesNotThrowAnyException();
        assertThat(members()).containsExactly(saved);
    }

    @Test
    @DisplayName("복사본이 이미 있는 회원의 이벤트를 받으면 같은 행의 닉네임을 이벤트의 닉네임으로 바꾼다")
    void existingMember_syncsNickname() {
        recommendKafkaListener.handle(created("구매자"));
        MemberRow saved = members().getFirst();

        recommendKafkaListener.handle(created("새닉네임"));

        assertThat(members()).containsExactly(new MemberRow(MEMBER_ID, MEMBER_UUID, "새닉네임", saved.createdAt()));
    }

    private static MemberCreatedEvent created(String nickname) {
        return new MemberCreatedEvent(new MemberDto(MEMBER_ID, MEMBER_UUID, nickname));
    }

    private List<MemberRow> members() {
        return jdbcTemplate.query(
                "select id, uuid, nickname, created_at from recommend_member order by id",
                (rs, rowNum) -> new MemberRow(
                        rs.getLong("id"), rs.getObject("uuid", UUID.class), rs.getString("nickname"),
                        rs.getObject("created_at", LocalDateTime.class)));
    }

    record MemberRow(long id, UUID uuid, String nickname, LocalDateTime createdAt) {
    }
}
