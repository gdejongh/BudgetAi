-- Restore allocated_balance to the user's original manual allocation
-- by subtracting the accumulated transaction amounts that were previously
-- auto-added to the envelope balance.
UPDATE envelope e
SET allocated_balance = e.allocated_balance - COALESCE(
    (SELECT SUM(t.amount) FROM transaction t WHERE t.envelope_id = e.id), 0
);
