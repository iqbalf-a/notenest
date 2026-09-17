// Cermin DTO backend. Kalau DTO di backend/ berubah, file ini yang pertama diperbarui.
// Sumber: docs/FRONTEND-BRIEF.html §1.6

export type ApiResponse<T> = {
  success: boolean
  message: string
  data: T | null
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export type AuthResponse = {
  accessToken: string
  tokenType: 'Bearer'
  userId: string
  email: string
  displayName: string
  role: 'USER' | 'ADMIN'
}

export type Note = {
  id: string
  ownerId: string
  title: string
  content: string | null
  tags: string[]
  owned: boolean
  ownerEmail: string | null
  ownerDisplayName: string | null
  createdAt: string
  updatedAt: string
}

export type Share = {
  id: string
  noteId: string
  sharedWithUserId: string
  sharedWithEmail: string
  permission: 'READ'
  createdAt: string
}

export type UserSummary = {
  userId: string
  email: string
  displayName: string
}

export type Profile = {
  id: string
  userId: string
  email: string
  displayName: string
  bio: string | null
  avatarUrl: string | null
  createdAt: string
  updatedAt: string
}

export type RegisterRequest = { displayName: string; email: string; password: string }
export type LoginRequest = { email: string; password: string }
export type NoteRequest = { title: string; content?: string; tags?: string[] }
export type ShareRequest = { targetEmail: string }
export type UpdateProfileRequest = { displayName: string; bio?: string; avatarUrl?: string }

export type NoteListParams = {
  q?: string
  tag?: string
  page?: number
  size?: number
}
