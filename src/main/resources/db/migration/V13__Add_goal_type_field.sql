-- Add goal_type to distinguish between MONTHLY and TARGET savings goals
ALTER TABLE envelope ADD COLUMN goal_type VARCHAR(20);
