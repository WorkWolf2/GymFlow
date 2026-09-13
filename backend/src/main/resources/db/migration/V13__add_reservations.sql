-- V13__add_reservations.sql
CREATE TABLE IF NOT EXISTS reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reservation_date DATE NOT NULL,
    time_slot VARCHAR(10) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reservation_gym_user_date_slot UNIQUE (gym_id, user_id, reservation_date, time_slot)
);

CREATE INDEX IF NOT EXISTS idx_reservations_gym_date ON reservations(gym_id, reservation_date);
CREATE INDEX IF NOT EXISTS idx_reservations_gym_date_slot ON reservations(gym_id, reservation_date, time_slot);
CREATE INDEX IF NOT EXISTS idx_reservations_user ON reservations(user_id);
