import { http, HttpResponse, delay } from 'msw'
import { db, localDateTime, persist, uuid, type NoteRow, type ProfileRow, type ShareRow, type UserRow } from './db'

// Meniru gateway + ketiga service, termasuk kebiasaan yang kurang enak
// (urutan default naik, profil lazy, shared-with-me gagal total).
// Tujuannya: bug penyambungan ketahuan sejak fase dummy.

const LATENCY_MS = 250

function ok<T>(message: string, data: T, status = 200) {
  return HttpResponse.json({ success: true, message, data }, { status })
}

function fail(status: number, message: string, data: unknown = null) {
  return HttpResponse.json({ success: false, message, data }, { status })
}

// Paksa error dari URL untuk menguji keadaan gagal: ?__fail=502
function forcedFailure(request: Request) {
  const code = new URL(request.url).searchParams.get('__fail')
  return code ? fail(Number(code), `Forced failure ${code}`) : null
}

const tokenFor = (userId: string) => `mock-token.${userId}`

// Pengganti JwtAuthFilter
function authenticate(request: Request): UserRow | null {
  const header = request.headers.get('Authorization')
  if (!header?.toLowerCase().startsWith('bearer ')) return null
  const userId = header.slice(7).replace(/^mock-token\./, '')
  return db.users.find((u) => u.id === userId) ?? null
}

const unauthorized = () => fail(401, 'Missing or invalid Authorization header')

function validation(errors: Record<string, string>) {
  return Object.keys(errors).length ? fail(400, 'Validation failed', errors) : null
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function authResponse(user: UserRow) {
  return {
    accessToken: tokenFor(user.id),
    tokenType: 'Bearer',
    userId: user.id,
    email: user.email,
    displayName: user.displayName,
    role: 'USER',
  }
}

function findOrCreateProfile(user: UserRow): ProfileRow {
  let profile = db.profiles.find((p) => p.userId === user.id)
  if (!profile) {
    const now = localDateTime()
    profile = {
      id: uuid(), userId: user.id, email: user.email, displayName: user.displayName,
      bio: null, avatarUrl: null, createdAt: now, updatedAt: now,
    }
    db.profiles.push(profile)
    persist()
  }
  return profile
}

function noteResponse(note: NoteRow, owned: boolean, owner?: ProfileRow) {
  return {
    ...note,
    tags: [...note.tags],
    owned,
    ownerEmail: owner?.email ?? null,
    ownerDisplayName: owner?.displayName ?? null,
  }
}

function shareResponse(share: ShareRow) {
  return { ...share, permission: 'READ' as const }
}

type NoteBody = { title?: string; content?: string | null; tags?: string[] }

function validateNote(body: NoteBody) {
  const errors: Record<string, string> = {}
  if (!body.title?.trim()) errors.title = 'Title is required'
  else if (body.title.length > 200) errors.title = 'Title must be at most 200 characters'
  if ((body.content ?? '').length > 20000) errors.content = 'Content must be at most 20000 characters'
  if ((body.tags ?? []).length > 10) errors.tags = 'A note can have at most 10 tags'
  return validation(errors)
}

function normalizeTags(tags: string[] = []) {
  return [...new Set(tags.map((t) => t.trim().toLowerCase()).filter(Boolean))]
}

function findNote(id: string) {
  return db.notes.find((n) => n.id === id)
}

export const handlers = [
  http.all('*/api/*', async ({ request }) => {
    await delay(LATENCY_MS)
    return forcedFailure(request) ?? undefined
  }),

  // ===== auth-service =====
  http.post('*/api/auth/register', async ({ request }) => {
    const body = (await request.json()) as { displayName?: string; email?: string; password?: string }
    const errors: Record<string, string> = {}
    if (!body.displayName?.trim()) errors.displayName = 'Display name is required'
    else if (body.displayName.length > 60) errors.displayName = 'Display name must be at most 60 characters'
    if (!body.email?.trim()) errors.email = 'Email is required'
    else if (!EMAIL_RE.test(body.email)) errors.email = 'Invalid email format'
    if (!body.password || body.password.length < 8) errors.password = 'Password must be at least 8 characters'
    const invalid = validation(errors)
    if (invalid) return invalid

    const email = body.email!.trim()
    if (db.users.some((u) => u.email.toLowerCase() === email.toLowerCase())) {
      return fail(409, `Email already registered: ${email}`)
    }
    // Tidak membuat profil — sama seperti AuthServiceImpl
    const user: UserRow = { id: uuid(), email, password: body.password!, displayName: body.displayName!.trim() }
    db.users.push(user)
    persist()
    return ok('Registration successful', authResponse(user), 201)
  }),

  http.post('*/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string }
    const user = db.users.find((u) => u.email.toLowerCase() === body.email?.trim().toLowerCase())
    if (!user || user.password !== body.password) return fail(401, 'Invalid email or password')
    return ok('Login successful', authResponse(user))
  }),

  // ===== user-service =====
  http.get('*/api/users/me', ({ request }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    return ok('Profile found', findOrCreateProfile(user))
  }),

  http.put('*/api/users/me', async ({ request }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const body = (await request.json()) as { displayName?: string; bio?: string; avatarUrl?: string }
    const errors: Record<string, string> = {}
    if (!body.displayName?.trim()) errors.displayName = 'Display name is required'
    else if (body.displayName.length > 60) errors.displayName = 'Display name must be at most 60 characters'
    if ((body.bio ?? '').length > 500) errors.bio = 'Bio must be at most 500 characters'
    if ((body.avatarUrl ?? '').length > 255) errors.avatarUrl = 'Avatar URL must be at most 255 characters'
    const invalid = validation(errors)
    if (invalid) return invalid

    const profile = findOrCreateProfile(user)
    profile.displayName = body.displayName!.trim()
    profile.bio = body.bio || null
    profile.avatarUrl = body.avatarUrl || null
    profile.updatedAt = localDateTime()
    persist()
    return ok('Profile updated', profile)
  }),

  http.get('*/api/users/search', ({ request }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const term = new URL(request.url).searchParams.get('email')?.trim().toLowerCase() ?? ''
    if (!term) return ok('Users found', [])
    const results = db.profiles
      .filter((p) => p.email.toLowerCase().includes(term) && p.userId !== user.id)
      .sort((a, b) => a.email.localeCompare(b.email))
      .slice(0, 10)
      .map(({ userId, email, displayName }) => ({ userId, email, displayName }))
    return ok('Users found', results)
  }),

  // ===== note-service =====
  http.get('*/api/notes/shared-with-me', ({ request }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const shares = db.shares
      .filter((s) => s.sharedWithUserId === user.id)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    const result = []
    for (const share of shares) {
      const note = findNote(share.noteId)
      if (!note) continue
      const owner = db.profiles.find((p) => p.userId === note.ownerId)
      // Sama dengan backend: satu pemilik tanpa profil menggagalkan seluruh daftar
      if (!owner) return fail(404, `Note owner no longer exists: ${note.ownerId}`)
      result.push(noteResponse(note, false, owner))
    }
    return ok('Shared notes found', result)
  }),

  http.get('*/api/notes', ({ request }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const params = new URL(request.url).searchParams
    const tag = params.get('tag')?.trim().toLowerCase() || null
    const q = params.get('q')?.trim().toLowerCase() || null
    const page = Math.max(0, Number(params.get('page') ?? 0))
    const size = Math.max(1, Number(params.get('size') ?? 10))
    // Default backend: updatedAt NAIK kalau sort tidak dikirim
    const [sortField = 'updatedAt', direction = 'asc'] = (params.get('sort') ?? 'updatedAt').split(',')

    const filtered = db.notes
      .filter((n) => n.ownerId === user.id)
      .filter((n) => !tag || n.tags.includes(tag))
      .filter((n) => !q || n.title.toLowerCase().includes(q) || (n.content ?? '').toLowerCase().includes(q))
      .sort((a, b) => {
        const key = sortField as 'updatedAt' | 'createdAt' | 'title'
        const cmp = String(a[key] ?? '').localeCompare(String(b[key] ?? ''))
        return direction === 'desc' ? -cmp : cmp
      })

    const totalPages = Math.ceil(filtered.length / size)
    return ok('Notes found', {
      content: filtered.slice(page * size, page * size + size).map((n) => noteResponse(n, true)),
      page,
      size,
      totalElements: filtered.length,
      totalPages,
      last: page >= totalPages - 1,
    })
  }),

  http.post('*/api/notes', async ({ request }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const body = (await request.json()) as NoteBody
    const invalid = validateNote(body)
    if (invalid) return invalid
    const now = localDateTime()
    const note: NoteRow = {
      id: uuid(), ownerId: user.id, title: body.title!.trim(), content: body.content || null,
      tags: normalizeTags(body.tags), createdAt: now, updatedAt: now,
    }
    db.notes.push(note)
    persist()
    return ok('Note created', noteResponse(note, true), 201)
  }),

  http.get('*/api/notes/:id', ({ request, params }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const note = findNote(String(params.id))
    if (!note) return fail(404, `Note not found: ${params.id}`)
    const owned = note.ownerId === user.id
    if (!owned && !db.shares.some((s) => s.noteId === note.id && s.sharedWithUserId === user.id)) {
      return fail(403, 'You do not have access to this note')
    }
    return ok('Note found', noteResponse(note, owned))
  }),

  http.put('*/api/notes/:id', async ({ request, params }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const note = findNote(String(params.id))
    if (!note) return fail(404, `Note not found: ${params.id}`)
    if (note.ownerId !== user.id) return fail(403, 'Note does not belong to the current user')
    const body = (await request.json()) as NoteBody
    const invalid = validateNote(body)
    if (invalid) return invalid
    note.title = body.title!.trim()
    note.content = body.content || null
    note.tags = normalizeTags(body.tags)
    note.updatedAt = localDateTime()
    persist()
    return ok('Note updated', noteResponse(note, true))
  }),

  http.delete('*/api/notes/:id', ({ request, params }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const note = findNote(String(params.id))
    if (!note) return fail(404, `Note not found: ${params.id}`)
    if (note.ownerId !== user.id) return fail(403, 'Note does not belong to the current user')
    db.notes = db.notes.filter((n) => n.id !== note.id)
    db.shares = db.shares.filter((s) => s.noteId !== note.id) // cascade
    persist()
    return ok('Note deleted', null)
  }),

  http.get('*/api/notes/:id/shares', ({ request, params }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const note = findNote(String(params.id))
    if (!note) return fail(404, `Note not found: ${params.id}`)
    if (note.ownerId !== user.id) return fail(403, 'Note does not belong to the current user')
    const shares = db.shares
      .filter((s) => s.noteId === note.id)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      .map(shareResponse)
    return ok('Shares found', shares)
  }),

  http.post('*/api/notes/:id/share', async ({ request, params }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const body = (await request.json()) as { targetEmail?: string }
    const invalid = validation(
      !body.targetEmail?.trim()
        ? { targetEmail: 'Target email is required' }
        : !EMAIL_RE.test(body.targetEmail)
          ? { targetEmail: 'Invalid email format' }
          : {},
    )
    if (invalid) return invalid

    const note = findNote(String(params.id))
    if (!note) return fail(404, `Note not found: ${params.id}`)
    // Cek pemilik SEBELUM lookup user, sama seperti NoteServiceImpl
    if (note.ownerId !== user.id) return fail(403, 'Note does not belong to the current user')

    const email = body.targetEmail!.trim()
    const target = db.profiles.find((p) => p.email.toLowerCase() === email.toLowerCase())
    if (!target) return fail(404, `No NoteNest user registered with email: ${email}`)
    if (target.userId === user.id) return fail(400, 'You cannot share a note with yourself')

    const existing = db.shares.find((s) => s.noteId === note.id && s.sharedWithUserId === target.userId)
    if (existing) return ok('Note shared', shareResponse(existing), 201)

    const share: ShareRow = {
      id: uuid(), noteId: note.id, sharedWithUserId: target.userId,
      sharedWithEmail: target.email, createdAt: localDateTime(),
    }
    db.shares.push(share)
    persist()
    return ok('Note shared', shareResponse(share), 201)
  }),

  http.delete('*/api/notes/:id/share/:userId', ({ request, params }) => {
    const user = authenticate(request)
    if (!user) return unauthorized()
    const note = findNote(String(params.id))
    if (!note) return fail(404, `Note not found: ${params.id}`)
    if (note.ownerId !== user.id) return fail(403, 'Note does not belong to the current user')
    const share = db.shares.find((s) => s.noteId === note.id && s.sharedWithUserId === params.userId)
    if (!share) return fail(404, `Note ${note.id} is not shared with user ${params.userId}`)
    db.shares = db.shares.filter((s) => s.id !== share.id)
    persist()
    return ok('Share revoked', null)
  }),
]
