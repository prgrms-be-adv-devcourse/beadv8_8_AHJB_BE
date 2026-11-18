package com.jjinmak.boundedcontext.chat.app;

import com.jjinmak.boundedcontext.chat.domain.ChatMessage;
import com.jjinmak.boundedcontext.chat.domain.ChatProduct;
import com.jjinmak.boundedcontext.chat.domain.ChatRoom;
import com.jjinmak.boundedcontext.chat.exception.ChatErrorCode;
import com.jjinmak.boundedcontext.chat.out.ChatMessageRepository;
import com.jjinmak.boundedcontext.chat.out.ChatProductRepository;
import com.jjinmak.boundedcontext.chat.out.ChatRoomRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatCreateRoomUseCase {

    private static final String ROOM_OPENED_MESSAGE = "낙찰됐어요. 배송 일정과 궁금한 점을 여기서 이야기해 보세요.";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatProductRepository chatProductRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 낙찰(일반·즉시) 이벤트를 받아 구매자·판매자 거래 채팅방을 연다 (OPEN).
     * 같은 경매의 방이 이미 있으면 아무것도 하지 않는다 (낙찰 1건당 방 1개).
     * 상품명·썸네일은 product.registered 이벤트로 저장해 둔 복제본에서 읽는다.
     * 복제본이 없으면 예외를 던져 Kafka 재시도 → DLT로 보낸다 (경매 요청 이벤트가 먼저 와야 정상).
     * 방을 열면서 시스템 메시지 1건을 넣어, 두 참여자 모두에게 "안 읽은 메시지 1개"인 새 방으로 보이게 한다.
     *
     * @param event - 경매 ID, 상품 ID, 낙찰자 ID, 판매자 ID (낙찰가·배송비·낙찰 시각은 채팅에서 쓰지 않는다)
     */
    public void createRoom(AuctionWonEvent event) {
        if (chatRoomRepository.existsByAuctionId(event.auctionId())) {
            return;
        }

        ChatProduct product = chatProductRepository.findById(event.productId())
                .orElseThrow(() -> new BusinessException(ChatErrorCode.PRODUCT_NOT_FOUND));

        ChatRoom room = chatRoomRepository.save(new ChatRoom(
                event.auctionId(),
                event.productId(),
                product.getName(),
                product.getThumbnailUrl(),
                event.winnerId(),
                event.sellerId()
        ));

        ChatMessage opened = chatMessageRepository.save(ChatMessage.system(room.getId(), ROOM_OPENED_MESSAGE));
        room.touch(opened);
    }
}