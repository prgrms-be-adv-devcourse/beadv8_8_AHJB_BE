package com.jjinmak.ai.recommend.domain;

import java.io.Serializable;

public record ProductCfSimilarityId(Long productId, Long otherProductId) implements Serializable {
}
