-- V11__add_stop_and_go_to_subscriptions.sql
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS suspended_from DATE;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS suspended_to DATE;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS stop_and_go_applied BOOLEAN NOT NULL DEFAULT false;
