-- =============================================================================
-- Migration: V2__security_2fa_biometric_notifications.sql
-- 2FA (TOTP / SMS), Hardware-backed Biometric Authentication, and Notifications
-- =============================================================================

-- 1. Extend user_accounts table with 2FA, Biometric credentials, and notification settings
ALTER TABLE user_accounts
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(64),
    ADD COLUMN IF NOT EXISTS two_factor_type VARCHAR(32) DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS two_factor_backup_codes VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS last_otp_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS otp_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS biometric_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS biometric_public_key VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS biometric_device_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS biometric_device_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS biometric_registered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS push_token VARCHAR(512),
    ADD COLUMN IF NOT EXISTS sms_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS push_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_user_accounts_bio_device ON user_accounts(biometric_device_id);


ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 3. Create notifications table for audit history of SMS and Push alerts
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    phone_number VARCHAR(32),
    channel VARCHAR(16) NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    message VARCHAR(1024) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SENT',
    reference_id VARCHAR(64),
    metadata VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    read_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_phone ON notifications(phone_number);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_reference ON notifications(reference_id);
