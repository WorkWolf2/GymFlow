-- V16__add_updated_at_to_receipts.sql

ALTER TABLE receipts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
