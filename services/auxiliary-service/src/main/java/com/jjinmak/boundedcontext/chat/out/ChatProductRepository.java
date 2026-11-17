package com.jjinmak.boundedcontext.chat.out;

import com.jjinmak.boundedcontext.chat.domain.ChatProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatProductRepository extends JpaRepository<ChatProduct, Long> {
}