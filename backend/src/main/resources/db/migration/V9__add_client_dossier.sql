CREATE TABLE client_dossier_notes (
    id UUID PRIMARY KEY, user_id UUID NOT NULL, author_id UUID,
    content TEXT NOT NULL, deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dossier_note_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_dossier_note_author FOREIGN KEY (author_id) REFERENCES staff_users(id)
);
CREATE TABLE client_dossier_progress (
    id UUID PRIMARY KEY, user_id UUID NOT NULL, recorded_date DATE NOT NULL,
    weight DECIMAL(7,2), height DECIMAL(7,2), body_fat_percentage DECIMAL(5,2), muscle_mass DECIMAL(7,2),
    measurements TEXT, observations TEXT, custom_parameters TEXT, deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dossier_progress_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE client_dossier_documents (
    id UUID PRIMARY KEY, user_id UUID NOT NULL, name VARCHAR(255) NOT NULL, file_path VARCHAR(1024) NOT NULL,
    description TEXT, deleted_at TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dossier_document_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE client_dossier_fields (
    id UUID PRIMARY KEY, user_id UUID NOT NULL, field_name VARCHAR(100) NOT NULL, field_value TEXT,
    value_type VARCHAR(30) NOT NULL, deleted_at TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dossier_field_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_dossier_notes_user_created ON client_dossier_notes(user_id, created_at DESC);
CREATE INDEX idx_dossier_progress_user_date ON client_dossier_progress(user_id, recorded_date DESC);
CREATE INDEX idx_dossier_documents_user_created ON client_dossier_documents(user_id, created_at DESC);
CREATE INDEX idx_dossier_fields_user ON client_dossier_fields(user_id);
