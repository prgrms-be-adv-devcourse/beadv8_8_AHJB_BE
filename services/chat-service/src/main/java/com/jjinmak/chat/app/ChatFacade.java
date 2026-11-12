package com.jjinmak.chat.app;

import com.jjinmak.chat.in.dto.ChatMessageListDto;
import com.jjinmak.chat.in.dto.ChatRoomDetailDto;
import com.jjinmak.chat.in.dto.ChatRoomListDto;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatFacade {

    private final ChatCreateRoomUseCase chatCreateRoomUseCase;
    private final ChatSaveMemberUseCase chatSaveMemberUseCase;
    private final ChatSaveProductUseCase chatSaveProductUseCase;
    private final ChatGetRoomsUseCase chatGetRoomsUseCase;
    private final ChatGetRoomUseCase chatGetRoomUseCase;
    private final ChatGetMessagesUseCase chatGetMessagesUseCase;

    @Transactional
    public void createRoom(AuctionWonEvent event) {
        chatCreateRoomUseCase.createRoom(event);
    }

    @Transactional
    public void saveMember(MemberDto member) {
        chatSaveMemberUseCase.saveMember(member);
    }

    @Transactional
    public void saveProduct(ProductRegisteredEvent event) {
        chatSaveProductUseCase.saveProduct(event);
    }

    @Transactional(readOnly = true)
    public ChatRoomListDto getRooms(Long memberId, int page, int size) {
        return ChatRoomListDto.from(chatGetRoomsUseCase.getRooms(memberId, page, size));
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailDto getRoom(Long memberId, Long roomId) {
        return chatGetRoomUseCase.getRoom(memberId, roomId);
    }

    @Transactional(readOnly = true)
    public ChatMessageListDto getMessages(Long memberId, Long roomId, int page, int size) {
        return ChatMessageListDto.from(chatGetMessagesUseCase.getMessages(memberId, roomId, page, size));
    }
}