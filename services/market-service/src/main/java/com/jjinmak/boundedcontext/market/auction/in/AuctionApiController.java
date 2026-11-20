package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.app.support.AuctionRetrySupport;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionListStatus;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionSortType;
import com.jjinmak.boundedcontext.market.auction.dto.*;
import com.jjinmak.boundedcontext.market.auction.exception.AuctionErrorCode;
import com.jjinmak.boundedcontext.market.auction.in.dto.AuctionBidRequestDto;
import com.jjinmak.boundedcontext.market.auction.in.dto.DutchBuyRequestDto;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "경매", description = "입찰, 즉시낙찰, 경매 상세, 입찰 내역 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionApiController {
    private final AuctionFacade auctionFacade;
    private final AuctionRetrySupport auctionRetrySupport;

    @Operation(
            summary = "입찰",
            description = "진행중인 경매에 입찰합니다. 종료 5분 전 이후 입찰하면 종료 시각이 5분 연장되고, "
                    + "즉시낙찰가 이상이면 즉시낙찰가로 낙찰됩니다."
    )
    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<RsData<AuctionBidResponseDto>> bid(
            @PathVariable Long auctionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AuctionBidRequestDto request
    ){
        // 낙관적 락 충돌이면 새 트랜잭션에서 재검증부터 다시 실행 (최대 3회, 소진 시 AUCTION008)
        AuctionBidResponseDto response = auctionRetrySupport.execute(AuctionRetrySupport.Operation.BID,
                () -> auctionFacade.bid(auctionId, principal.memberId(), request.bidPrice()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(true,"입찰이 완료되었습니다.",response));
    }

    @Operation(
            summary = "즉시낙찰",
            description = "즉시낙찰가로 경매를 바로 낙찰합니다. 현재 최고 입찰자도 가능하며, 판매자는 불가합니다. "
    )
    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PostMapping("/{auctionId}/instant-win")
    public ResponseEntity<RsData<AuctionInstantWinResponseDto>> instantWin(
            @PathVariable Long auctionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        AuctionInstantWinResponseDto response = auctionRetrySupport.execute(AuctionRetrySupport.Operation.INSTANT_WIN,
                () -> auctionFacade.instantWin(auctionId, principal.memberId()));
        return ResponseEntity.status(HttpStatus.OK)
                .body(new RsData<>(true, "즉시 낙찰되었습니다.", response));
    }
    @Operation(
            summary = "입찰 내역 조회",
            description = "경매의 입찰 내역을 최신순으로 조회합니다. (본인 입찰 여부를 함께 반환합니다.)"
    )
    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<RsData<AuctionBidHistoryResponseDto>> bidHistory(
            @PathVariable Long auctionId,@AuthenticationPrincipal AuthPrincipal principal, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size){
        // 공개 조회라 비로그인(principal == null)도 들어온다. 이때 내 입찰 여부는 모두 false
        Long memberId = principal == null ? null : principal.memberId();
        AuctionBidHistoryResponseDto response = auctionFacade.bidHistory(auctionId,memberId,page,size);
        return  ResponseEntity.ok(new RsData<>(true,"입찰 내역 조회에 성공했습니다.",response));

    }

    @Operation(
            summary = "경매 상세 조회",
            description = "경매 정보와 내 정보(판매자 여부 등)를 조회합니다. 영국식과 하향식은 응답 항목이 다릅니다."
    )
    @GetMapping("/{auctionId}")
    public ResponseEntity<RsData<AuctionDetailResponseDto>> detail(@PathVariable Long auctionId,@AuthenticationPrincipal AuthPrincipal principal){
        // 공개 조회라 비로그인(principal == null)도 들어온다. 이때 판매자·최고 입찰자 여부는 false
        Long memberId = principal == null ? null : principal.memberId();
        AuctionDetailResponseDto response = auctionFacade.detail(auctionId,memberId);
        return ResponseEntity.ok(new RsData<>(true,"경매 상세 조회에 성공했습니다.",response));
    }

    @Operation(
            summary = "입찰 중인 경매",
            description = "내가 입찰한 진행 중 경매를 마감 임박순으로 조회합니다. (최고 입찰자 여부 포함)"
    )
    @GetMapping("/me/bids")
    public ResponseEntity<RsData<List<AuctionMyBidDto>>> myBids(@AuthenticationPrincipal AuthPrincipal principal) {
        List<AuctionMyBidDto> response = auctionFacade.myBids(principal.memberId());
        return ResponseEntity.ok(new RsData<>(true, "입찰 중인 경매 조회에 성공했습니다.", response));
    }

    @Operation(
            summary = "영국식 경매 목록",
            description = "진행 중(기본) 또는 종료된 영국식 경매를 조회합니다. 카테고리로 거를 수 있고, 생략하면 전체를 보여줍니다. "
                    + "정렬을 생략하면 진행 중은 마감 임박순, 종료는 최근 종료순입니다."
    )
    @GetMapping
    public ResponseEntity<RsData<EnglishAuctionListResponseDto>> englishList(
            @RequestParam(defaultValue = "IN_PROGRESS") AuctionListStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) AuctionSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        EnglishAuctionListResponseDto response = auctionFacade.englishList(status, category, sort, page, size);
        return ResponseEntity.ok(new RsData<>(true, "경매 목록 조회에 성공했습니다.", response));
    }

    @Operation(
            summary = "하향식 구매",
            description = "하향식 경매를 현재 가격으로 구매합니다. 화면에서 본 가격보다 서버 가격이 더 낮으면 낮은 가격으로 구매됩니다."
    )

    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PostMapping("/{auctionId}/dutch-buy")
    public ResponseEntity<RsData<DutchBuyResponseDto>> dutchBuy(
            @PathVariable Long auctionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody DutchBuyRequestDto request
    ) {
        DutchBuyResponseDto response;
        try {
            response = auctionFacade.dutchBuy(auctionId, principal.memberId(), request.expectedPrice());
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(AuctionErrorCode.DUTCH_ALREADY_SOLD);
        }
        return ResponseEntity.ok(new RsData<>(true, "구매가 완료되었습니다.", response));
    }

    @Operation(
            summary = "하향식 경매 목록",
            description = "진행 중(IN_PROGRESS, 기본) 또는 종료(ENDED) 하향식 경매를 최신 시작순으로 조회합니다."
    )
    @GetMapping("/dutch")
    public ResponseEntity<RsData<DutchAuctionListResponseDto>> dutchList(
            @RequestParam(defaultValue = "IN_PROGRESS") AuctionListStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        DutchAuctionListResponseDto response = auctionFacade.dutchList(status, page, size);
        return ResponseEntity.ok(new RsData<>(true, "하향식 경매 목록 조회에 성공했습니다.", response));
    }
}
