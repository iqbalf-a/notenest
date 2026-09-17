import { useMemo, useState } from 'react'
import { useSharedWithMe } from '../api/hooks'
import { NoteCard } from '../components/NoteCard'
import { AlertIcon, Button, CardSkeleton, SearchIcon, ShareIcon, StateCard } from '../components/ui'

export function SharedPage() {
  const { data, isPending, isError, refetch } = useSharedWithMe()
  const [filter, setFilter] = useState('')

  // Backend tidak mendukung pencarian di sini: saring di klien, beri label jujur (§1.8 poin 5)
  const visible = useMemo(() => {
    const term = filter.trim().toLowerCase()
    if (!data || !term) return data ?? []
    return data.filter((n) =>
      [n.title, n.content ?? '', n.ownerDisplayName ?? '', ...n.tags].some((v) => v.toLowerCase().includes(term)),
    )
  }, [data, filter])

  return (
    <>
      <h1 className="text-4xl tracking-[-0.02em]">Dibagikan ke saya</h1>
      <p className="mt-1 text-muted-foreground">Catatan milik orang lain. Kamu hanya bisa membacanya.</p>

      {data && data.length > 0 && (
        <label className="relative mt-8 block max-w-md">
          <span className="sr-only">Saring daftar di layar</span>
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <input
            type="search"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Saring daftar di layar…"
            className="w-full rounded-lg border border-border bg-card py-2.5 pl-9 pr-3 text-[15px] focus:border-primary focus:outline-none focus:ring-3 focus:ring-primary/30"
          />
        </label>
      )}

      <section className="mt-8">
        {isPending ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }, (_, i) => <CardSkeleton key={i} />)}
          </div>
        ) : isError ? (
          // Termasuk 404 "pemilik hilang" — tampil sebagai gangguan umum (§1.8 poin 3)
          <StateCard icon={<AlertIcon />} tone="pink" title="Daftar belum bisa dimuat" action={<Button variant="secondary" onClick={() => refetch()}>Coba lagi</Button>}>
            Sedang ada gangguan saat mengambil catatan yang dibagikan.
          </StateCard>
        ) : data.length === 0 ? (
          <StateCard icon={<ShareIcon />} tone="mint" title="Belum ada yang membagikan">
            Kalau teman membagikan catatan ke emailmu, catatan itu muncul di sini — hanya untuk dibaca.
          </StateCard>
        ) : visible.length === 0 ? (
          <StateCard icon={<SearchIcon />} title="Tidak ada yang cocok" action={<Button variant="secondary" onClick={() => setFilter('')}>Hapus saringan</Button>} />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {visible.map((note) => <NoteCard key={note.id} note={note} />)}
          </div>
        )}
      </section>
    </>
  )
}
