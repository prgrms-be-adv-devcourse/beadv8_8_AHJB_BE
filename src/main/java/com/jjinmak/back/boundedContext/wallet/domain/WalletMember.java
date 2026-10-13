package com.jjinmak.back.boundedContext.wallet.domain;


import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import com.jjinmak.back.shared.wallet.dto.WalletMemberDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
public class WalletMember extends BaseManualIdAndTime {

    @NotNull
    @Column(unique = true)
    private UUID uuid;


    public WalletMember(Long id, UUID uuid){
        super(id);
        this.uuid = uuid;
    }

    public WalletMemberDto toDto() {
        return new WalletMemberDto(
                getId(),
                getUuid()
        );
    }

}
