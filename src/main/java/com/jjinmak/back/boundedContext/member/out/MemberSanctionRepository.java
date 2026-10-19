package com.jjinmak.back.boundedContext.member.out;

import com.jjinmak.back.boundedContext.member.domain.MemberSanction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberSanctionRepository extends JpaRepository<MemberSanction, Long> {

    @Query("""
        select s from MemberSanction s
        where s.memberId = :memberId
          and s.releasedAt is null
        order by s.startedAt desc
        limit 1
        """)
    Optional<MemberSanction> findActiveByMemberId(@Param("memberId") Long memberId);

    List<MemberSanction> findAllByMemberIdAndReleasedAtIsNull(Long memberId);
}