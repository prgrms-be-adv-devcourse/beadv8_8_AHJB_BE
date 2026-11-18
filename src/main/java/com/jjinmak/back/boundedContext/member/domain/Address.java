package com.jjinmak.back.boundedContext.member.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "address", indexes = @Index(
        name = "idx_address_member_id", columnList = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseIdAndTime {

    @Column(nullable = false)
    private Long memberId;

    @Column(length = 50)
    private String alias;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Column(nullable = false)
    private String recipientPhone;

    @Column(nullable = false)
    private String zipcode;

    @Column(nullable = false)
    private String address;

    private String addressDetail;

    @Column(nullable = false)
    private boolean isDefault;

    private Address(Long memberId, String alias, String recipient, String recipientPhone,
                    String zipcode, String address, String addressDetail, boolean isDefault){

        this.memberId = memberId;
        this.alias = alias;
        this.recipient = recipient;
        this.recipientPhone = recipientPhone;
        this.zipcode = zipcode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.isDefault = isDefault;
    }

    public static Address createDefault(Long memberId, String alias, String recipient, String recipientPhone,
                                 String zipcode, String address, String addressDetail){

        return new Address(memberId, alias, recipient, recipientPhone, zipcode,
                address, addressDetail, true);
    }

}
