import { zodResolver } from '@hookform/resolvers/zod'
import { useState, type ReactNode } from 'react'
import { useForm, type FieldValues, type Path, type UseFormSetError } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { API_BASE_URL } from '../api/client'
import { api } from '../api/endpoints'
import { Logo } from '../components/AppLayout'
import { Button, InputField } from '../components/ui'
import { describeError } from '../lib/errors'
import { useSession } from '../lib/session'
import { loginSchema, registerSchema, type LoginForm, type RegisterForm } from '../lib/validation'

const USING_MOCKS = !API_BASE_URL

// 400 dari backend membawa { field: pesan } — tempel ke field-nya
function applyFieldErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>) {
  if (error instanceof ApiError && error.fieldErrors) {
    for (const [field, message] of Object.entries(error.fieldErrors)) {
      setError(field as Path<T>, { message })
    }
    return true
  }
  return false
}

function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <aside className="hidden flex-col justify-between bg-card p-12 lg:flex">
        <Logo />
        <div>
          <h1 className="max-w-md text-5xl leading-[1.1] tracking-[-0.02em]">Tempat menulis catatan yang tenang dan rapi.</h1>
          <p className="mt-4 max-w-md text-lg text-muted-foreground">Tulis untuk dirimu sendiri. Pinjamkan satu catatan ke teman kalau perlu — mereka hanya bisa membaca.</p>
          <div className="mt-10 grid max-w-lg grid-cols-3 gap-3">
            <div className="rounded-xl bg-note-blue p-4 shadow-soft"><b className="block font-semibold">Catatan cepat</b><span className="text-sm text-on-pastel">Judul, isi, tag.</span></div>
            <div className="rounded-xl bg-note-pink p-4 shadow-soft"><b className="block font-semibold">Tag sendiri</b><span className="text-sm text-on-pastel">Cari dan saring.</span></div>
            <div className="rounded-xl bg-note-yellow p-4 shadow-soft"><b className="block font-semibold">Bagikan</b><span className="text-sm text-on-pastel">Hanya untuk dibaca.</span></div>
          </div>
        </div>
        <span className="text-sm text-muted-foreground">NoteNest · sister project ShopNest</span>
      </aside>

      <main className="flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden"><Logo /></div>
          <h1 className="text-3xl tracking-[-0.02em]">{title}</h1>
          <p className="mt-1.5 text-muted-foreground">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <p className="mt-6 text-sm text-muted-foreground">{footer}</p>
        </div>
      </main>
    </div>
  )
}

export function LoginPage() {
  const { start, expired } = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setValue, formState: { errors, isSubmitting } } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  })

  async function onSubmit(values: LoginForm) {
    setFormError(null)
    try {
      await start(await api.login(values))
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/notes', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setFormError('Email atau password salah.')
      else setFormError(describeError(error))
    }
  }

  return (
    <AuthShell title="Masuk" subtitle="Lanjutkan ke catatanmu." footer={<>Belum punya akun? <Link to="/register" className="font-semibold">Buat akun gratis</Link></>}>
      {expired && (
        <p className="mb-5 rounded-xl border border-[#EFDC8C] bg-[#FBF3CF] px-4 py-3 text-sm" role="status">
          Sesi kamu sudah habis. Silakan masuk lagi.
        </p>
      )}
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
        <InputField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <InputField label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...register('password')} />
        {formError && <p className="text-sm text-danger-text" role="alert">{formError}</p>}
        <Button type="submit" loading={isSubmitting} className="mt-2">Masuk</Button>
      </form>

      {USING_MOCKS && (
        <div className="mt-8 rounded-xl border border-dashed border-border bg-card p-4 text-sm">
          <b className="font-semibold">Mode dummy.</b>{' '}
          <span className="text-muted-foreground">Semua akun memakai password <code>rahasia123</code>.</span>
          <div className="mt-3 flex flex-wrap gap-2">
            {[['Ani', 'ani@example.com'], ['Budi', 'budi@example.com'], ['Citra', 'citra@example.com']].map(([name, email]) => (
              <Button key={email} type="button" size="sm" variant="secondary" onClick={() => {
                setValue('email', email)
                setValue('password', 'rahasia123')
              }}>
                Isi sebagai {name}
              </Button>
            ))}
          </div>
        </div>
      )}
    </AuthShell>
  )
}

export function RegisterPage() {
  const { start } = useSession()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
  })

  async function onSubmit(values: RegisterForm) {
    setFormError(null)
    try {
      await start(await api.register(values))
      navigate('/notes', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) setError('email', { message: 'Email ini sudah terdaftar.' })
      else if (!applyFieldErrors(error, setError)) setFormError(describeError(error))
    }
  }

  return (
    <AuthShell title="Buat akun" subtitle="Gratis, cukup nama, email, dan password." footer={<>Sudah punya akun? <Link to="/login" className="font-semibold">Masuk</Link></>}>
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
        <InputField label="Nama tampilan" autoComplete="name" maxLength={60} error={errors.displayName?.message} {...register('displayName')} />
        <InputField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <InputField label="Password" type="password" autoComplete="new-password" hint="Minimal 8 karakter." error={errors.password?.message} {...register('password')} />
        {formError && <p className="text-sm text-danger-text" role="alert">{formError}</p>}
        <Button type="submit" loading={isSubmitting} className="mt-2">Buat akun</Button>
      </form>
    </AuthShell>
  )
}
