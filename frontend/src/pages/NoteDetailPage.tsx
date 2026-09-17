import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { keys, useDeleteNote, useNote } from '../api/hooks'
import type { Note } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { SharePanel } from '../components/SharePanel'
import { AlertIcon, BackIcon, Button, cx, LockIcon, OwnerChip, ShareIcon, StateCard, Tags } from '../components/ui'
import { describeError, statusOf } from '../lib/errors'
import { formatFull } from '../lib/format'
import { noteColor, NOTE_BG } from '../lib/noteColor'

export function NoteDetailPage() {
  const { id = '' } = useParams()
  const { data, isPending, isError, error, refetch } = useNote(id)

  if (isPending) {
    return (
      <div className="mx-auto max-w-3xl" aria-busy>
        <div className="h-4 w-20 animate-pulse rounded bg-secondary" />
        <div className="mt-6 h-72 animate-pulse rounded-2xl bg-card" />
      </div>
    )
  }

  if (isError) {
    const status = statusOf(error)
    // 403 = tidak berhak (atau akses baru saja dicabut). Jangan logout (§1.6).
    if (status === 403) {
      return (
        <StateCard icon={<LockIcon />} tone="pink" title="Kamu tidak punya akses ke catatan ini"
          action={<Link to="/shared" className="font-semibold">Ke daftar yang dibagikan</Link>}>
          Mungkin pemiliknya sudah mencabut aksesmu.
        </StateCard>
      )
    }
    return (
      <StateCard icon={<AlertIcon />} tone="pink" title={status === 404 ? 'Catatan tidak ditemukan' : 'Catatan gagal dimuat'}
        action={status === 404
          ? <Link to="/notes" className="font-semibold">Kembali ke catatan saya</Link>
          : <Button variant="secondary" onClick={() => refetch()}>Coba lagi</Button>}>
        {status === 404 ? 'Catatan ini mungkin sudah dihapus.' : describeError(error)}
      </StateCard>
    )
  }

  return <NoteView note={data} />
}

function NoteView({ note }: { note: Note }) {
  const navigate = useNavigate()
  const remove = useDeleteNote()
  const [confirming, setConfirming] = useState(false)
  const [sharing, setSharing] = useState(false)
  const borrowed = !note.owned
  // GET /api/notes/{id} tidak membawa nama pemilik — ambil dari cache shared-with-me kalau ada
  const qc = useQueryClient()
  const ownerName =
    note.ownerDisplayName ?? qc.getQueryData<Note[]>(keys.shared)?.find((n) => n.id === note.id)?.ownerDisplayName ?? 'orang lain'

  return (
    <div className={cx('mx-auto grid gap-6', sharing ? 'max-w-6xl lg:grid-cols-[1fr_380px]' : 'max-w-3xl')}>
      <div className="min-w-0">
        <Link to={borrowed ? '/shared' : '/notes'} className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground">
          <BackIcon /> {borrowed ? 'Dibagikan ke saya' : 'Catatan saya'}
        </Link>

        <article className={cx(
          'mt-4 rounded-2xl p-6 shadow-soft sm:p-10',
          NOTE_BG[noteColor(note.id)],
          borrowed ? 'border-2 border-dashed border-foreground/45' : 'border border-transparent',
        )}>
          {borrowed && (
            <div className="mb-4">
              <OwnerChip name={ownerName} />
            </div>
          )}
          <h1 className="text-3xl leading-tight tracking-[-0.01em] break-words sm:text-[34px]">{note.title}</h1>
          <p className="mt-2 text-sm text-on-pastel">
            Diubah {formatFull(note.updatedAt)}
            {note.createdAt !== note.updatedAt && <> · dibuat {formatFull(note.createdAt)}</>}
          </p>
          <div className="mt-4"><Tags tags={note.tags} /></div>

          <div className="mt-6 rounded-xl bg-white/70 p-5 sm:p-7">
            {note.content ? (
              <p className="max-w-[68ch] whitespace-pre-wrap break-words text-base leading-[1.7]">{note.content}</p>
            ) : (
              <p className="italic text-muted-foreground">Catatan ini belum punya isi.</p>
            )}
          </div>
        </article>

        {/* Tidak ada tombol palsu: catatan pinjaman tidak punya aksi sama sekali */}
        {!borrowed && (
          <div className="mt-5 flex flex-wrap gap-2">
            <Link to={`/notes/${note.id}/edit`} className="inline-flex items-center rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground shadow-lift hover:bg-primary-hover">
              Ubah
            </Link>
            <Button variant="secondary" onClick={() => setSharing((v) => !v)} aria-expanded={sharing}>
              <ShareIcon className="size-4" /> {sharing ? 'Tutup panel bagikan' : 'Bagikan'}
            </Button>
            <Button variant="danger" className="ml-auto" onClick={() => setConfirming(true)}>Hapus</Button>
          </div>
        )}
      </div>

      {sharing && !borrowed && (
        <div className="lg:pt-10">
          <SharePanel note={note} onClose={() => setSharing(false)} />
        </div>
      )}

      {!borrowed && <ConfirmDialog
        open={confirming}
        title="Hapus catatan ini?"
        confirmLabel="Hapus"
        loading={remove.isPending}
        onClose={() => setConfirming(false)}
        onConfirm={async () => {
          await remove.mutateAsync(note.id)
          navigate('/notes', { replace: true })
        }}
      >
        “{note.title}” akan dihapus permanen. <b className="font-semibold text-foreground">Semua orang yang kamu bagikan juga kehilangan aksesnya.</b>
      </ConfirmDialog>}
    </div>
  )
}
