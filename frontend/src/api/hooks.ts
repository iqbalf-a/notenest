import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './endpoints'
import type { NoteListParams, NoteRequest, UpdateProfileRequest } from './types'

export const keys = {
  me: ['me'] as const,
  notes: ['notes'] as const,
  noteList: (params: NoteListParams) => ['notes', 'list', params] as const,
  note: (id: string) => ['notes', 'detail', id] as const,
  shared: ['notes', 'shared'] as const,
  shares: (noteId: string) => ['shares', noteId] as const,
  userSearch: (email: string) => ['users', 'search', email] as const,
}

export function useMe() {
  return useQuery({ queryKey: keys.me, queryFn: api.getMe })
}

export function useUpdateMe() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateProfileRequest) => api.updateMe(body),
    onSuccess: (profile) => qc.setQueryData(keys.me, profile),
  })
}

export function useNoteList(params: NoteListParams) {
  return useQuery({
    queryKey: keys.noteList(params),
    queryFn: () => api.listNotes(params),
    placeholderData: keepPreviousData,
  })
}

// Share tidak realtime: segarkan saat tab kembali aktif (§1.8 poin 8)
export function useSharedWithMe() {
  return useQuery({ queryKey: keys.shared, queryFn: api.sharedWithMe, refetchOnWindowFocus: true })
}

export function useNote(id: string) {
  return useQuery({ queryKey: keys.note(id), queryFn: () => api.getNote(id), refetchOnWindowFocus: true })
}

export function useCreateNote() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: NoteRequest) => api.createNote(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.notes }),
  })
}

export function useUpdateNote(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: NoteRequest) => api.updateNote(id, body),
    onSuccess: (note) => {
      qc.setQueryData(keys.note(id), note)
      return qc.invalidateQueries({ queryKey: keys.notes })
    },
  })
}

export function useDeleteNote() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.deleteNote(id),
    onSuccess: (_, id) => {
      qc.removeQueries({ queryKey: keys.note(id) })
      return qc.invalidateQueries({ queryKey: keys.notes })
    },
  })
}

export function useShares(noteId: string) {
  return useQuery({ queryKey: keys.shares(noteId), queryFn: () => api.listShares(noteId) })
}

export function useUserSearch(email: string) {
  const term = email.trim()
  return useQuery({
    queryKey: keys.userSearch(term),
    queryFn: () => api.searchUsers(term),
    enabled: term.length >= 2,
    staleTime: 30_000,
  })
}

export function useShareNote(noteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (targetEmail: string) => api.share(noteId, { targetEmail }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.shares(noteId) }),
  })
}

export function useRevokeShare(noteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => api.revokeShare(noteId, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.shares(noteId) }),
  })
}
