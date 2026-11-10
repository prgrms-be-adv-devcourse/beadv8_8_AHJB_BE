CREATE TABLE recommend_member (
                                  id          bigint       NOT NULL,
                                  created_at  timestamp(6) NOT NULL,
                                  updated_at  timestamp(6) NOT NULL,
                                  uuid        uuid         NOT NULL,
                                  nickname    varchar(255) NOT NULL,
                                  CONSTRAINT pk_recommend_member PRIMARY KEY (id),
                                  CONSTRAINT uk_recommend_member_uuid UNIQUE (uuid)
);

CREATE TABLE recommend_product (
                                   id             bigint       NOT NULL,
                                   created_at     timestamp(6) NOT NULL,
                                   updated_at     timestamp(6) NOT NULL,
                                   version        bigint       NOT NULL,
                                   seller_id      bigint       NOT NULL,
                                   product_name   varchar(255) NOT NULL,
                                   category       varchar(255) NOT NULL,
                                   manufacturer   varchar(255) NOT NULL,
                                   thumbnail_url  varchar(255),
                                   deleted        boolean      NOT NULL,
                                   CONSTRAINT pk_recommend_product PRIMARY KEY (id)
);

CREATE TABLE recommend_auction (
                                   id                 bigint       NOT NULL,
                                   created_at         timestamp(6) NOT NULL,
                                   updated_at         timestamp(6) NOT NULL,
                                   product_id         bigint       NOT NULL,
                                   instant_win_price  bigint,
                                   current_price      bigint,
                                   bid_count          bigint       NOT NULL,
                                   start_at           timestamp(6) NOT NULL,
                                   end_at             timestamp(6) NOT NULL,
                                   ended_at           timestamp(6),
                                   status             varchar(255) NOT NULL,
                                   CONSTRAINT pk_recommend_auction PRIMARY KEY (id),
                                   CONSTRAINT ck_recommend_auction_status CHECK (status IN ('ACTIVE', 'ENDED'))
);
CREATE INDEX idx_auction_status_end_at ON recommend_auction (status, end_at);
