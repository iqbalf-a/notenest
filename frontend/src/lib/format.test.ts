import { describe, expect, it } from 'vitest'
import { formatFull, formatRelative, initial } from './format'

const now = new Date(2026, 8, 17, 14, 0, 0)

describe('formatRelative', () => {
  it('menampilkan menit dan jam untuk hari ini', () => {
    expect(formatRelative('2026-09-17T13:59:40.000', now)).toBe('baru saja')
    expect(formatRelative('2026-09-17T13:48:00.000', now)).toBe('12 menit lalu')
    expect(formatRelative('2026-09-17T11:00:00.000', now)).toBe('3 jam lalu')
  })

  it('menampilkan Kemarin, lalu tanggal pendek, lalu dengan tahun', () => {
    expect(formatRelative('2026-09-16T23:00:00.000', now)).toBe('Kemarin')
    expect(formatRelative('2026-09-14T09:00:00.000', now)).toBe('14 Sep')
    expect(formatRelative('2025-09-14T09:00:00.000', now)).toBe('14 Sep 2025')
  })
})

describe('formatFull', () => {
  it('memakai titik sebagai pemisah jam', () => {
    expect(formatFull('2026-09-17T11:02:45.908')).toBe('17 September 2026, 11.02')
  })
})

describe('initial', () => {
  it('mengambil huruf pertama kapital', () => {
    expect(initial(' ani lestari')).toBe('A')
    expect(initial('')).toBe('?')
  })
})
