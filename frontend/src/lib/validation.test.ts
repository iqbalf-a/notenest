import { describe, expect, it } from 'vitest'
import { noteSchema, normalizeTags, registerSchema } from './validation'

describe('aturan validasi meniru @Valid backend', () => {
  it('menolak password kurang dari 8 karakter', () => {
    expect(registerSchema.safeParse({ displayName: 'Ani', email: 'ani@example.com', password: '1234567' }).success).toBe(false)
  })

  it('menolak lebih dari 10 tag dan judul lebih dari 200 karakter', () => {
    const tags = Array.from({ length: 11 }, (_, i) => `t${i}`)
    expect(noteSchema.safeParse({ title: 'ok', content: '', tags }).success).toBe(false)
    expect(noteSchema.safeParse({ title: 'x'.repeat(201), content: '', tags: [] }).success).toBe(false)
  })
})

describe('normalizeTags', () => {
  it('trim, lowercase, dan buang duplikat seperti NoteServiceImpl', () => {
    expect(normalizeTags([' Java', 'java', 'Spring ', ''])).toEqual(['java', 'spring'])
  })
})
