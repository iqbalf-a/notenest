import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useRevokeShare, useShareNote, useShares, useUserSearch } from '../api/hooks'
import type { Note } from '../api/types'
import { formatRelative } from '../lib/format'
import { shareSchema } from '../lib/validation'
import { Avatar, Button, CloseIcon, InputField } from './ui'

// Pesan untuk kegagalan share — lihat docs/FRONTEND-BRIEF.html §1.6
function shareErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 404) return 'Belum ada pengguna NoteNest dengan email ini.'
    if (error.status === 400) return 'Kamu tidak bisa membagikan catatan ke dirimu sendiri.'
    if (error.status === 502 || error.status === 503) return 'Layanan sedang bermasalah. Catatan belum dibagikan — coba lagi sebentar.'
  }
  return 'Gagal membagikan. Coba lagi.'
}

export function SharePanel({ note, onClose }: { note: Note; onClose: () => void }) {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pendingRevoke, setPendingRevoke] = useState<string | null>(null)

  const shares = useShares(note.id)
  const search = useUserSearch(email)
  const share = useShareNote(note.id)
  const revoke = useRevokeShare(note.id)

  const sharedIds = new Set(shares.data?.map((s) => s.sharedWithUserId))
  const candidates = (search.data ?? []).filter((u) => !sharedIds.has(u.userId))

  async function shareTo(targetEmail: string) {
    setError(null)
    const parsed = shareSchema.safeParse({ targetEmail })
    if (!parsed.success) {
      setError(parsed.error.issues[0].message)
      return
    }
    try {
      await share.mutateAsync(parsed.data.targetEmail)
      setEmail('')
    } catch (e) {
      setError(shareErrorMessage(e))
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void shareTo(email)
  }

  const term = email.trim()

  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-card shadow-lift" aria-labelledby="share-heading">
      <header className="flex items-center justify-between gap-3 border-b border-border px-5 py-4">
        <h2 id="share-heading" className="truncate text-base">Bagikan “{note.title}”</h2>
        <button type="button" onClick={onClose} className="grid size-8 shrink-0 place-items-center rounded-lg text-muted-foreground hover:bg-secondary" aria-label="Tutup panel bagikan">
          <CloseIcon />
        </button>
      </header>

      <div className="px-5 py-4">
        <p className="text-sm text-muted-foreground">Penerima hanya bisa membaca. Mereka tidak bisa mengubah atau membagikannya lagi.</p>

        <form onSubmit={onSubmit} className="mt-4 flex flex-col gap-2" noValidate>
          <InputField
            label="Cari atau ketik email"
            type="email"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value)
              setError(null)
            }}
            placeholder="nama@contoh.com"
            autoComplete="off"
            error={error ?? undefined}
          />
          <Button type="submit" size="sm" variant="secondary" loading={share.isPending} disabled={!term} className="self-end">
            Bagikan ke email ini
          </Button>
        </form>

        {term.length >= 2 && (
          <div className="mt-3" aria-live="polite">
            {search.isFetching && !search.data ? (
              <p className="py-2 text-sm text-muted-foreground">Mencari…</p>
            ) : candidates.length > 0 ? (
              <ul>
                {candidates.map((user) => (
                  <li key={user.userId} className="flex items-center gap-3 border-t border-border py-2.5 first:border-t-0">
                    <Avatar name={user.displayName} seed={user.userId} />
                    <span className="min-w-0 flex-1">
                      <b className="block truncate text-sm font-semibold">{user.displayName}</b>
                      <span className="block truncate text-[12.5px] text-muted-foreground">{user.email}</span>
                    </span>
                    <Button size="sm" variant="secondary" onClick={() => shareTo(user.email)} disabled={share.isPending}>
                      Bagikan
                    </Button>
                  </li>
                ))}
              </ul>
            ) : search.data ? (
              // Profil dibuat saat login pertama — orang yang belum pernah masuk tidak muncul
              <p className="rounded-lg bg-secondary px-3 py-2.5 text-[13px] text-muted-foreground">
                Tidak ketemu? Orang itu perlu masuk ke NoteNest sekali dulu.
              </p>
            ) : null}
          </div>
        )}

        <h3 className="mt-6 text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">Bisa membaca</h3>
        {shares.isPending ? (
          <p className="py-3 text-sm text-muted-foreground">Memuat…</p>
        ) : shares.isError ? (
          <p className="py-3 text-sm text-danger-text">Daftar penerima gagal dimuat.</p>
        ) : shares.data.length === 0 ? (
          <p className="py-3 text-sm text-muted-foreground">Belum dibagikan ke siapa pun.</p>
        ) : (
          <ul className="mt-1">
            {shares.data.map((s) => (
              <li key={s.id} className="flex items-center gap-3 border-t border-border py-2.5 first:border-t-0">
                {/* Hanya email snapshot yang tersedia — jangan cari nama lagi */}
                <Avatar name={s.sharedWithEmail} seed={s.sharedWithUserId} />
                <span className="min-w-0 flex-1">
                  <b className="block truncate text-sm font-semibold">{s.sharedWithEmail}</b>
                  <span className="block text-[12.5px] text-muted-foreground">sejak {formatRelative(s.createdAt)}</span>
                </span>
                {pendingRevoke === s.sharedWithUserId ? (
                  <span className="flex gap-1">
                    <Button size="sm" variant="danger" loading={revoke.isPending} onClick={async () => {
                      await revoke.mutateAsync(s.sharedWithUserId)
                      setPendingRevoke(null)
                    }}>Ya, cabut</Button>
                    <Button size="sm" variant="ghost" onClick={() => setPendingRevoke(null)}>Batal</Button>
                  </span>
                ) : (
                  <button type="button" onClick={() => setPendingRevoke(s.sharedWithUserId)} className="text-[13px] font-semibold text-danger-text hover:underline">
                    Cabut
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
