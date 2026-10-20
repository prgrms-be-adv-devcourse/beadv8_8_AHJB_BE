package com.jjinmak.back.boundedContext.auction.in;

import com.jjinmak.back.boundedContext.auction.app.AuctionFacade;
import com.jjinmak.back.boundedContext.auction.dto.*;
import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.boundedContext.auction.in.dto.AuctionBidRequestDto;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
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
        AuctionBidResponseDto response;
        try{
            response = auctionFacade.bid(auctionId,principal.memberId(),request.bidPrice());
        }catch (ObjectOptimisticLockingFailureException e){
            throw new BusinessException(AuctionErrorCode.BID_CONFLICT);
        }
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
        AuctionInstantWinResponseDto response;
        try {
            response = auctionFacade.instantWin(auctionId, principal.memberId());
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(AuctionErrorCode.BID_CONFLICT);
        }
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
        AuctionBidHistoryResponseDto response = auctionFacade.bidHistory(auctionId,principal.memberId(),page,size);
        return  ResponseEntity.ok(new RsData<>(true,"입찰 내역 조회에 성공했습니다.",response));

    }

    @Operation(
            summary = "경매 상세 조회",
            description = "경매 조건, 현재 상태(현재가,최소 입찰가,종료 시각 등), 내 정보(판매자,최고 입찰자 여부)를 조회합니다. "
    )
    @GetMapping("/{auctionId}")
    public ResponseEntity<RsData<AuctionDetailResponseDto>> detail(@PathVariable Long auctionId,@AuthenticationPrincipal AuthPrincipal principal){
        AuctionDetailResponseDto response = auctionFacade.detail(auctionId,principal.memberId());
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
}
