-- ---------------------------------------------------------------------------
-- V3 field traceability: documents lineage, extracted fields, sources and
-- append-only corrections. Additive migration, compatible with PostgreSQL
-- and H2 (tests use H2 in PostgreSQL mode).
-- ---------------------------------------------------------------------------

ALTER TABLE documents ADD COLUMN lineage_id UUID;
ALTER TABLE documents ADD COLUMN source_document_id UUID;

UPDATE documents SET lineage_id = id WHERE lineage_id IS NULL;

ALTER TABLE documents ALTER COLUMN lineage_id SET NOT NULL;
ALTER TABLE documents ADD CONSTRAINT fk_documents_source_document
    FOREIGN KEY (source_document_id) REFERENCES documents(id) ON DELETE SET NULL;
CREATE INDEX idx_documents_lineage ON documents (lineage_id, created_at);

CREATE TABLE extracted_fields (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    lineage_id UUID NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    label VARCHAR(160) NOT NULL,
    raw_value TEXT NOT NULL,
    normalized_value TEXT NOT NULL,
    confidence DOUBLE PRECISION,
    extraction_method VARCHAR(20) NOT NULL,
    extractor_version VARCHAR(40) NOT NULL,
    verification_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_extracted_field_document_key UNIQUE (document_id, field_key)
);

CREATE INDEX idx_extracted_fields_org_document
    ON extracted_fields (organization_id, document_id, field_key);
CREATE INDEX idx_extracted_fields_lineage
    ON extracted_fields (organization_id, lineage_id, field_key);

CREATE TABLE field_sources (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    field_id UUID NOT NULL REFERENCES extracted_fields(id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL,
    bbox_x0 DOUBLE PRECISION NOT NULL,
    bbox_y0 DOUBLE PRECISION NOT NULL,
    bbox_x1 DOUBLE PRECISION NOT NULL,
    bbox_y1 DOUBLE PRECISION NOT NULL,
    source_space VARCHAR(20) NOT NULL,
    page_width DOUBLE PRECISION NOT NULL,
    page_height DOUBLE PRECISION NOT NULL,
    rotation_deg INTEGER NOT NULL,
    char_start INTEGER NOT NULL,
    char_end INTEGER NOT NULL,
    source_text TEXT NOT NULL,
    ordinal INTEGER NOT NULL
);

CREATE INDEX idx_field_sources_field_page
    ON field_sources (field_id, page_number, ordinal);
CREATE INDEX idx_field_sources_org
    ON field_sources (organization_id);

CREATE TABLE field_corrections (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    field_id UUID NOT NULL REFERENCES extracted_fields(id) ON DELETE CASCADE,
    corrected_value TEXT NOT NULL,
    previous_value TEXT,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    superseded_at TIMESTAMP WITH TIME ZONE,
    active_marker SMALLINT
);

CREATE UNIQUE INDEX uq_field_corrections_current
    ON field_corrections (field_id, active_marker);
CREATE INDEX idx_field_corrections_field
    ON field_corrections (field_id, created_at);
CREATE INDEX idx_field_corrections_org
    ON field_corrections (organization_id);
