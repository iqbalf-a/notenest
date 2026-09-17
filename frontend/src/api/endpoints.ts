import { request } from './client'
import type {
  AuthResponse,
  LoginRequest,
  Note,
  NoteListParams,
  NoteRequest,
  PageResponse,
  Profile,
  RegisterRequest,
  Share,
  ShareRequest,
  UpdateProfileRequest,
  UserSummary,
} from './types'

// Satu fungsi per endpoint gateway. Tidak ada logika di sini.
export const api = {
  register: (body: RegisterRequest) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body, skipAuthRedirect: true }),
  login: (body: LoginRequest) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body, skipAuthRedirect: true }),

  getMe: () => request<Profile>('/api/users/me'),
  updateMe: (body: UpdateProfileRequest) => request<Profile>('/api/users/me', { method: 'PUT', body }),
  searchUsers: (email: string) => request<UserSummary[]>('/api/users/search', { query: { email } }),

  // sort dikirim eksplisit: default backend updatedAt NAIK (docs/FRONTEND-BRIEF.html §1.8 poin 1)
  listNotes: ({ q, tag, page = 0, size = 12 }: NoteListParams) =>
    request<PageResponse<Note>>('/api/notes', { query: { q, tag, page, size, sort: 'updatedAt,desc' } }),
  sharedWithMe: () => request<Note[]>('/api/notes/shared-with-me'),
  getNote: (id: string) => request<Note>(`/api/notes/${id}`),
  createNote: (body: NoteRequest) => request<Note>('/api/notes', { method: 'POST', body }),
  updateNote: (id: string, body: NoteRequest) => request<Note>(`/api/notes/${id}`, { method: 'PUT', body }),
  deleteNote: (id: string) => request<null>(`/api/notes/${id}`, { method: 'DELETE' }),

  listShares: (noteId: string) => request<Share[]>(`/api/notes/${noteId}/shares`),
  share: (noteId: string, body: ShareRequest) =>
    request<Share>(`/api/notes/${noteId}/share`, { method: 'POST', body }),
  revokeShare: (noteId: string, userId: string) =>
    request<null>(`/api/notes/${noteId}/share/${userId}`, { method: 'DELETE' }),
}
