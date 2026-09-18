-- Snapshot skema database NoteNest (branch deploy/monolith) — untuk dokumentasi,
-- bukan untuk dijalankan. Tabel dibuat otomatis oleh Hibernate (ddl-auto=update)
-- saat aplikasi startup.
--
-- BEDA DENGAN BRANCH dev:
--   dev  : schema-per-service — auth_schema.users, user_schema.profiles,
--          note_schema.{notes,note_tags,note_shares}, dibuat lewat
--          hibernate.default_schema + hbm2ddl.create_namespaces=true
--   sini : satu schema (default), karena satu aplikasi hanya punya satu
--          konfigurasi JPA dan tidak bisa menyetel tiga default_schema
--
-- Yang TIDAK berubah: tidak ada FK lintas domain. Relasi antara users, profiles
-- dan notes tetap dipegang lewat nilai UUID, bukan constraint database — persis
-- seperti di dev. Batas domain itu dijaga di kode (lihat UserClient), dan itulah
-- yang membuat pemisahan bisa dikembalikan tanpa migrasi data.
--
-- Diturunkan dari entity JPA di backend/src/main/java/com/notenest/*/entity/.
-- Semua tabel punya created_at / updated_at dari BaseEntity (@EnableJpaAuditing).

-- ============ domain: auth (paket com.notenest.authservice) ============
CREATE TABLE users (
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

-- ============ domain: user (paket com.notenest.userservice) ============
-- Baris dibuat saat user pertama kali memanggil /api/users/me (find-or-create),
-- dari klaim token. Register tidak membuat profil — pola ini warisan desain
-- microservices yang sengaja dipertahankan supaya kedua branch berperilaku sama.
CREATE TABLE profiles (
  id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone,
  user_id uuid NOT NULL,                             -- = users.id (sengaja bukan FK)
  email character varying NOT NULL,                  -- salinan dari token, untuk fitur cari user
  display_name character varying NOT NULL,
  bio character varying(500),
  avatar_url character varying,
  CONSTRAINT profiles_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX idx_profiles_user_id ON profiles (user_id);
CREATE UNIQUE INDEX idx_profiles_email ON profiles (email);

-- ============ domain: note (paket com.notenest.noteservice) ============
CREATE TABLE notes (
  id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone,
  owner_id uuid NOT NULL,                            -- = users.id (sengaja bukan FK)
  title character varying NOT NULL,
  content text,
  CONSTRAINT notes_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_notes_owner_id ON notes (owner_id);

-- @ElementCollection: tag tidak punya identitas sendiri, jadi tanpa kolom id.
-- Disimpan lowercase oleh NoteServiceImpl.
CREATE TABLE note_tags (
  note_id uuid NOT NULL,
  tag character varying NOT NULL,
  CONSTRAINT fk_note_tags_note FOREIGN KEY (note_id) REFERENCES notes (id)
);
CREATE INDEX idx_note_tags_tag ON note_tags (tag);

CREATE TABLE note_shares (
  id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL,
  updated_at timestamp without time zone,
  note_id uuid NOT NULL,
  shared_with_user_id uuid NOT NULL,                 -- = users.id (sengaja bukan FK)
  shared_with_email character varying NOT NULL,      -- snapshot email saat share dibuat
  permission character varying NOT NULL CHECK (permission IN ('READ')),
  CONSTRAINT note_shares_pkey PRIMARY KEY (id),
  CONSTRAINT fk_note_shares_note FOREIGN KEY (note_id) REFERENCES notes (id),
  CONSTRAINT uk_note_shares_note_user UNIQUE (note_id, shared_with_user_id)
);
CREATE INDEX idx_note_shares_target ON note_shares (shared_with_user_id);

-- Catatan: FK hanya ada DI DALAM satu domain (note_tags -> notes,
-- note_shares -> notes). Tidak ada FK dari notes.owner_id ke users.id, meski
-- sekarang keduanya di schema yang sama dan secara teknis sudah bisa dibuat.
-- Menambahkannya akan mengikat kedua domain di level database dan menghapus
-- satu-satunya hal yang membuat branch ini masih bisa dikembalikan jadi
-- microservices tanpa migrasi.
