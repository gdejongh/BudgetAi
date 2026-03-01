-- Add account type to bank_account (CHECKING, SAVINGS, CREDIT_CARD)
ALTER TABLE bank_account ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CHECKING';

-- Add transaction type to transaction (STANDARD, CC_PAYMENT)
ALTER TABLE transaction ADD COLUMN transaction_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

-- Add linked transaction reference for CC payments (pairs two sides of a payment)
ALTER TABLE transaction ADD COLUMN linked_transaction_id UUID REFERENCES transaction(id) ON DELETE SET NULL;

-- Index on the new columns for query performance
CREATE INDEX idx_bank_account_account_type ON bank_account(account_type);
CREATE INDEX idx_transaction_transaction_type ON transaction(transaction_type);
CREATE INDEX idx_transaction_linked_transaction_id ON transaction(linked_transaction_id);
