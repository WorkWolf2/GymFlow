-- V19__add_package_entries_tracking.sql
ALTER TABLE subscription_types ADD COLUMN IF NOT EXISTS total_entries INT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS total_entries INT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS remaining_entries INT;
