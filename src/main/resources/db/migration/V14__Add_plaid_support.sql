-- Plaid Item table: stores connection metadata and encrypted access tokens
CREATE TABLE plaid_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    item_id VARCHAR(255) NOT NULL UNIQUE,
    access_token TEXT NOT NULL,
    institution_id VARCHAR(255),
    institution_name VARCHAR(255),
    transaction_cursor TEXT,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_plaid_item_app_user_id ON plaid_item(app_user_id);
CREATE INDEX idx_plaid_item_status ON plaid_item(status);

-- Add Plaid fields to bank_account
ALTER TABLE bank_account ADD COLUMN plaid_item_id UUID REFERENCES plaid_item(id) ON DELETE SET NULL;
ALTER TABLE bank_account ADD COLUMN plaid_account_id VARCHAR(255);
ALTER TABLE bank_account ADD COLUMN account_mask VARCHAR(4);
ALTER TABLE bank_account ADD COLUMN is_manual BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX idx_bank_account_plaid_account_id ON bank_account(plaid_account_id);

-- Add Plaid fields to transaction
ALTER TABLE transaction ADD COLUMN plaid_transaction_id VARCHAR(255);
ALTER TABLE transaction ADD COLUMN pending BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE transaction ADD COLUMN merchant_name VARCHAR(255);
ALTER TABLE transaction ADD COLUMN plaid_category VARCHAR(255);

CREATE UNIQUE INDEX idx_transaction_plaid_transaction_id ON transaction(plaid_transaction_id) WHERE plaid_transaction_id IS NOT NULL;
