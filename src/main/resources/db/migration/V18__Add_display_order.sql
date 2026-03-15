-- Add display_order column to bank_account, envelope, and envelope_category tables
-- for user-defined ordering via drag-and-drop

ALTER TABLE bank_account ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE envelope ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE envelope_category ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;
