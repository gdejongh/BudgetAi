-- Add plaid_linked_at timestamp to bank_account to track when each account was linked via Plaid.
-- Used to filter out transactions that occurred before the account was connected.
ALTER TABLE bank_account ADD COLUMN plaid_linked_at TIMESTAMPTZ;

-- Backfill existing Plaid-linked accounts with their created_at date
-- so that the scheduled sync doesn't retroactively import old transactions.
UPDATE bank_account SET plaid_linked_at = created_at WHERE plaid_account_id IS NOT NULL AND is_manual = false;
