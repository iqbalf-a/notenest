import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { useCreateNote, useNote, useUpdateNote } from '../api/hooks'
import type { Note } from '../api/types'
import { TagInput } from '../components/TagInput'
import { AlertIcon, BackIcon, Button, InputField, StateCard, TextareaField } from '../components/ui'
import { describeError, statusOf } from '../lib/errors'
import { CONTENT_MAX, noteSchema, normalizeTags, TITLE_MAX, type NoteForm } from '../lib/validation'

const DRAFT_KEY = 'notenest.draft.new'

function readDraft(): NoteForm | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    return raw ? (JSON.parse(raw) as NoteForm) : null
  } catch {
    return null
  }
}

export function NewNotePage() {
  const create = useCreateNote()
  const navigate = useNavigate()
  return (
    <NoteForm
      heading="Tulis catatan"
      initial={readDraft() ?? { title: '', content: '', tags: [] }}
      // Draf disimpan lokal: token 24 jam bisa habis di tengah menulis (§1.8 poin 7)
      draftKey={DRAFT_KEY}
      cancelTo="/notes"
      submitLabel="Simpan catatan"
      onSubmit={async (values) => {
        const note = await create.mutateAsync(values)
        navigate(`/notes/${note.id}`, { replace: true })
      }}
    />
  )
}

export function EditNotePage() {
  const { id = '' } = useParams()
  const { data, isPending, isError, error } = useNote(id)

  if (isPending) return <p className="text-muted-foreground">Memuat…</p>
  if (isError) {
    return (
      <StateCard icon={<AlertIcon />} tone="pink" title={statusOf(error) === 404 ? 'Catatan tidak ditemukan' : 'Catatan gagal dimuat'}
        action={<Link to="/notes" className="font-semibold">Kembali ke catatan saya</Link>}>
        {describeError(error)}
      </StateCard>
    )
  }
  // Catatan pinjaman tidak boleh dibuka di editor — PUT pasti 403 (§1.5)
  if (!data.owned) return <Navigate to={`/notes/${id}`} replace />
  return <EditForm note={data} />
}

function EditForm({ note }: { note: Note }) {
  const update = useUpdateNote(note.id)
  const navigate = useNavigate()
  return (
    <NoteForm
      heading="Ubah catatan"
      initial={{ title: note.title, content: note.content ?? '', tags: note.tags }}
      cancelTo={`/notes/${note.id}`}
      submitLabel="Simpan perubahan"
      onSubmit={async (values) => {
        await update.mutateAsync(values)
        navigate(`/notes/${note.id}`, { replace: true })
      }}
    />
  )
}

type NoteFormProps = {
  heading: string
  initial: NoteForm
  draftKey?: string
  cancelTo: string
  submitLabel: string
  onSubmit: (values: { title: string; content: string; tags: string[] }) => Promise<void>
}

function NoteForm({ heading, initial, draftKey, cancelTo, submitLabel, onSubmit }: NoteFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const { register, control, handleSubmit, watch, setError, formState: { errors, isSubmitting } } = useForm<NoteForm>({
    resolver: zodResolver(noteSchema),
    defaultValues: initial,
  })

  const title = watch('title') ?? ''
  const content = watch('content') ?? ''

  useEffect(() => {
    if (!draftKey) return
    const subscription = watch((values) => {
      try {
        localStorage.setItem(draftKey, JSON.stringify(values))
      } catch {
        // abaikan
      }
    })
    return () => subscription.unsubscribe()
  }, [watch, draftKey])

  async function submit(values: NoteForm) {
    setFormError(null)
    try {
      await onSubmit({ title: values.title.trim(), content: values.content, tags: normalizeTags(values.tags) })
      if (draftKey) localStorage.removeItem(draftKey)
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as keyof NoteForm, { message })
        }
      } else if (statusOf(error) !== 401) {
        setFormError(describeError(error))
      }
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <Link to={cancelTo} className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground">
        <BackIcon /> Kembali
      </Link>
      <h1 className="mt-3 text-3xl tracking-[-0.02em]">{heading}</h1>

      <form onSubmit={handleSubmit(submit)} className="mt-8 flex flex-col gap-5 rounded-2xl border border-border bg-card p-5 shadow-soft sm:p-8" noValidate>
        <InputField label="Judul" autoFocus error={errors.title?.message} count={{ value: title.length, max: TITLE_MAX }} {...register('title')} />
        <TextareaField label="Isi" rows={14} placeholder="Tulis apa saja…" error={errors.content?.message} count={{ value: content.length, max: CONTENT_MAX }} {...register('content')} />
        <Controller
          control={control}
          name="tags"
          render={({ field, fieldState }) => (
            <TagInput value={field.value} onChange={field.onChange} error={fieldState.error?.message ?? errors.tags?.root?.message ?? errors.tags?.[0]?.message} />
          )}
        />
        {formError && <p className="text-sm text-danger-text" role="alert">{formError}</p>}
        <div className="sticky bottom-16 -mx-5 flex justify-end gap-2 border-t border-border bg-card px-5 pt-4 sm:static sm:mx-0 sm:border-0 sm:px-0">
          <Link to={cancelTo} className="inline-flex items-center rounded-lg border border-border px-4 py-2.5 text-sm font-semibold hover:bg-secondary">Batal</Link>
          <Button type="submit" loading={isSubmitting}>{submitLabel}</Button>
        </div>
      </form>
    </div>
  )
}
