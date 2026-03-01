-- Monthly allocation tracking per envelope
CREATE TABLE envelope_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE,
    year_month DATE NOT NULL, -- stores first-of-month, e.g. '2026-03-01'
    amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    UNIQUE (envelope_id, year_month)
);

CREATE INDEX idx_envelope_allocation_envelope_id ON envelope_allocation(envelope_id);
CREATE INDEX idx_envelope_allocation_year_month ON envelope_allocation(year_month);

-- Seed current month with each envelope's existing allocated_balance
INSERT INTO envelope_allocation (envelope_id, year_month, amount)
SELECT id, DATE_TRUNC('month', CURRENT_DATE)::date, allocated_balance
FROM envelope
WHERE allocated_balance > 0;
