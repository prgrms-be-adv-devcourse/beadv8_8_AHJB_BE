-- chat_room: 역할별 마지막 읽은 메시지 id (null = 아직 아무것도 안 읽음), 목록 미리보기용 마지막 메시지 스냅샷
ALTER TABLE chat_room
    ADD COLUMN winner_last_read_id   bigint,
    ADD COLUMN seller_last_read_id   bigint,
    ADD COLUMN last_message_content  varchar(1000);

-- chat_message: 시스템 메시지(보낸 사람 없음) 허용 + 종류 구분
ALTER TABLE chat_message
    ALTER COLUMN sender_id DROP NOT NULL,
    ADD COLUMN type varchar(20) NOT NULL DEFAULT 'TEXT',
    ADD CONSTRAINT ck_chat_message_type CHECK (type IN ('TEXT', 'SYSTEM'));