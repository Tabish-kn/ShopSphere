-- Flyway V1: Notification Service Initial Schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Notifications Table
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    recipient_email VARCHAR(255),
    notification_type VARCHAR(50) NOT NULL, -- ORDER_STATUS, PAYMENT_ALERT, PROMOTIONAL
    channel VARCHAR(50) NOT NULL,           -- EMAIL, WEBSOCKET, BOTH
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    read_status BOOLEAN NOT NULL DEFAULT FALSE,
    reference_id UUID,                     -- e.g. orderId
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(user_id, read_status) WHERE read_status = FALSE;
