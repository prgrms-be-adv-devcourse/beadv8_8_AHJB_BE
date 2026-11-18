package com.jjinmak.member.out;

import com.jjinmak.member.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findAddressByMemberIdAndIsDefaultIsTrue(Long memberID);

}
