import type { ApiResponse } from './types'

// Kosong = dummy (MSW mencegat request relatif /api/*).
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  readonly status: number
  // Terisi hanya untuk 400 validasi: { field: pesan }
  readonly fieldErrors: Record<string, string> | null

  constructor(status: number, message: string, fieldErrors: Record<string, string> | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type TokenSource = () => string | null
type UnauthorizedHandler = () => void

let getToken: TokenSource = () => null
let onUnauthorized: UnauthorizedHandler = () => {}

// Dipasang oleh SessionProvider, supaya client tidak bergantung pada React.
export function configureClient(options: { getToken: TokenSource; onUnauthorized: UnauthorizedHandler }) {
  getToken = options.getToken
  onUnauthorized = options.onUnauthorized
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | undefined>
  // Login yang salah juga 401, tapi bukan "sesi habis" — jangan logout.
  skipAuthRedirect?: boolean
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(API_BASE_URL + path, window.location.origin)
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== '') url.searchParams.set(key, String(value))
  }

  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(url, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Tidak bisa terhubung ke server')
  }

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (!response.ok) {
    if (response.status === 401 && token && !options.skipAuthRedirect) onUnauthorized()
    const fieldErrors =
      response.status === 400 && payload?.data && typeof payload.data === 'object'
        ? (payload.data as Record<string, string>)
        : null
    throw new ApiError(response.status, payload?.message ?? response.statusText, fieldErrors)
  }

  return (payload?.data ?? null) as T
}
