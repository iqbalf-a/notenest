import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { configureClient } from '../api/client'
import { api } from '../api/endpoints'
import type { AuthResponse } from '../api/types'

export type SessionUser = Pick<AuthResponse, 'userId' | 'email' | 'displayName'>

type Session = { token: string; user: SessionUser }

type SessionContextValue = {
  session: Session | null
  // Dipanggil setelah login/register sukses
  start: (auth: AuthResponse) => Promise<void>
  end: (reason?: 'expired') => void
  expired: boolean
  setDisplayName: (name: string) => void
}

const STORAGE_KEY = 'notenest.session'
const SessionContext = createContext<SessionContextValue | null>(null)

function readStored(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

function writeStored(session: Session | null) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // storage diblokir: sesi tetap hidup di memori
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [session, setSession] = useState<Session | null>(readStored)
  const [expired, setExpired] = useState(false)

  const end = useCallback(
    (reason?: 'expired') => {
      writeStored(null)
      setSession(null)
      setExpired(reason === 'expired')
      qc.clear()
    },
    [qc],
  )

  // Dipasang sinkron saat render, supaya request pertama sudah membawa token.
  configureClient({
    getToken: () => readStored()?.token ?? null,
    onUnauthorized: () => end('expired'),
  })

  const start = useCallback(
    async (auth: AuthResponse) => {
      const next: Session = {
        token: auth.accessToken,
        user: { userId: auth.userId, email: auth.email, displayName: auth.displayName },
      }
      writeStored(next)
      setSession(next)
      setExpired(false)
      // Profil baru lahir saat /me pertama kali dipanggil. Tanpa ini pengguna
      // tidak bisa ditemukan orang lain untuk dibagikan catatan (§1.8 poin 2).
      try {
        const profile = await api.getMe()
        qc.setQueryData(['me'], profile)
      } catch {
        // tidak fatal; halaman profil akan mencoba lagi
      }
    },
    [qc],
  )

  // Nama di token tidak ikut berubah saat profil diubah (§1.8 poin 6)
  const setDisplayName = useCallback((displayName: string) => {
    setSession((current) => {
      if (!current) return current
      const next = { ...current, user: { ...current.user, displayName } }
      writeStored(next)
      return next
    })
  }, [])

  const value = useMemo(
    () => ({ session, start, end, expired, setDisplayName }),
    [session, start, end, expired, setDisplayName],
  )

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession() {
  const value = useContext(SessionContext)
  if (!value) throw new Error('useSession harus dipakai di dalam SessionProvider')
  return value
}
