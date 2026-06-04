ALTER TABLE payments
    ADD COLUMN payment_type VARCHAR(10) NOT NULL DEFAULT 'INCOME'
        CHECK (payment_type IN ('INCOME', 'EXPENSE'));

UPDATE payments SET payment_type = 'INCOME' WHERE payment_type IS NULL;
