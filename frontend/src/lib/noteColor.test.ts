import { describe, expect, it } from 'vitest'
import { NOTE_COLORS, noteColor } from './noteColor'

describe('noteColor', () => {
  it('selalu memberi warna yang sama untuk id yang sama', () => {
    const id = '3f1c2a3e-0000-4000-8000-00000000a001'
    expect(noteColor(id)).toBe(noteColor(id))
  })

  it('memakai kelima warna pastel untuk id yang beragam', () => {
    const used = new Set(Array.from({ length: 200 }, () => noteColor(crypto.randomUUID())))
    expect(used).toEqual(new Set(NOTE_COLORS))
  })
})
