package com.jjinmak.boundedcontext.auction.app;

import com.jjinmak.boundedcontext.auction.domain.AuctionListStatus;
import com.jjinmak.boundedcontext.auction.domain.AuctionSortType;
import com.jjinmak.boundedcontext.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.auction.dto.EnglishAuctionListItemDto;
import com.jjinmak.boundedcontext.auction.dto.EnglishAuctionListResponseDto;
import com.jjinmak.boundedcontext.auction.dto.DutchAuctionListItemDto;
import com.jjinmak.boundedcontext.auction.dto.DutchAuctionListResponseDto;
import com.jjinmak.boundedcontext.auction.out.AuctionRepository;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuctionListUseCase {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Sort DUTCH_SORT = Sort.by(Sort.Direction.DESC, "startAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final AuctionRepository auctionRepository;
    private final AuctionProductReader auctionProductReader;

    public EnglishAuctionListResponseDto englishList(AuctionListStatus status, String category, AuctionSortType sort,
                                       int page, int size, LocalDateTime now) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        AuctionSortType resolvedSort = (sort != null) ? sort : status.defaultSort();
        String resolvedCategory = StringUtils.hasText(category) ? category : null;   // "" → 전체

        Page<EnglishAuction> result = auctionRepository.findEnglishList(
                status.statuses(),
                resolvedCategory,
                PageRequest.of(safePage, safeSize, resolvedSort.toSort())
        );

        Map<Long, ProductSummaryDto> products = auctionProductReader.getSummaries(result.getContent());

        return new EnglishAuctionListResponseDto(
                result.getContent().stream()
                        .map(auction -> EnglishAuctionListItemDto.from(auction, products.get(auction.getProductId()), now))
                        .toList(),
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

        Map<Long, ProductSummaryDto> products = auctionProductReader.getSummaries(result.getContent());

        return new DutchAuctionListResponseDto(
                result.getContent().stream()
                        .map(auction -> DutchAuctionListItemDto.from(auction, products.get(auction.getProductId()), now))
                        .toList(),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.hasNext(),
                now
        );
    }
}