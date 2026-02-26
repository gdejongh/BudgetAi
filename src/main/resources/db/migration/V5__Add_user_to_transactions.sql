ALTER TABLE transaction
    ADD COLUMN app_user_id UUID;

ALTER TABLE transaction
    ADD CONSTRAINT fk_transaction_app_user
        FOREIGN KEY (app_user_id)
            REFERENCES app_user(id)
            ON DELETE CASCADE;

ALTER TABLE transaction
    ALTER COLUMN app_user_id SET NOT NULL;