import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router'
import { ApiError } from './api/client'
import { AppLayout } from './components/AppLayout'
import { StateCard } from './components/ui'
import { SessionProvider, useSession } from './lib/session'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { NoteDetailPage } from './pages/NoteDetailPage'
import { EditNotePage, NewNotePage } from './pages/NoteEditorPage'
import { NotesPage } from './pages/NotesPage'
import { ProfilePage } from './pages/ProfilePage'
import { SharedPage } from './pages/SharedPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      // 4xx tidak akan berubah kalau diulang
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
})

function RequireAuth() {
  const { session } = useSession()
  const location = useLocation()
  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}

function GuestOnly() {
  const { session } = useSession()
  return session ? <Navigate to="/notes" replace /> : <Outlet />
}

function NotFound() {
  return (
    <div className="grid min-h-dvh place-items-center px-4">
      <StateCard icon="?" title="Halaman tidak ditemukan" action={<Link to="/notes" className="font-semibold">Ke catatan saya</Link>} />
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<GuestOnly />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
            </Route>
            <Route element={<RequireAuth />}>
              <Route element={<AppLayout />}>
                <Route path="/notes" element={<NotesPage />} />
                <Route path="/notes/new" element={<NewNotePage />} />
                <Route path="/notes/:id" element={<NoteDetailPage />} />
                <Route path="/notes/:id/edit" element={<EditNotePage />} />
                <Route path="/shared" element={<SharedPage />} />
                <Route path="/profile" element={<ProfilePage />} />
              </Route>
            </Route>
            <Route path="/" element={<Navigate to="/notes" replace />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </SessionProvider>
    </QueryClientProvider>
  )
}
