CREATE SCHEMA IF NOT EXISTS docs;

-- =========================================================
-- Accounts
-- =========================================================
CREATE TABLE docs.accounts (
                               id            UUID         PRIMARY KEY,
                               tenant_id     UUID         NOT NULL,
                               email         VARCHAR(255) NOT NULL,
                               display_name  VARCHAR(255) NOT NULL,
                               status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                               created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               CONSTRAINT uq_accounts_tenant_email UNIQUE (tenant_id, email),
                               CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED'))
);
CREATE INDEX idx_accounts_tenant ON docs.accounts (tenant_id);

-- =========================================================
-- Projects
-- =========================================================
CREATE TABLE docs.projects (
                               id            UUID         PRIMARY KEY,
                               tenant_id     UUID         NOT NULL,
                               name          VARCHAR(255) NOT NULL,
                               description   TEXT,
                               created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               created_by    UUID         NOT NULL,
                               archived_at   TIMESTAMPTZ,
                               CONSTRAINT uq_projects_tenant_name UNIQUE (tenant_id, name),
                               CONSTRAINT fk_projects_created_by
                                   FOREIGN KEY (created_by) REFERENCES docs.accounts (id)
);
CREATE INDEX idx_projects_tenant     ON docs.projects (tenant_id);
CREATE INDEX idx_projects_created_by ON docs.projects (created_by);

-- =========================================================
-- Documents
-- =========================================================
CREATE TABLE docs.documents (
                                id                  UUID         PRIMARY KEY,
                                project_id          UUID         NOT NULL,
                                title               VARCHAR(512) NOT NULL,
                                doc_kind            VARCHAR(64)  NOT NULL,
                                current_version_id  UUID,
                                version_seq         BIGINT       NOT NULL DEFAULT 0,
                                created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                created_by          UUID         NOT NULL,
                                updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                deleted_at          TIMESTAMPTZ,
                                CONSTRAINT fk_documents_project
                                    FOREIGN KEY (project_id) REFERENCES docs.projects (id) ON DELETE CASCADE,
                                CONSTRAINT fk_documents_created_by
                                    FOREIGN KEY (created_by) REFERENCES docs.accounts (id)
);
CREATE INDEX idx_documents_project ON docs.documents (project_id, updated_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_created_by ON docs.documents (created_by);

-- =========================================================
-- Document versions
-- =========================================================
CREATE TABLE docs.document_versions (
                                        id                UUID         PRIMARY KEY,
                                        document_id       UUID         NOT NULL,
                                        version_number    INTEGER      NOT NULL,
                                        content           BYTEA        NOT NULL,
                                        mime_type         VARCHAR(255) NOT NULL,
                                        original_name     VARCHAR(512) NOT NULL,
                                        size_bytes        BIGINT       NOT NULL,
                                        content_hash      BYTEA        NOT NULL,
                                        parent_version_id UUID,
                                        author_id         UUID         NOT NULL,
                                        comment           TEXT,
                                        created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                        CONSTRAINT uq_versions_doc_number UNIQUE (document_id, version_number),
                                        CONSTRAINT fk_versions_document
                                            FOREIGN KEY (document_id) REFERENCES docs.documents (id) ON DELETE CASCADE,
                                        CONSTRAINT fk_versions_parent
                                            FOREIGN KEY (parent_version_id) REFERENCES docs.document_versions (id),
                                        CONSTRAINT fk_versions_author
                                            FOREIGN KEY (author_id) REFERENCES docs.accounts (id),
                                        CONSTRAINT ck_versions_size     CHECK (size_bytes >= 0 AND size_bytes <= 15728640),
                                        CONSTRAINT ck_versions_hash_len CHECK (octet_length(content_hash) = 32)
);
CREATE INDEX idx_versions_document ON docs.document_versions (document_id, version_number DESC);
CREATE INDEX idx_versions_author   ON docs.document_versions (author_id);
CREATE INDEX idx_versions_hash     ON docs.document_versions (content_hash);

ALTER TABLE docs.document_versions ALTER COLUMN content SET STORAGE EXTERNAL;

-- =========================================================
-- Document events
-- =========================================================
CREATE TABLE docs.document_events (
                                      id              BIGSERIAL    PRIMARY KEY,
                                      document_id     UUID         NOT NULL,
                                      project_id      UUID         NOT NULL,
                                      version_id      UUID,
                                      event_type      VARCHAR(32)  NOT NULL,
                                      actor_id        UUID         NOT NULL,
                                      occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                      payload         TEXT         NOT NULL DEFAULT '{}',
                                      prev_event_hash BYTEA,
                                      event_hash      BYTEA        NOT NULL,
                                      CONSTRAINT fk_events_document
                                          FOREIGN KEY (document_id) REFERENCES docs.documents (id),
                                      CONSTRAINT fk_events_project
                                          FOREIGN KEY (project_id) REFERENCES docs.projects (id),
                                      CONSTRAINT fk_events_version
                                          FOREIGN KEY (version_id) REFERENCES docs.document_versions (id),
                                      CONSTRAINT fk_events_actor
                                          FOREIGN KEY (actor_id) REFERENCES docs.accounts (id),
                                      CONSTRAINT ck_events_type CHECK (event_type IN (
                                                                                      'CREATED', 'UPLOADED', 'METADATA_CHANGED', 'STATUS_CHANGED',
                                                                                      'DELETED', 'RESTORED', 'COMMENTED', 'ROLLED_BACK'
                                          )),
                                      CONSTRAINT ck_events_hash_len      CHECK (octet_length(event_hash) = 32),
                                      CONSTRAINT ck_events_prev_hash_len CHECK (prev_event_hash IS NULL OR octet_length(prev_event_hash) = 32)
);
CREATE INDEX idx_events_document_time ON docs.document_events (document_id, occurred_at DESC, id DESC);
CREATE INDEX idx_events_project_time  ON docs.document_events (project_id, occurred_at DESC);
CREATE INDEX idx_events_actor         ON docs.document_events (actor_id);
CREATE INDEX idx_events_type          ON docs.document_events (event_type, occurred_at DESC);