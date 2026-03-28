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

CREATE TABLE user_credentials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    credential_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    verified_at TIMESTAMP NULL,
    reviewer_notes VARCHAR(1000) NULL,
    verification_email VARCHAR(100),
    verification_token VARCHAR(100),
    UNIQUE KEY uk_user_credential_type (user_id, credential_type),
    INDEX idx_cred_user_id (user_id),
    INDEX idx_cred_token (verification_token),
    INDEX idx_cred_user_status (user_id, status)
);

CREATE TABLE military_credential_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_credential_id BIGINT NOT NULL UNIQUE,
    branch VARCHAR(100),
    rank VARCHAR(100),
    service_start_date DATE,
    currently_serving BOOLEAN,
    service_end_date DATE,
    discharge_type VARCHAR(150)
);

CREATE TABLE student_credential_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_credential_id BIGINT NOT NULL UNIQUE,
    school_name VARCHAR(255),
    enrollment_status VARCHAR(100),
    major VARCHAR(255),
    student_id VARCHAR(100),
    graduation_date VARCHAR(20)
);

CREATE TABLE first_responder_credential_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_credential_id BIGINT NOT NULL UNIQUE,
    agency_name VARCHAR(255),
    role VARCHAR(150),
    badge_number VARCHAR(100),
    employment_start_date DATE
);

CREATE TABLE teacher_credential_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_credential_id BIGINT NOT NULL UNIQUE,
    school_name VARCHAR(255),
    teaching_level VARCHAR(150),
    subject VARCHAR(255),
    employee_id VARCHAR(100),
    employment_start_date DATE
);

CREATE TABLE healthcare_credential_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_credential_id BIGINT NOT NULL UNIQUE,
    license_type VARCHAR(150),
    license_number VARCHAR(100),
    issuing_state VARCHAR(100),
    employer VARCHAR(255)
);

CREATE TABLE government_credential_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_credential_id BIGINT NOT NULL UNIQUE,
    agency_name VARCHAR(255),
    position VARCHAR(150),
    level VARCHAR(100),
    employee_id VARCHAR(100)
);

CREATE TABLE senior_credential_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_credential_id BIGINT NOT NULL UNIQUE,
    date_of_birth DATE
);

CREATE TABLE nonprofit_credential_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_credential_id BIGINT NOT NULL UNIQUE,
    org_name VARCHAR(255),
    ein VARCHAR(100),
    position VARCHAR(150),
    org_type VARCHAR(150),
    employment_start_date DATE
);

CREATE TABLE IF NOT EXISTS developer_apps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    website VARCHAR(255),
    description VARCHAR(500),
    callback_url VARCHAR(500),
    allowed_credential_types TEXT,
    api_key_hash VARCHAR(255) NOT NULL,
    api_key_prefix VARCHAR(15) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    owner_email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_prefix (api_key_prefix)
);

CREATE TABLE IF NOT EXISTS verification_grants (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    credential_type VARCHAR(30) NOT NULL,
    token VARCHAR(100) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_grant_token (token),
    INDEX idx_grant_user (user_id)
);
