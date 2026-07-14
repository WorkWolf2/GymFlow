CREATE TABLE email_notification_logs (
    id UUID PRIMARY KEY,
    gym_id UUID NOT NULL,
    user_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    days_before INTEGER NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_email_notification_target UNIQUE (notification_type, target_type, target_id, days_before)
);

CREATE INDEX idx_email_notification_logs_gym_sent_at
    ON email_notification_logs (gym_id, sent_at DESC);

CREATE INDEX idx_email_notification_logs_user
    ON email_notification_logs (user_id);