-- V10__add_pacchetto_subscription_type.sql
ALTER TABLE subscription_types DROP CONSTRAINT IF EXISTS subscription_types_type_check;
ALTER TABLE subscription_types ADD CONSTRAINT subscription_types_type_check CHECK (type IN ('ABBONAMENTO', 'ASSICURAZIONE', 'PACCHETTO'));
