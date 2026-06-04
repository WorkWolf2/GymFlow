-- V1__initial_schema.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- GYMS
-- ============================================================
CREATE TABLE gyms (
                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                      name VARCHAR(100) NOT NULL,
                      address TEXT,
                      phone VARCHAR(30),
                      email VARCHAR(150),
                      settings JSONB DEFAULT '{}',
                      active BOOLEAN NOT NULL DEFAULT true,
                      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- ROLES & PERMISSIONS
-- ============================================================
CREATE TABLE roles (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE permissions (
                             id BIGSERIAL PRIMARY KEY,
                             name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE role_permissions (
                                  role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                                  permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
                                  PRIMARY KEY (role_id, permission_id)
);

-- ============================================================
-- STAFF USERS (operators of the system)
-- ============================================================
CREATE TABLE staff_users (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
                             email VARCHAR(150) NOT NULL UNIQUE,
                             password_hash VARCHAR(255) NOT NULL,
                             first_name VARCHAR(80) NOT NULL,
                             last_name VARCHAR(80) NOT NULL,
                             role_id BIGINT NOT NULL REFERENCES roles(id),
                             active BOOLEAN NOT NULL DEFAULT true,
                             last_login_at TIMESTAMPTZ,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                             updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- CLIENTS (gym members)
-- ============================================================
CREATE SEQUENCE users_client_code_seq START WITH 1;

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       client_code BIGINT NOT NULL UNIQUE DEFAULT nextval('users_client_code_seq'),
                       gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
                       first_name VARCHAR(80) NOT NULL,
                       last_name VARCHAR(80) NOT NULL,
                       email VARCHAR(150),
                       phone VARCHAR(30),
                       birth_date DATE,
                       birth_place VARCHAR(120),
                       sex VARCHAR(1),
                       fiscal_code VARCHAR(20),
                       address TEXT,
                       notes TEXT,
                       avatar_path VARCHAR(512),
                       doc_front_path VARCHAR(512),
                       doc_back_path VARCHAR(512),
                       signature_path VARCHAR(512),
                       active BOOLEAN NOT NULL DEFAULT true,
                       deleted_at TIMESTAMPTZ,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- NFC TAGS
-- ============================================================
CREATE TABLE nfc_tags (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
                          tag_uid VARCHAR(64) NOT NULL UNIQUE,
                          user_id UUID REFERENCES users(id) ON DELETE SET NULL,
                          assigned_at TIMESTAMPTZ,
                          active BOOLEAN NOT NULL DEFAULT true,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- MEDICAL CERTIFICATES
-- ============================================================
CREATE TABLE medical_certificates (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                      issued_date DATE NOT NULL,
                                      expiry_date DATE NOT NULL,
                                      file_path VARCHAR(512),
                                      notes TEXT,
                                      deleted_at TIMESTAMPTZ,
                                      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- SUBSCRIPTION TYPES
-- ============================================================
CREATE TABLE subscription_types (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
                                    name VARCHAR(100) NOT NULL,
                                    type VARCHAR(20) NOT NULL CHECK (type IN ('ABBONAMENTO', 'ASSICURAZIONE')),
                                    base_price NUMERIC(10, 2) NOT NULL DEFAULT 0,
                                    validity_days INTEGER,
                                    forced_expiry DATE,
                                    description TEXT,
                                    color VARCHAR(7) DEFAULT '#6366f1',
                                    active BOOLEAN NOT NULL DEFAULT true,
                                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- SUBSCRIPTIONS
-- ============================================================
CREATE TABLE subscriptions (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               subscription_type_id UUID NOT NULL REFERENCES subscription_types(id),
                               start_date DATE NOT NULL,
                               end_date DATE NOT NULL,
                               price NUMERIC(10, 2) NOT NULL,
                               notes TEXT,
                               created_by UUID REFERENCES staff_users(id) ON DELETE SET NULL,
                               deleted_at TIMESTAMPTZ,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                               updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- PAYMENTS
-- ============================================================
CREATE TABLE payments (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
                          user_id UUID REFERENCES users(id) ON DELETE SET NULL,
                          subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
                          amount NUMERIC(10, 2) NOT NULL,
                          method VARCHAR(20) NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
                          payment_date DATE NOT NULL,
                          notes TEXT,
                          created_by UUID REFERENCES staff_users(id) ON DELETE SET NULL,
                          deleted_at TIMESTAMPTZ,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- ACCESS LOG
-- ============================================================
CREATE TABLE accesses (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
                          user_id UUID REFERENCES users(id) ON DELETE SET NULL,
                          nfc_tag_uid VARCHAR(64),
                          device_id VARCHAR(100),
                          device_ip VARCHAR(45),
                          access_time TIMESTAMPTZ NOT NULL DEFAULT now(),
                          granted BOOLEAN NOT NULL,
                          denial_reason VARCHAR(50),
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- USER DOCUMENTS
-- ============================================================
CREATE TABLE user_documents (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                doc_type VARCHAR(50) NOT NULL CHECK (doc_type IN ('DOC_FRONT', 'DOC_BACK', 'CERTIFICATE', 'OTHER')),
                                file_path VARCHAR(512) NOT NULL,
                                original_name VARCHAR(255),
                                uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- EMAIL LOG
-- ============================================================
CREATE TABLE emails_log (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            gym_id UUID REFERENCES gyms(id) ON DELETE SET NULL,
                            user_id UUID REFERENCES users(id) ON DELETE SET NULL,
                            to_address VARCHAR(150) NOT NULL,
                            subject VARCHAR(255),
                            template VARCHAR(100),
                            sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            status VARCHAR(20) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT', 'FAILED')),
                            error_message TEXT
);

-- ============================================================
-- AUDIT LOG
-- ============================================================
CREATE TABLE audit_logs (
                            id BIGSERIAL PRIMARY KEY,
                            gym_id UUID REFERENCES gyms(id) ON DELETE SET NULL,
                            staff_user_id UUID REFERENCES staff_users(id) ON DELETE SET NULL,
                            action VARCHAR(100) NOT NULL,
                            entity_type VARCHAR(50),
                            entity_id VARCHAR(50),
                            old_value JSONB,
                            new_value JSONB,
                            ip_address VARCHAR(45),
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- INTERNAL NOTES
-- ============================================================
CREATE TABLE internal_notes (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
                                user_id UUID REFERENCES users(id) ON DELETE SET NULL,
                                staff_user_id UUID REFERENCES staff_users(id) ON DELETE SET NULL,
                                content TEXT NOT NULL,
                                deleted_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- SETTINGS
-- ============================================================
CREATE TABLE settings (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          gym_id UUID NOT NULL REFERENCES gyms(id) ON DELETE CASCADE,
                          key VARCHAR(100) NOT NULL,
                          value TEXT,
                          UNIQUE (gym_id, key)
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX idx_users_gym_id ON users(gym_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(active) WHERE deleted_at IS NULL;
CREATE INDEX idx_nfc_tags_uid ON nfc_tags(tag_uid);
CREATE INDEX idx_nfc_tags_user ON nfc_tags(user_id);
CREATE INDEX idx_subscriptions_user ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_end ON subscriptions(end_date);
CREATE INDEX idx_subscriptions_active ON subscriptions(user_id, end_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_payments_date ON payments(payment_date DESC);
CREATE INDEX idx_payments_gym_date ON payments(gym_id, payment_date DESC);
CREATE INDEX idx_accesses_time ON accesses(access_time DESC);
CREATE INDEX idx_accesses_user ON accesses(user_id);
CREATE INDEX idx_accesses_gym_time ON accesses(gym_id, access_time DESC);
CREATE INDEX idx_certs_user_expiry ON medical_certificates(user_id, expiry_date);
CREATE INDEX idx_audit_gym_time ON audit_logs(gym_id, created_at DESC);

-- ============================================================
-- SEED DATA
-- ============================================================
INSERT INTO roles (name) VALUES ('ADMIN'), ('RECEPTION'), ('TRAINER');

INSERT INTO permissions (name) VALUES
                                   ('USER_READ'), ('USER_WRITE'), ('USER_DELETE'),
                                   ('SUBSCRIPTION_READ'), ('SUBSCRIPTION_WRITE'),
                                   ('PAYMENT_READ'), ('PAYMENT_WRITE'),
                                   ('ACCESS_READ'), ('SETTINGS_WRITE'),
                                   ('REPORT_EXPORT'), ('AUDIT_READ');

-- ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ADMIN';

-- RECEPTION
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'RECEPTION'
  AND p.name IN ('USER_READ','USER_WRITE','SUBSCRIPTION_READ','SUBSCRIPTION_WRITE',
                 'PAYMENT_READ','PAYMENT_WRITE','ACCESS_READ','REPORT_EXPORT');

-- TRAINER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'TRAINER'
  AND p.name IN ('USER_READ','ACCESS_READ');

-- Default gym
INSERT INTO gyms (id, name, email, phone, address)
VALUES ('00000000-0000-0000-0000-000000000001', 'Legion Asd', 'legionrende@gmail.com', '+39 392 1584765', 'Via Gioacchino Rossini, 84');

-- Default subscription types
INSERT INTO subscription_types (gym_id, name, type, base_price, validity_days, color)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'Mensile', 'ABBONAMENTO', 50.00, 30, '#6366f1'),
    ('00000000-0000-0000-0000-000000000001', 'Trimestrale', 'ABBONAMENTO', 130.00, 90, '#8b5cf6'),
    ('00000000-0000-0000-0000-000000000001', 'Annuale', 'ABBONAMENTO', 450.00, 365, '#a855f7'),
    ('00000000-0000-0000-0000-000000000001', 'Assicurazione Annuale', 'ASSICURAZIONE', 25.00, 365, '#f59e0b');

-- Default admin staff user (password: Admin1234!)
INSERT INTO staff_users (gym_id, email, password_hash, first_name, last_name, role_id)
VALUES (
           '00000000-0000-0000-0000-000000000001',
           'legionrende@gmail.com',
           '$2a$12$GDu0c/9j1WCdOdDTi0jed.88MJvewzRztXK3d3Tzh/xrj/2DIA5qC',
           'Legion',
           '',
           (SELECT id FROM roles WHERE name = 'ADMIN')
       );



