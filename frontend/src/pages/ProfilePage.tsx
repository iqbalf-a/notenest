import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { ApiError } from '../api/client'
import { useMe, useUpdateMe } from '../api/hooks'
import { AlertIcon, Avatar, Button, InputField, StateCard, TextareaField } from '../components/ui'
import { describeError } from '../lib/errors'
import { useSession } from '../lib/session'
import { profileSchema, type ProfileForm } from '../lib/validation'

export function ProfilePage() {
  const { data: profile, isPending, isError, error, refetch } = useMe()
  const update = useUpdateMe()
  const { setDisplayName } = useSession()
  const [saved, setSaved] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const { register, handleSubmit, reset, watch, setError, formState: { errors, isDirty, isSubmitting } } = useForm<ProfileForm>({
    resolver: zodResolver(profileSchema),
    defaultValues: { displayName: '', bio: '', avatarUrl: '' },
  })

  useEffect(() => {
    if (profile) reset({ displayName: profile.displayName, bio: profile.bio ?? '', avatarUrl: profile.avatarUrl ?? '' })
  }, [profile, reset])

  if (isPending) return <div className="mx-auto h-96 max-w-2xl animate-pulse rounded-2xl bg-card" aria-busy />
  if (isError) {
    return (
      <StateCard icon={<AlertIcon />} tone="pink" title="Profil gagal dimuat" action={<Button variant="secondary" onClick={() => refetch()}>Coba lagi</Button>}>
        {describeError(error)}
      </StateCard>
    )
  }

  async function onSubmit(values: ProfileForm) {
    setSaved(false)
    setFormError(null)
    try {
      const next = await update.mutateAsync({
        displayName: values.displayName.trim(),
        bio: values.bio || undefined,
        avatarUrl: values.avatarUrl || undefined,
      })
      // Nama di token tidak ikut berubah — perbarui dari response profil (§1.8 poin 6)
      setDisplayName(next.displayName)
      setSaved(true)
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors) {
        for (const [field, message] of Object.entries(e.fieldErrors)) setError(field as keyof ProfileForm, { message })
      } else {
        setFormError(describeError(e))
      }
    }
  }

  const name = watch('displayName') || profile.displayName
  const bio = watch('bio') ?? ''

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-4xl tracking-[-0.02em]">Profil</h1>

      <div className="mt-8 flex items-center gap-4 rounded-2xl border border-border bg-card p-5 shadow-soft">
        <Avatar name={name} seed={profile.userId} size={56} />
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold">{name}</p>
          <p className="truncate text-sm text-muted-foreground">{profile.email}</p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 flex flex-col gap-5 rounded-2xl border border-border bg-card p-5 shadow-soft sm:p-8" noValidate>
        <InputField label="Nama tampilan" error={errors.displayName?.message} maxLength={60} {...register('displayName')} />
        <InputField label="Email" value={profile.email} disabled hint="Email tidak bisa diubah." readOnly className="bg-secondary text-muted-foreground" />
        <TextareaField label="Bio" rows={4} error={errors.bio?.message} count={{ value: bio.length, max: 500 }} {...register('bio')} />
        <InputField label="URL avatar" type="url" placeholder="https://…" hint="Opsional. Belum ditampilkan — avatar memakai inisial." error={errors.avatarUrl?.message} {...register('avatarUrl')} />
        {formError && <p className="text-sm text-danger-text" role="alert">{formError}</p>}
        <div className="flex items-center justify-end gap-3">
          {saved && !isDirty && <span className="text-sm text-muted-foreground" role="status">Tersimpan.</span>}
          <Button type="submit" loading={isSubmitting} disabled={!isDirty}>Simpan profil</Button>
        </div>
      </form>
    </div>
  )
}
