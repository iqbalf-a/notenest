// Waktu dari backend tanpa zona ("2026-09-17T10:14:03.221") → diperlakukan sebagai waktu lokal.
export function parseLocalDateTime(value: string): Date {
  return new Date(value.replace(/Z$/, ''))
}

const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des']
const MONTHS_LONG = [
  'Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
  'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember',
]

function sameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

// Di daftar: "baru saja" → "12 menit lalu" → "3 jam lalu" → "Kemarin" → "14 Sep" → "14 Sep 2025"
export function formatRelative(value: string, now: Date = new Date()): string {
  const date = parseLocalDateTime(value)
  const minutes = Math.floor((now.getTime() - date.getTime()) / 60_000)

  if (sameDay(date, now)) {
    if (minutes < 1) return 'baru saja'
    if (minutes < 60) return `${minutes} menit lalu`
    return `${Math.floor(minutes / 60)} jam lalu`
  }

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (sameDay(date, yesterday)) return 'Kemarin'

  const dayMonth = `${date.getDate()} ${MONTHS_SHORT[date.getMonth()]}`
  return date.getFullYear() === now.getFullYear() ? dayMonth : `${dayMonth} ${date.getFullYear()}`
}

// Di detail: "17 September 2026, 11.02" — jam Indonesia memakai titik
export function formatFull(value: string): string {
  const d = parseLocalDateTime(value)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${d.getDate()} ${MONTHS_LONG[d.getMonth()]} ${d.getFullYear()}, ${hh}.${mm}`
}

export function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase() || '?'
}
