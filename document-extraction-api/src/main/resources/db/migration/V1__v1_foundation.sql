-- ---------------------------------------------------------------------------
-- V1 foundation: organizations, accounts, documents, durable jobs, audit.
-- The schema is deliberately compatible with PostgreSQL and H2 (dev/test).
-- ---------------------------------------------------------------------------

CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_membership_user_org UNIQUE (user_id, organization_id)
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    languages VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    page_count INTEGER NOT NULL DEFAULT 0,
    full_text TEXT,
    pages_json TEXT,
    duration_ms BIGINT,
    error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    retention_until TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_documents_org_created ON documents (organization_id, created_at);
CREATE INDEX idx_documents_org_status ON documents (organization_id, status);
CREATE INDEX idx_documents_owner ON documents (owner_id);
CREATE INDEX idx_documents_retention ON documents (retention_until);

CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL UNIQUE REFERENCES documents(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(120),
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_jobs_claim ON jobs (status, available_at, created_at);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(80) NOT NULL,
    document_id UUID,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_org_created ON audit_events (organization_id, created_at);
CREATE INDEX idx_audit_document ON audit_events (document_id);
