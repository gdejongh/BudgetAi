-- Add optional savings goal fields to envelope table
ALTER TABLE envelope ADD COLUMN goal_amount DECIMAL(19, 2);
ALTER TABLE envelope ADD COLUMN monthly_goal_target DECIMAL(19, 2);
ALTER TABLE envelope ADD COLUMN goal_target_date DATE;
