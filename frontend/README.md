# NoteNest — Frontend

Aplikasi web NoteNest: React + TypeScript + Vite, Tailwind v4, tema pastel dengan font Geist.

**Saat ini berjalan dengan dummy data (MSW).** Backend tidak dipanggil sama sekali; semua request `/api/*` dijawab service worker yang meniru perilaku gateway dan ketiga service — termasuk kode status, validasi, dan celah yang tercatat di `docs/FRONTEND-BRIEF.html` §1.8.

## Menjalankan

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

Akun demo (password semua `rahasia123`) — ada tombol pengisi otomatis di halaman masuk:

| Akun | Isi |
|---|---|
| `ani@example.com` | 25 catatan, 3 dibagikan ke Budi, menerima 1 dari Budi |
| `budi@example.com` | 1 catatan, menerima 3 dari Ani |
| `citra@example.com` | Belum punya profil — tidak bisa ditemukan untuk di-share sampai login pertama |

Data dummy disimpan di `localStorage` (`notenest.mockdb.v2`). Hapus key itu untuk kembali ke data awal.

Untuk menguji keadaan gagal, tambahkan `__fail` ke request, mis. `?__fail=502` (lihat `src/mocks/handlers.ts`).

## Perintah

```bash
npm run dev        # dev server
npm test           # vitest
npm run typecheck  # tsc
npm run lint       # oxlint
npm run build      # typecheck + build produksi
```

## Struktur

```
src/
├─ api/          client.ts (fetch + ApiError), endpoints.ts, hooks.ts (TanStack Query), types.ts (cermin DTO backend)
├─ mocks/        db.ts (seed + penyimpanan), handlers.ts (tiruan gateway + service), browser.ts
├─ lib/          session, validasi Zod, format tanggal, warna catatan, pesan error
├─ components/   ui.tsx (primitif), NoteCard, SharePanel, TagInput, ConfirmDialog, AppLayout
├─ pages/        Auth, Notes, NoteDetail, NoteEditor, Shared, Profile
└─ index.css     token tema pastel (docs/FRONTEND-BRIEF.html §2.7)
```

## Pindah ke API asli

1. Salin `.env.example` → `.env.local`, isi `VITE_API_BASE_URL=http://localhost:8080`.
2. Nyalakan backend: `docker compose up -d --build` dari root repo.
3. Pastikan `http://localhost:5173` ada di `CORS_ALLOWED_ORIGINS` gateway (default sudah).

Tidak ada kode komponen yang perlu diubah — kalau ada yang rusak, bentuk data di `src/api/types.ts` atau `src/mocks/handlers.ts` yang tidak sesuai backend.

## Dokumen acuan

- [`../docs/FRONTEND-README.md`](../docs/FRONTEND-README.md) — pintu masuk
- [`../docs/FRONTEND-BRIEF.html`](../docs/FRONTEND-BRIEF.html) — PRD + design system
- [`../docs/DESIGN-HANDOVER.md`](../docs/DESIGN-HANDOVER.md) — layar dan keadaan wajib
