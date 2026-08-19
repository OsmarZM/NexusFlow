-- ==============================================================================
-- Schema: NexusFlow V4 Lease Timeouts & User Customer Link
-- ==============================================================================

-- 1. Add claimed_at column to outbox_events for distributed lease timeouts
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_outbox_status_claimed ON outbox_events (status, claimed_at);

-- 2. Add customer_id column to users table for explicit ownership linking
ALTER TABLE users ADD COLUMN IF NOT EXISTS customer_id UUID;
CREATE INDEX IF NOT EXISTS idx_users_customer_id ON users (customer_id);
