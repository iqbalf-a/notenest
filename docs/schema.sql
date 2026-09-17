-- Snapshot skema database NoteNest — untuk dokumentasi, bukan untuk dijalankan.
-- Tabel dibuat otomatis oleh Hibernate (ddl-auto=update) saat tiap service startup,
-- schema-nya juga (hibernate.hbm2ddl.create_namespaces=true).
-- Arsitektur: schema-per-service — tiap service hanya menyentuh schema miliknya sendiri.
-- Tidak ada FK lintas schema: relasi antar service dipegang lewat nilai UUID dan API (Feign),
-- bukan constraint database.
--
-- Diturunkan dari entity JPA di backend/*/src/main/java/**/entity/.
-- Semua tabel punya created_at / updated_at dari BaseEntity (@EnableJpaAuditing).

-- ============ auth-service (schema: auth_schema) ============
CREATE TABLE auth_schema.users (
  id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone,
  email character varying NOT NULL UNIQUE,
  password_hash character varying NOT NULL,          -- hash BCrypt, bukan plaintext
  display_name character varying NOT NULL,           -- ikut jadi klaim "name" di JWT
  role character varying NOT NULL CHECK (role IN ('USER', 'ADMIN')),
  enabled boolean NOT NULL,
  CONSTRAINT users_pkey PRIMARY KEY (id)
);

-- ============ user-service (schema: user_schema) ============
-- Baris dibuat saat user pertama kali memanggil /api/users/me (find-or-create),
-- dari klaim token. Register di auth-service tidak memanggil user-service.
CREATE TABLE user_schema.profiles (
  id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone,
  user_id uuid NOT NULL,                             -- = auth_schema.users.id (bukan FK)
  email character varying NOT NULL,                  -- salinan dari token, untuk fitur cari user
  display_name character varying NOT NULL,
  bio character varying(500),
  avatar_url character varying,
  CONSTRAINT profiles_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX idx_profiles_user_id ON user_schema.profiles (user_id);
CREATE UNIQUE INDEX idx_profiles_email ON user_schema.profiles (email);

-- ============ note-service (schema: note_schema) ============
CREATE TABLE note_schema.notes (
  id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone,
  owner_id uuid NOT NULL,                            -- = auth_schema.users.id (bukan FK)
  title character varying NOT NULL,
  content text,
  CONSTRAINT notes_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_notes_owner_id ON note_schema.notes (owner_id);

-- @ElementCollection: tag tidak punya identitas sendiri, jadi tanpa kolom id.
-- Disimpan lowercase oleh NoteServiceImpl.
CREATE TABLE note_schema.note_tags (
  note_id uuid NOT NULL,
  tag character varying NOT NULL,
  CONSTRAINT fk_note_tags_note FOREIGN KEY (note_id) REFERENCES note_schema.notes (id)
);
CREATE INDEX idx_note_tags_tag ON note_schema.note_tags (tag);

CREATE TABLE note_schema.note_shares (
  id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone,
  note_id uuid NOT NULL,
  shared_with_user_id uuid NOT NULL,                 -- = auth_schema.users.id (bukan FK)
  shared_with_email character varying NOT NULL,      -- snapshot email saat share dibuat
  permission character varying NOT NULL CHECK (permission IN ('READ')),
  CONSTRAINT note_shares_pkey PRIMARY KEY (id),
  CONSTRAINT fk_note_shares_note FOREIGN KEY (note_id) REFERENCES note_schema.notes (id),
  CONSTRAINT uk_note_shares_note_user UNIQUE (note_id, shared_with_user_id)
);
CREATE INDEX idx_note_shares_target ON note_schema.note_shares (shared_with_user_id);
