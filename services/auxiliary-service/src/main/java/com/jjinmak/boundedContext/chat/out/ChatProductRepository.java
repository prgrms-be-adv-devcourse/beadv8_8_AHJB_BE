package com.jjinmak.boundedContext.chat.out;

import com.jjinmak.boundedContext.chat.domain.ChatProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatProductRepository extends JpaRepository<ChatProduct, Long> {
}