CREATE TABLE transaction (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             bank_account_id UUID NOT NULL REFERENCES bank_account(id) ON DELETE CASCADE,
                             envelope_id UUID REFERENCES envelope(id) ON DELETE SET NULL,
                             amount NUMERIC(19, 2) NOT NULL,
                             description VARCHAR(255),
                             transaction_date DATE NOT NULL,
                             created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);