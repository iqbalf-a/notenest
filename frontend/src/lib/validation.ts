import { z } from 'zod'

// Tiruan persis @Valid di backend (docs/FRONTEND-BRIEF.html §1.6)

const email = z.string().trim().min(1, 'Email wajib diisi').email('Format email tidak valid')

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'Password wajib diisi'),
})

export const registerSchema = z.object({
  displayName: z.string().trim().min(1, 'Nama tampilan wajib diisi').max(60, 'Maksimal 60 karakter'),
  email,
  password: z.string().min(8, 'Password minimal 8 karakter'),
})

export const TITLE_MAX = 200
export const CONTENT_MAX = 20_000
export const TAGS_MAX = 10
export const TAG_MAX = 30

export const noteSchema = z.object({
  title: z.string().trim().min(1, 'Judul wajib diisi').max(TITLE_MAX, `Maksimal ${TITLE_MAX} karakter`),
  content: z.string().max(CONTENT_MAX, `Maksimal ${CONTENT_MAX.toLocaleString('id-ID')} karakter`),
  tags: z
    .array(z.string().trim().min(1, 'Tag tidak boleh kosong').max(TAG_MAX, `Tag maksimal ${TAG_MAX} karakter`))
    .max(TAGS_MAX, `Maksimal ${TAGS_MAX} tag`),
})

export const shareSchema = z.object({ targetEmail: email })

export const profileSchema = z.object({
  displayName: z.string().trim().min(1, 'Nama tampilan wajib diisi').max(60, 'Maksimal 60 karakter'),
  bio: z.string().max(500, 'Maksimal 500 karakter'),
  avatarUrl: z.string().max(255, 'Maksimal 255 karakter'),
})

export type LoginForm = z.infer<typeof loginSchema>
export type RegisterForm = z.infer<typeof registerSchema>
export type NoteForm = z.infer<typeof noteSchema>
export type ProfileForm = z.infer<typeof profileSchema>

// Sama dengan normalisasi di NoteServiceImpl: trim, lowercase, tanpa duplikat
export function normalizeTags(tags: string[]): string[] {
  return [...new Set(tags.map((t) => t.trim().toLowerCase()).filter(Boolean))]
}
