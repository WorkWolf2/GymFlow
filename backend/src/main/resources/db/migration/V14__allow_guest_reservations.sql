-- V14__allow_guest_reservations.sql
ALTER TABLE reservations ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS guest_name VARCHAR(150);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS guest_phone VARCHAR(50);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS guest_email VARCHAR(100);

-- Drop old unique constraint requiring user_id
ALTER TABLE reservations DROP CONSTRAINT IF EXISTS uq_reservation_gym_user_date_slot;
