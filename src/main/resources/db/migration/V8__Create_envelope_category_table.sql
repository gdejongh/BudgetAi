-- Create envelope_category table
CREATE TABLE envelope_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- For each user that has envelopes, create a "General" category
INSERT INTO envelope_category (id, app_user_id, name)
SELECT gen_random_uuid(), DISTINCT_USERS.app_user_id, 'General'
FROM (SELECT DISTINCT app_user_id FROM envelope) AS DISTINCT_USERS;

-- Add the envelope_category_id column (nullable initially)
ALTER TABLE envelope ADD COLUMN envelope_category_id UUID;

-- Assign existing envelopes to their user's "General" category
UPDATE envelope e
SET envelope_category_id = ec.id
FROM envelope_category ec
WHERE ec.app_user_id = e.app_user_id
  AND ec.name = 'General';

-- Now make it NOT NULL
ALTER TABLE envelope ALTER COLUMN envelope_category_id SET NOT NULL;

-- Add foreign key constraint
ALTER TABLE envelope ADD CONSTRAINT fk_envelope_category
    FOREIGN KEY (envelope_category_id) REFERENCES envelope_category(id) ON DELETE CASCADE;
