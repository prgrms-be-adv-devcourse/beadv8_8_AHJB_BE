package com.jjinmak.back.boundedContext.member.out;

import com.jjinmak.back.boundedContext.member.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
}
