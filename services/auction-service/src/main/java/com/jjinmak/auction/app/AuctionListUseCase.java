package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.AuctionListStatus;
import com.jjinmak.auction.domain.AuctionSortType;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.dto.AuctionListItemDto;
import com.jjinmak.auction.dto.AuctionListResponseDto;
import com.jjinmak.auction.dto.DutchAuctionListItemDto;
import com.jjinmak.auction.dto.DutchAuctionListResponseDto;
import com.jjinmak.auction.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionListUseCase {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Sort DUTCH_SORT = Sort.by(Sort.Direction.DESC, "startAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final AuctionRepository auctionRepository;

    public AuctionListResponseDto list(AuctionListStatus status, String category, AuctionSortType sort,
                                       int page, int size, LocalDateTime now) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        AuctionSortType resolvedSort = (sort != null) ? sort : status.defaultSort();
        String resolvedCategory = StringUtils.hasText(category) ? category : null;   // "" → 전체

        Page<EnglishAuction> result = auctionRepository.findList(
                status.statuses(),
                resolvedCategory,
                PageRequest.of(safePage, safeSize, resolvedSort.toSort())
        );

        return new AuctionListResponseDto(
                result.getContent().stream().map(auction -> AuctionListItemDto.from(auction, now)).toList(),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.hasNext(),
                now
        );
    }
    public DutchAuctionListResponseDto dutchList(AuctionListStatus status, int page, int size, LocalDateTime now) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        Page<DutchAuction> result = auctionRepository.findDutchList(
                status.statuses(), PageRequest.of(safePage, safeSize, DUTCH_SORT));

        return new DutchAuctionListResponseDto(
                result.getContent().stream().map(auction -> DutchAuctionListItemDto.from(auction, now)).toList(),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.hasNext(),
                now
        );
    }
}