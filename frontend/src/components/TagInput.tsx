import { useId, useState, type KeyboardEvent } from 'react'
import { TAG_MAX, TAGS_MAX, normalizeTags } from '../lib/validation'
import { CloseIcon, cx } from './ui'

type Props = {
  value: string[]
  onChange: (tags: string[]) => void
  error?: string
}

// Enter atau koma menambah tag. Disimpan lowercase, seperti backend.
export function TagInput({ value, onChange, error }: Props) {
  const id = useId()
  const [draft, setDraft] = useState('')
  const full = value.length >= TAGS_MAX

  function commit() {
    const next = normalizeTags([...value, draft.slice(0, TAG_MAX)])
    if (next.length <= TAGS_MAX) onChange(next)
    setDraft('')
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if ((event.key === 'Enter' || event.key === ',') && draft.trim()) {
      event.preventDefault()
      commit()
    } else if (event.key === 'Backspace' && !draft && value.length) {
      onChange(value.slice(0, -1))
    }
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[13px] font-semibold">
        Tag <span className="font-normal text-muted-foreground">(opsional, maks {TAGS_MAX})</span>
      </label>
      <div
        className={cx(
          'flex flex-wrap items-center gap-1.5 rounded-lg border bg-card px-2.5 py-2 focus-within:border-primary focus-within:ring-3 focus-within:ring-primary/30',
          error ? 'border-danger' : 'border-border',
        )}
      >
        {value.map((tag) => (
          <span key={tag} className="inline-flex items-center gap-1 rounded-full bg-secondary py-0.5 pl-2.5 pr-1 text-[13px] font-medium">
            {tag}
            <button
              type="button"
              onClick={() => onChange(value.filter((t) => t !== tag))}
              className="grid size-5 place-items-center rounded-full text-muted-foreground hover:bg-border hover:text-foreground"
              aria-label={`Hapus tag ${tag}`}
            >
              <CloseIcon className="size-3" />
            </button>
          </span>
        ))}
        <input
          id={id}
          value={draft}
          onChange={(e) => setDraft(e.target.value.replace(',', ''))}
          onKeyDown={onKeyDown}
          onBlur={() => draft.trim() && commit()}
          disabled={full}
          maxLength={TAG_MAX}
          placeholder={full ? 'Sudah 10 tag' : value.length ? '' : 'mis. kerja, resep'}
          className="min-w-24 flex-1 bg-transparent py-0.5 text-[15px] outline-none placeholder:text-muted-foreground/80 disabled:cursor-not-allowed"
        />
      </div>
      <span className={cx('text-[12.5px]', error ? 'text-danger-text' : 'text-muted-foreground')}>
        {error ?? 'Tekan Enter atau koma untuk menambah.'}
      </span>
    </div>
  )
}
