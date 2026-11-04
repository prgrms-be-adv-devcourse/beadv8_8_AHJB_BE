package com.jjinmak.contracts.review.port;

import com.jjinmak.contracts.review.dto.SellerRatingDto;

public interface ReviewReadPort {

    SellerRatingDto getSellerRating(Long sellerId);
}
