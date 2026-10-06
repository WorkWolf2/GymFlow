-- V15__add_receipts_and_parent_info.sql

-- Aggiunta campi genitore per tesserati minorenni
ALTER TABLE users
ADD COLUMN IF NOT EXISTS parent_name VARCHAR(150),
ADD COLUMN IF NOT EXISTS parent_fiscal_code VARCHAR(20);

-- Creazione tabella ricevute ASD
CREATE TABLE IF NOT EXISTS receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    receipt_number INT NOT NULL,
    receipt_year INT NOT NULL,
    receipt_formatted_number VARCHAR(50) NOT NULL,
    issue_date DATE NOT NULL,
    causale TEXT NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PAGATO',
    stamp_duty_applied BOOLEAN NOT NULL DEFAULT false,
    stamp_duty_amount NUMERIC(10,2) DEFAULT 0.00,
    pdf_storage_path VARCHAR(255),
    notes TEXT,
    created_by UUID REFERENCES staff_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_receipt_gym_year_number UNIQUE(gym_id, receipt_year, receipt_number)
);

ALTER TABLE receipts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_receipts_gym_id ON receipts(gym_id);
CREATE INDEX IF NOT EXISTS idx_receipts_user_id ON receipts(user_id);
CREATE INDEX IF NOT EXISTS idx_receipts_subscription_id ON receipts(subscription_id);
CREATE INDEX IF NOT EXISTS idx_receipts_payment_id ON receipts(payment_id);
