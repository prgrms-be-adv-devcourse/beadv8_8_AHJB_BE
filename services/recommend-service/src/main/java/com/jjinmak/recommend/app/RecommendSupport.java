package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.out.RecommendMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.common.exception.CommonErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendSupport {

    private final RecommendMemberRepository recommendMemberRepository;

    public RecommendMember findById(Long id){
        return recommendMemberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }
}
