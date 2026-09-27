CREATE SCHEMA IF NOT EXISTS docs;

-- =========================================================
-- Accounts
-- =========================================================
CREATE TABLE docs.accounts (
                               id                  UUID         PRIMARY KEY,
                               email               VARCHAR(255) NOT NULL,
                               display_name        VARCHAR(255) NOT NULL,
                               status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                               system_role         VARCHAR(16)  NOT NULL DEFAULT 'USER',
                               salt                BYTEA,
                               verifier            BYTEA,
                               avatar              BYTEA,
                               avatar_mime         VARCHAR(64),
                               avatar_updated_at   TIMESTAMPTZ,
                               created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               CONSTRAINT uq_accounts_email UNIQUE (email),
                               CONSTRAINT ck_accounts_status
                                   CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED')),
                               CONSTRAINT ck_accounts_system_role
                                   CHECK (system_role IN ('USER', 'ADMIN')),
                               CONSTRAINT ck_accounts_salt_len
                                   CHECK (salt IS NULL OR octet_length(salt) BETWEEN 16 AND 64),
                               CONSTRAINT ck_accounts_verifier_len
                                   CHECK (verifier IS NULL OR octet_length(verifier) BETWEEN 64 AND 256),
                               CONSTRAINT ck_accounts_avatar_mime
                                   CHECK (avatar_mime IS NULL OR avatar_mime IN (
                                                                                 'image/png', 'image/jpeg', 'image/webp', 'image/gif'
                                       )),
                               CONSTRAINT ck_accounts_avatar_size
                                   CHECK (avatar IS NULL OR octet_length(avatar) BETWEEN 1 AND 2097152),
                               CONSTRAINT ck_accounts_avatar_consistency
                                   CHECK (
                                       (avatar IS NULL AND avatar_mime IS NULL) OR
                                       (avatar IS NOT NULL AND avatar_mime IS NOT NULL)
                                       )
);
ALTER TABLE docs.accounts ALTER COLUMN avatar SET STORAGE EXTERNAL;

-- =========================================================
-- Sessions
-- =========================================================
CREATE TABLE docs.sessions (
                               id           UUID        PRIMARY KEY,
                               account_id   UUID        NOT NULL,
                               token_hash   BYTEA       NOT NULL,
                               created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                               expires_at   TIMESTAMPTZ NOT NULL,
                               last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                               CONSTRAINT uq_sessions_token UNIQUE (token_hash),
                               CONSTRAINT fk_sessions_account
                                   FOREIGN KEY (account_id) REFERENCES docs.accounts (id) ON DELETE CASCADE,
                               CONSTRAINT ck_sessions_token_len
                                   CHECK (octet_length(token_hash) = 32)
);
CREATE INDEX idx_sessions_account ON docs.sessions (account_id);
CREATE INDEX idx_sessions_expires ON docs.sessions (expires_at);

-- =========================================================
-- Projects
-- =========================================================
CREATE TABLE docs.projects (
                               id            UUID         PRIMARY KEY,
                               name          VARCHAR(255) NOT NULL,
                               description   TEXT,
                               created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               created_by    UUID         NOT NULL,
                               archived_at   TIMESTAMPTZ,
                               CONSTRAINT uq_projects_name UNIQUE (name),
                               CONSTRAINT fk_projects_created_by
                                   FOREIGN KEY (created_by) REFERENCES docs.accounts (id)
);
CREATE INDEX idx_projects_created_by ON docs.projects (created_by);

-- =========================================================
-- Project access
-- =========================================================
CREATE TABLE docs.project_access (
                                     id         UUID        PRIMARY KEY,
                                     project_id UUID        NOT NULL,
                                     account_id UUID        NOT NULL,
                                     role       VARCHAR(16) NOT NULL,
                                     granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                     granted_by UUID,
                                     CONSTRAINT uq_project_access UNIQUE (project_id, account_id),
                                     CONSTRAINT ck_project_access_role
                                         CHECK (role IN ('VIEWER', 'EDITOR', 'OWNER')),
                                     CONSTRAINT fk_project_access_project
                                         FOREIGN KEY (project_id) REFERENCES docs.projects (id) ON DELETE CASCADE,
                                     CONSTRAINT fk_project_access_account
                                         FOREIGN KEY (account_id) REFERENCES docs.accounts (id) ON DELETE CASCADE,
                                     CONSTRAINT fk_project_access_granted_by
                                         FOREIGN KEY (granted_by) REFERENCES docs.accounts (id) ON DELETE SET NULL
);
CREATE INDEX idx_project_access_account ON docs.project_access (account_id);
CREATE INDEX idx_project_access_project ON docs.project_access (project_id);

-- =========================================================
-- Documents
-- =========================================================
CREATE TABLE docs.documents (
                                id                        UUID         PRIMARY KEY,
                                project_id                UUID         NOT NULL,
                                title                     VARCHAR(512) NOT NULL,
                                doc_kind                  VARCHAR(64)  NOT NULL,
                                current_version_id        UUID,
                                current_version_author_id UUID,
                                version_seq               BIGINT       NOT NULL DEFAULT 0,
                                created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                created_by                UUID         NOT NULL,
                                updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                deleted_at                TIMESTAMPTZ,
                                CONSTRAINT fk_documents_project
                                    FOREIGN KEY (project_id) REFERENCES docs.projects (id) ON DELETE CASCADE,
                                CONSTRAINT fk_documents_created_by
                                    FOREIGN KEY (created_by) REFERENCES docs.accounts (id),
                                CONSTRAINT fk_documents_current_version_author
                                    FOREIGN KEY (current_version_author_id) REFERENCES docs.accounts (id)
);
CREATE INDEX idx_documents_project ON docs.documents (project_id, updated_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_created_by ON docs.documents (created_by);
CREATE INDEX idx_documents_current_version_author
    ON docs.documents (current_version_author_id);

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
                                        CONSTRAINT ck_versions_size
                                            CHECK (size_bytes >= 0 AND size_bytes <= 15728640),
                                        CONSTRAINT ck_versions_hash_len
                                            CHECK (octet_length(content_hash) = 32)
);
CREATE INDEX idx_versions_document ON docs.document_versions (document_id, version_number DESC);
CREATE INDEX idx_versions_author   ON docs.document_versions (author_id);
CREATE INDEX idx_versions_hash     ON docs.document_versions (content_hash);

-- Хранить байты в TOAST без попыток сжатия: JPEG/PDF/PNG/ZIP не сжимаются.
ALTER TABLE docs.document_versions ALTER COLUMN content SET STORAGE EXTERNAL;

-- =========================================================
-- Document events (append-only, хеш-цепочка)
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
                                      CONSTRAINT ck_events_hash_len
                                          CHECK (octet_length(event_hash) = 32),
                                      CONSTRAINT ck_events_prev_hash_len
                                          CHECK (prev_event_hash IS NULL OR octet_length(prev_event_hash) = 32)
);
CREATE INDEX idx_events_document_time ON docs.document_events (document_id, occurred_at DESC, id DESC);
CREATE INDEX idx_events_project_time  ON docs.document_events (project_id, occurred_at DESC);
CREATE INDEX idx_events_actor         ON docs.document_events (actor_id);
CREATE INDEX idx_events_type          ON docs.document_events (event_type, occurred_at DESC);

-- =========================================================
-- Бэкфилл: создатели существующих проектов становятся OWNER
-- =========================================================
INSERT INTO docs.project_access (id, project_id, account_id, role, granted_at, granted_by)
SELECT gen_random_uuid(), p.id, p.created_by, 'OWNER', p.created_at, p.created_by
FROM docs.projects p
WHERE p.created_by IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM docs.project_access pa
    WHERE pa.project_id = p.id AND pa.account_id = p.created_by
);

-- =========================================================
-- (Опционально) Назначить первого администратора
-- =========================================================
-- UPDATE docs.accounts SET system_role = 'ADMIN' WHERE email = 'admin@example.com';