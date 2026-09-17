// Backend tidak menyimpan warna. Warna diturunkan dari id supaya satu catatan
// selalu berwarna sama di mana pun — termasuk di layar penerima share.
export const NOTE_COLORS = ['blue', 'pink', 'yellow', 'mint', 'lilac'] as const
export type NoteColor = (typeof NOTE_COLORS)[number]

export function noteColor(id: string): NoteColor {
  let hash = 0
  for (const ch of id) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return NOTE_COLORS[hash % NOTE_COLORS.length]
}

// Nama kelas ditulis utuh supaya Tailwind bisa menemukannya saat build.
export const NOTE_BG: Record<NoteColor, string> = {
  blue: 'bg-note-blue',
  pink: 'bg-note-pink',
  yellow: 'bg-note-yellow',
  mint: 'bg-note-mint',
  lilac: 'bg-note-lilac',
}
