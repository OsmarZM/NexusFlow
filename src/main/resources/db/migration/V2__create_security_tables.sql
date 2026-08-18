-- ==============================================================================
-- Schema: NexusFlow V2 Security & Authentication Tables (Users & Roles)
-- ==============================================================================

-- 1. Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);

-- 2. User Roles Table
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_role ON user_roles (role);

-- 3. Seed Default Admin User (Password: Admin@123456)
-- BCrypt hash: $2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
INSERT INTO users (id, username, email, password_hash, full_name, enabled, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin',
    'admin@nexusflow.com',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'NexusFlow System Administrator',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO user_roles (user_id, role)
VALUES 
    ('a0000000-0000-0000-0000-000000000001', 'ADMIN'),
    ('a0000000-0000-0000-0000-000000000001', 'WAREHOUSE_OPERATOR'),
    ('a0000000-0000-0000-0000-000000000001', 'FINANCE');
