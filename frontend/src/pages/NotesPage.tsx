import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useNoteList } from '../api/hooks'
import { NoteCard } from '../components/NoteCard'
import { AlertIcon, Button, CardSkeleton, cx, PenIcon, PlusIcon, SearchIcon, StateCard } from '../components/ui'
import { describeError } from '../lib/errors'

const PAGE_SIZE = 12

export function NotesPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? 0)

  const [draft, setDraft] = useState(q)
  useEffect(() => setDraft(q), [q])

  // Debounce pencarian supaya tidak ada request tiap ketukan
  useEffect(() => {
    if (draft === q) return
    const timer = setTimeout(() => update({ q: draft, page: '' }), 300)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft])

  function update(next: Record<string, string>) {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    setParams(merged, { replace: true })
  }

  const { data, isPending, isError, error, refetch, isFetching } = useNoteList({ q, page, size: PAGE_SIZE })

  const filtering = Boolean(q)

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-4xl tracking-[-0.02em]">Catatan saya</h1>
          <p className="mt-1 text-muted-foreground">
            {data ? `${data.totalElements.toLocaleString('id-ID')} catatan` : ' '}
          </p>
        </div>
        <Link to="/notes/new" className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground shadow-lift hover:bg-primary-hover">
          <PlusIcon /> Tulis catatan
        </Link>
      </div>

      <div className="mt-8 flex flex-col gap-3">
        <label className="relative block max-w-md">
          <span className="sr-only">Cari catatan</span>
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <input
            type="search"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Cari judul atau isi…"
            className="w-full rounded-lg border border-border bg-card py-2.5 pl-9 pr-3 text-[15px] focus:border-primary focus:outline-none focus:ring-3 focus:ring-primary/30"
          />
        </label>
      </div>

      <section className="mt-8" aria-busy={isFetching}>
        {isPending ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => <CardSkeleton key={i} />)}
          </div>
        ) : isError ? (
          <StateCard icon={<AlertIcon />} tone="pink" title="Catatan gagal dimuat" action={<Button variant="secondary" onClick={() => refetch()}>Coba lagi</Button>}>
            {describeError(error)}
          </StateCard>
        ) : data.content.length === 0 ? (
          filtering ? (
            <StateCard icon={<SearchIcon />} title="Tidak ada catatan yang cocok" action={<Button variant="secondary" onClick={() => update({ q: '', page: '' })}>Hapus pencarian</Button>}>
              Coba kata lain.
            </StateCard>
          ) : (
            <StateCard icon={<PenIcon />} title="Belum ada catatan" action={<Link to="/notes/new" className="inline-flex rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground shadow-lift">Tulis catatan</Link>}>
              Tulis hal pertama yang ingin kamu ingat.
            </StateCard>
          )
        ) : (
          <>
            <div className={cx('grid gap-4 sm:grid-cols-2 lg:grid-cols-3', isFetching && 'opacity-70')}>
              {data.content.map((note) => <NoteCard key={note.id} note={note} />)}
            </div>
            {data.totalPages > 1 && (
              <nav className="mt-10 flex items-center justify-center gap-3" aria-label="Halaman">
                <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => update({ page: page - 1 ? String(page - 1) : '' })}>Sebelumnya</Button>
                <span className="text-sm tabular-nums text-muted-foreground">Halaman {data.page + 1} dari {data.totalPages}</span>
                <Button variant="secondary" size="sm" disabled={data.last} onClick={() => update({ page: String(page + 1) })}>Berikutnya</Button>
              </nav>
            )}
          </>
        )}
      </section>
    </>
  )
}
