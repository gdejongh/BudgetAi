-- Add envelope_type and linked_account_id to envelope table
ALTER TABLE envelope ADD COLUMN envelope_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE envelope ADD COLUMN linked_account_id UUID;
ALTER TABLE envelope ADD CONSTRAINT fk_envelope_linked_account
    FOREIGN KEY (linked_account_id) REFERENCES bank_account(id) ON DELETE SET NULL;

-- Add category_type to envelope_category table
ALTER TABLE envelope_category ADD COLUMN category_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
