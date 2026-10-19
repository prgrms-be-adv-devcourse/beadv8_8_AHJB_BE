package com.jjinmak.back.boundedContext.member.out;

import com.jjinmak.back.boundedContext.member.domain.MemberWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberWarningRepository extends JpaRepository<MemberWarning, Long> {

    int countByMemberId(Long memberId);

    boolean existsBySourceContextAndSourceId(String sourceContext, Long sourceId);
}