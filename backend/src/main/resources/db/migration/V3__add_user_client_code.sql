ALTER TABLE users
    ADD COLUMN IF NOT EXISTS client_code BIGINT;

CREATE SEQUENCE IF NOT EXISTS users_client_code_seq START WITH 1;

WITH numbered_users AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS code
    FROM users
    WHERE client_code IS NULL
)
UPDATE users u
SET client_code = numbered_users.code
FROM numbered_users
WHERE u.id = numbered_users.id;

SELECT setval(
    'users_client_code_seq',
    COALESCE((SELECT MAX(client_code) FROM users), 0) + 1,
    false
);

ALTER TABLE users
    ALTER COLUMN client_code SET DEFAULT nextval('users_client_code_seq'),
    ALTER COLUMN client_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_client_code ON users(client_code);
