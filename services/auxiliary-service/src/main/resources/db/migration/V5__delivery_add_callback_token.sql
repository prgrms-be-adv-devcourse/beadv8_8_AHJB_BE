ALTER TABLE delivery ADD COLUMN callback_token uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE delivery ALTER COLUMN callback_token DROP DEFAULT;