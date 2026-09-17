import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { Note } from '../api/types'
import { NoteCard } from './NoteCard'

const base: Note = {
  id: 'n-1',
  ownerId: 'u-1',
  title: 'Resep sambal matah',
  content: 'Bawang merah, serai',
  tags: ['masak'],
  owned: true,
  ownerEmail: null,
  ownerDisplayName: null,
  createdAt: '2026-09-17T10:00:00.000',
  updatedAt: '2026-09-17T10:00:00.000',
}

function renderCard(note: Note) {
  return render(<MemoryRouter><NoteCard note={note} /></MemoryRouter>)
}

describe('NoteCard', () => {
  it('catatan milik sendiri tidak menampilkan penanda pinjaman', () => {
    renderCard(base)
    expect(screen.queryByText(/dibagikan oleh/)).not.toBeInTheDocument()
  })

  it('catatan pinjaman ditandai label teks, bukan hanya warna', () => {
    renderCard({ ...base, owned: false, ownerDisplayName: 'Ani' })
    expect(screen.getByText(/dibagikan oleh Ani · hanya baca/)).toBeInTheDocument()
  })

  it('isi kosong tetap terlihat disengaja', () => {
    renderCard({ ...base, content: null })
    expect(screen.getByText('Tidak ada isi')).toBeInTheDocument()
  })
})
