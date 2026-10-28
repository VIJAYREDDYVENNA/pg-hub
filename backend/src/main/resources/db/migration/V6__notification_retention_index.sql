-- Supports the daily retention cleanup (DELETE ... WHERE created_at <= cutoff) without scanning the whole table.
-- The existing idx_notifications_user starts with user_id and cannot serve a cross-user range delete.
CREATE INDEX idx_notifications_created ON notifications (created_at);
