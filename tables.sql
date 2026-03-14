CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    username VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,

    phone_no BIGINT UNIQUE,

    date_of_birth DATE,

    gender VARCHAR(10),

    password_hash VARCHAR(255) NOT NULL,
    password_updated_at TIMESTAMP,

    -- Verification fields
    email_verified_at TIMESTAMP,
    phone_verified_at TIMESTAMP,

    -- Role & account status
    role VARCHAR(20) NOT NULL,          -- USER, ADMIN
    account_status VARCHAR(20) NOT NULL, -- INACTIVE, ACTIVE, DISABLED

    -- Optional deactivation fields
    deactivated_at TIMESTAMP,
    deactivation_reason VARCHAR(255),

    last_login_at TIMESTAMP,
    failed_login_attempts INT DEFAULT 0,

    -- Optional legal acceptance fields
    terms_accepted_at TIMESTAMP,
    privacy_policy_accepted_at TIMESTAMP,

    two_factor_enabled BOOLEAN DEFAULT FALSE,
    marketing_opt_in BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50),
    action VARCHAR(50) NOT NULL,
    details VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_audit_username (username),
    INDEX idx_audit_action (action),
    INDEX idx_audit_created (created_at)
);