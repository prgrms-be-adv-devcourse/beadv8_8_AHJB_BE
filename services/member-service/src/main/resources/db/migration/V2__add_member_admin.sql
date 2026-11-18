-- 관리자 여부 (네덜란드식 경매 등록 등)
-- 관리자를 만드는 API는 없다. 가입 후 운영자가 직접 지정한다.
--   UPDATE member SET is_admin = true WHERE email = '...';
-- 기존 회원은 모두 일반 회원(false)으로 채운다.
ALTER TABLE member ADD COLUMN is_admin boolean NOT NULL DEFAULT false;
