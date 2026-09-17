// Database dummy di memori, disimpan ke localStorage supaya perubahan
// bertahan saat halaman dimuat ulang. Bentuknya meniru tabel di docs/schema.sql.

export type UserRow = { id: string; email: string; password: string; displayName: string }
export type ProfileRow = {
  id: string
  userId: string
  email: string
  displayName: string
  bio: string | null
  avatarUrl: string | null
  createdAt: string
  updatedAt: string
}
export type NoteRow = {
  id: string
  ownerId: string
  title: string
  content: string | null
  tags: string[]
  createdAt: string
  updatedAt: string
}
export type ShareRow = {
  id: string
  noteId: string
  sharedWithUserId: string
  sharedWithEmail: string
  createdAt: string
}

export type Db = { users: UserRow[]; profiles: ProfileRow[]; notes: NoteRow[]; shares: ShareRow[] }

const STORAGE_KEY = 'notenest.mockdb.v2'
export const DEMO_PASSWORD = 'rahasia123'

export function uuid(): string {
  return crypto.randomUUID()
}

// LocalDateTime tanpa zona, persis format Jackson di backend
export function localDateTime(date: Date = new Date()): string {
  const pad = (n: number, w = 2) => String(n).padStart(w, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`
  )
}

function ago(minutes: number): string {
  return localDateTime(new Date(Date.now() - minutes * 60_000))
}

const ANI = '6f1c2a3e-0000-4000-8000-00000000a001'
const BUDI = '6f1c2a3e-0000-4000-8000-00000000b002'
const CITRA = '6f1c2a3e-0000-4000-8000-00000000c003'

const LONG_TITLE =
  'Rangkuman panjang diskusi arsitektur microservices: gateway, service discovery, Feign, schema-per-service, ' +
  'dan kenapa otorisasi berbasis kepemilikan tidak bisa ditaruh di gateway sama sekali'

const aniNotes: Array<[string, string | null, string[], number]> = [
  ['Rapat mingguan tim produk', 'Bahas roadmap Q3.\n- API share sudah jalan\n- Frontend mulai minggu depan', ['kerja', 'rapat'], 12],
  ['Resep sambal matah', 'Bawang merah, serai, cabai rawit, daun jeruk, garam, minyak kelapa panas.', ['masak'], 95],
  ['Ide judul skripsi', null, [], 60 * 26],
  [LONG_TITLE.slice(0, 200), 'Gateway membuktikan siapa, service memutuskan boleh apa.', ['belajar', 'java', 'spring'], 60 * 30],
  ['Daftar belanja', 'Kopi\nGula aren\nSusu oat\nRoti gandum', ['pribadi'], 60 * 50],
  ['Catatan belajar Feign', 'FeignException.NotFound diterjemahkan jadi 404 yang jelas.\nError lain → 502.', ['belajar', 'java'], 60 * 72],
  ['Buku yang ingin dibaca', 'Designing Data-Intensive Applications\nThe Pragmatic Programmer\nLaskar Pelangi', ['baca'], 60 * 90],
  ['Retrospektif sprint 14', 'Yang berjalan baik: deploy lebih sering.\nYang perlu diperbaiki: review PR terlalu lama.', ['kerja'], 60 * 120],
  ['Rencana liburan Lombok', 'Hari 1: Senggigi\nHari 2: Gili Trawangan\nHari 3: Bukit Merese', ['liburan', 'pribadi'], 60 * 150],
  ['Kutipan favorit', '"Simplicity is prerequisite for reliability." — Dijkstra', [], 60 * 200],
  ['Setup laptop baru', 'JDK 21, Node 22, Docker Desktop, VS Code, IntelliJ', ['setup'], 60 * 260],
  ['Pertanyaan wawancara', 'Kenapa 403 bukan 404 untuk note orang lain?\nKenapa profil dibuat lazy?', ['karier', 'belajar'], 60 * 300],
  ['Ide fitur NoteNest', 'Permission EDIT\nNotifikasi share realtime\nEndpoint batch lookup pemilik', ['ide'], 60 * 340],
  ['Latihan olahraga', 'Senin: lari 5 km\nRabu: renang\nJumat: angkat beban', ['kesehatan'], 60 * 400],
  ['Catatan kajian', 'Tentang sabar dan syukur.', ['pribadi'], 60 * 460],
  ['Budget bulan ini', 'Kos 1.500.000\nMakan 1.200.000\nTransport 400.000', ['keuangan'], 60 * 520],
  ['Checklist rilis', '- Test lulus\n- Changelog\n- Tag versi\n- Umumkan', ['kerja', 'rilis'], 60 * 600],
  ['Hadiah ulang tahun ibu', 'Tas batik atau jam tangan', ['keluarga'], 60 * 700],
  ['Resep kopi susu gula aren', 'Espresso 2 shot, gula aren cair 20 ml, susu 150 ml, es.', ['masak', 'kopi'], 60 * 820],
  ['Materi presentasi', 'Arsitektur NoteNest dalam 10 menit.', ['kerja', 'presentasi'], 60 * 900],
  ['Tag maksimal', 'Catatan ini punya sepuluh tag untuk menguji tampilan.', ['satu', 'dua', 'tiga', 'empat', 'lima', 'enam', 'tujuh', 'delapan', 'sembilan', 'sepuluh'], 60 * 1000],
  ['Film untuk ditonton', 'Petualangan Sherina 2\nOppenheimer', ['hiburan'], 60 * 1100],
  ['Password wifi kantor', 'Tanya ke admin, jangan ditulis di sini.', [], 60 * 1300],
  ['Jadwal servis motor', 'Ganti oli tiap 2.000 km.', ['kendaraan'], 60 * 1500],
  ['Catatan panjang', Array.from({ length: 40 }, (_, i) => `Baris ke-${i + 1}: isi catatan yang cukup panjang untuk menguji tampilan detail.`).join('\n'), ['uji'], 60 * 24 * 400],
]

function seed(): Db {
  const users: UserRow[] = [
    { id: ANI, email: 'ani@example.com', password: DEMO_PASSWORD, displayName: 'Ani Lestari' },
    { id: BUDI, email: 'budi@example.com', password: DEMO_PASSWORD, displayName: 'Budi Santoso' },
    { id: CITRA, email: 'citra@example.com', password: DEMO_PASSWORD, displayName: 'Citra Maharani' },
  ]

  // Citra sengaja belum punya profil: belum pernah "membuka aplikasi", jadi belum
  // bisa ditemukan lewat pencarian — sama seperti backend. Catatannya juga tidak
  // dibagikan ke siapa pun: kalau dibagikan, shared-with-me penerimanya gagal total
  // (docs/FRONTEND-BRIEF.html §1.8 poin 3).
  const profiles: ProfileRow[] = users
    .filter((u) => u.id !== CITRA)
    .map((u) => ({
      id: uuid(),
      userId: u.id,
      email: u.email,
      displayName: u.displayName,
      bio: u.id === ANI ? 'Suka menulis catatan kecil dan resep.' : null,
      avatarUrl: null,
      createdAt: ago(60 * 24 * 30),
      updatedAt: ago(60 * 24 * 30),
    }))

  const notes: NoteRow[] = aniNotes.map(([title, content, tags, minutes]) => ({
    id: uuid(),
    ownerId: ANI,
    title,
    content,
    tags,
    createdAt: ago(minutes + 30),
    updatedAt: ago(minutes),
  }))

  const budiNote: NoteRow = {
    id: uuid(), ownerId: BUDI, title: 'Rute gowes Minggu pagi', content: 'Start Monas → Senayan → Kota Tua. Kumpul 05.30.',
    tags: ['olahraga'], createdAt: ago(60 * 5), updatedAt: ago(60 * 4),
  }
  const citraNote: NoteRow = {
    id: uuid(), ownerId: CITRA, title: 'Catatan kuliah Basis Data', content: 'Normalisasi: 1NF, 2NF, 3NF, BCNF.',
    tags: ['kuliah'], createdAt: ago(60 * 40), updatedAt: ago(60 * 40),
  }
  notes.push(budiNote, citraNote)

  const byTitle = (t: string) => notes.find((n) => n.title === t)!.id
  const shares: ShareRow[] = [
    { id: uuid(), noteId: byTitle('Rapat mingguan tim produk'), sharedWithUserId: BUDI, sharedWithEmail: 'budi@example.com', createdAt: ago(10) },
    { id: uuid(), noteId: byTitle('Resep sambal matah'), sharedWithUserId: BUDI, sharedWithEmail: 'budi@example.com', createdAt: ago(90) },
    { id: uuid(), noteId: byTitle('Rencana liburan Lombok'), sharedWithUserId: BUDI, sharedWithEmail: 'budi@example.com', createdAt: ago(60 * 100) },
    { id: uuid(), noteId: budiNote.id, sharedWithUserId: ANI, sharedWithEmail: 'ani@example.com', createdAt: ago(60 * 3) },
  ]

  return { users, profiles, notes, shares }
}

function load(): Db {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw) as Db
  } catch {
    // storage tidak tersedia
  }
  return seed()
}

export const db: Db = load()

export function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(db))
  } catch {
    // abaikan: data tetap hidup di memori
  }
}

export function resetDb() {
  const fresh = seed()
  db.users = fresh.users
  db.profiles = fresh.profiles
  db.notes = fresh.notes
  db.shares = fresh.shares
  persist()
}
