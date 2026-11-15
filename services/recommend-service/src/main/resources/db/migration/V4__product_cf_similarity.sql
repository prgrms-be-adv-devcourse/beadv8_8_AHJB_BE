CREATE TABLE product_cf_similarity (
                                       product_id        bigint           NOT NULL,
                                       other_product_id  bigint           NOT NULL,
                                       score             double precision NOT NULL,
                                       co_count          integer          NOT NULL,
                                       computed_at       timestamp(6)     NOT NULL,
                                       CONSTRAINT pk_product_cf_similarity PRIMARY KEY (product_id, other_product_id),
                                       CONSTRAINT fk_product_cf_similarity_product FOREIGN KEY (product_id) REFERENCES recommend_product (id),
                                       CONSTRAINT fk_product_cf_similarity_other_product FOREIGN KEY (other_product_id) REFERENCES recommend_product (id)
);
