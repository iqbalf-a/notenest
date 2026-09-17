import { Link } from 'react-router'
import type { Note } from '../api/types'
import { formatRelative } from '../lib/format'
import { noteColor, NOTE_BG } from '../lib/noteColor'
import { cx, OwnerChip, Tags } from './ui'

export function NoteCard({ note }: { note: Note }) {
  // owned adalah satu-satunya penentu tampilan pinjaman — jangan menebak dari ownerId
  const borrowed = !note.owned
  return (
    <Link
      to={`/notes/${note.id}`}
      className={cx(
        'group flex flex-col rounded-xl p-5 text-foreground shadow-soft transition hover:-translate-y-0.5 hover:shadow-lift',
        NOTE_BG[noteColor(note.id)],
        borrowed ? 'border-2 border-dashed border-foreground/45' : 'border border-transparent',
      )}
    >
      {borrowed && (
        <span className="mb-2.5">
          <OwnerChip name={note.ownerDisplayName ?? 'orang lain'} />
        </span>
      )}
      <h3 className="line-clamp-2 text-[17px] leading-snug">{note.title}</h3>
      {note.content ? (
        <p className="mt-1.5 line-clamp-3 whitespace-pre-line text-sm text-on-pastel">{note.content}</p>
      ) : (
        <p className="mt-1.5 text-sm italic text-on-pastel">Tidak ada isi</p>
      )}
      <div className="mt-auto flex items-end justify-between gap-3 pt-4">
        <Tags tags={note.tags.slice(0, 4)} />
        <span className="shrink-0 text-xs text-on-pastel">
          {note.tags.length > 4 && <span className="mr-2">+{note.tags.length - 4} tag</span>}
          {formatRelative(note.updatedAt)}
        </span>
      </div>
    </Link>
  )
}
