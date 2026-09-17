# NoteNest — Frontend

Belum dimulai. Folder ini disiapkan untuk aplikasi web NoteNest (React + TypeScript + Vite).

Sebelum menulis satu baris pun, baca paket serah-terimanya:

1. [`../docs/FRONTEND-README.md`](../docs/FRONTEND-README.md) — pintu masuk, urutan baca, checklist
2. [`../docs/FRONTEND-SERVICE-ATLAS.html`](../docs/FRONTEND-SERVICE-ATLAS.html) — bentuk data dan endpoint
3. [`../docs/FRONTEND-BRIEF.html`](../docs/FRONTEND-BRIEF.html) — PRD + design system (token siap salin di §2.7)

## Memulai

```bash
cd frontend
npm create vite@latest . -- --template react-ts
npm install @tanstack/react-query react-router-dom zod
npm install -D msw
```

Buat `.env.example`:

```
# Kosong = pakai MSW (dummy). Isi untuk menyambung ke gateway.
VITE_API_BASE_URL=
```

## Backend

- Gateway: `http://localhost:8080` — satu-satunya alamat yang boleh dipanggil browser
- Dokumentasi interaktif: http://localhost:8080/docs.html
- Menyalakan backend: `docker compose up -d --build` dari root repo
- Origin dev server harus ada di `CORS_ALLOWED_ORIGINS` (`docker-compose.yml`, default `http://localhost:5173`)
