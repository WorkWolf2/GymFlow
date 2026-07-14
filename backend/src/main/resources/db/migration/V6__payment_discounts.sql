ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS gross_amount NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(10, 2) DEFAULT 0;

UPDATE payments
SET gross_amount = amount
WHERE gross_amount IS NULL;

