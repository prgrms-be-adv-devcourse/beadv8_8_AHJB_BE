package com.jjinmak.chat.out;

import com.jjinmak.chat.domain.ChatProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatProductRepository extends JpaRepository<ChatProduct, Long> {
}