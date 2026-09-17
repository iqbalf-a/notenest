import { ApiError } from '../api/client'

// Pesan manusia untuk status backend. Jangan tampilkan message mentah (§1.6).
export function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Terjadi kesalahan. Coba lagi.'
  switch (error.status) {
    case 0:
      return 'Tidak bisa terhubung ke server. Periksa koneksimu.'
    case 403:
      return 'Kamu tidak punya akses ke catatan ini.'
    case 404:
      return 'Data tidak ditemukan.'
    case 502:
    case 503:
      return 'Layanan sedang bermasalah. Coba lagi sebentar.'
    default:
      return 'Terjadi kesalahan. Coba lagi.'
  }
}

export function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null
}
