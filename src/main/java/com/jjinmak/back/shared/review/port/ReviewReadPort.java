package com.jjinmak.back.shared.review.port;

import com.jjinmak.back.shared.review.dto.SellerRatingDto;

public interface ReviewReadPort {

    SellerRatingDto getSellerRating(Long sellerId);
}
