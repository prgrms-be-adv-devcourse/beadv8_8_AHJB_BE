CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE product_embedding (
                                   id               bigint       NOT NULL,
                                   created_at       timestamp(6) NOT NULL,
                                   updated_at       timestamp(6) NOT NULL,
                                   status           varchar(255) NOT NULL,
                                   text_hash        varchar(64)  NOT NULL,
                                   model            varchar(255),
                                   retry_count      integer      NOT NULL,
                                   next_attempt_at  timestamp(6),
                                   claimed_at       timestamp(6),
                                   embedding        vector(1536),
                                   CONSTRAINT pk_product_embedding PRIMARY KEY (id),
                                   CONSTRAINT fk_product_embedding_product FOREIGN KEY (id) REFERENCES recommend_product (id),
                                   CONSTRAINT ck_product_embedding_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'))
);
