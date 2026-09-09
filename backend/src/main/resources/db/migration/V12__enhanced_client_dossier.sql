-- V12__enhanced_client_dossier.sql
CREATE TABLE IF NOT EXISTS client_dossier_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    main_goal TEXT,
    secondary_goals TEXT,
    experience_level VARCHAR(20),
    initial_weight NUMERIC(7,2),
    initial_height NUMERIC(7,2),
    initial_bia_file_path VARCHAR(1024),
    initial_measurements TEXT,
    initial_assessment TEXT,
    initial_limitations TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE client_dossier_progress ADD COLUMN IF NOT EXISTS bia_file_path VARCHAR(1024);
ALTER TABLE client_dossier_progress ADD COLUMN IF NOT EXISTS progress_notes TEXT;
ALTER TABLE client_dossier_progress ADD COLUMN IF NOT EXISTS performance_notes TEXT;
ALTER TABLE client_dossier_progress ADD COLUMN IF NOT EXISTS coach_notes TEXT;
ALTER TABLE client_dossier_progress ADD COLUMN IF NOT EXISTS critical_issues TEXT;
ALTER TABLE client_dossier_progress ADD COLUMN IF NOT EXISTS changes_made TEXT;
ALTER TABLE client_dossier_progress ADD COLUMN IF NOT EXISTS next_check_date DATE;

CREATE TABLE IF NOT EXISTS client_dossier_programs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    program_file_path VARCHAR(1024),
    start_date DATE,
    review_date DATE,
    changes_made TEXT,
    coach_name VARCHAR(150),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dossier_programs_user ON client_dossier_programs(user_id, start_date DESC);
