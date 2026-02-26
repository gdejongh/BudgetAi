CREATE TABLE bank_account (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              app_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                              name VARCHAR(100) NOT NULL,
                              current_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);