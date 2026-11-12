package com.jjinmak.chat.app;

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
}