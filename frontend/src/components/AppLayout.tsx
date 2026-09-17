import { NavLink, Outlet, useNavigate } from 'react-router'
import { useSession } from '../lib/session'
import { Avatar, cx } from './ui'

const NAV = [
  { to: '/notes', label: 'Catatan saya', end: true },
  { to: '/shared', label: 'Dibagikan ke saya', end: false },
]

export function Logo() {
  return (
    <span className="inline-flex items-center gap-2.5">
      <img src="/favicon.svg" alt="" className="size-8" />
      <span className="text-lg font-semibold tracking-tight">notenest</span>
    </span>
  )
}

export function AppLayout() {
  const { session, end } = useSession()
  const navigate = useNavigate()
  const user = session!.user

  return (
    <div className="min-h-dvh pb-20 sm:pb-0">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-card focus:px-3 focus:py-2">
        Lewati ke konten
      </a>
      <header className="sticky top-0 z-30 border-b border-border bg-card/70 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <NavLink to="/notes" aria-label="NoteNest, ke catatan saya">
            <Logo />
          </NavLink>
          <nav className="hidden gap-1 sm:flex" aria-label="Utama">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cx('rounded-lg px-3 py-2 text-sm font-medium', isActive ? 'bg-secondary text-foreground' : 'text-muted-foreground hover:text-foreground')
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <NavLink to="/profile" className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-secondary" aria-label="Profil">
              <Avatar name={user.displayName} seed={user.userId} size={30} />
              <span className="hidden text-sm font-medium md:inline">{user.displayName}</span>
            </NavLink>
            <button
              type="button"
              onClick={() => {
                end()
                navigate('/login')
              }}
              className="rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-secondary hover:text-foreground"
            >
              Keluar
            </button>
          </div>
        </div>
      </header>

      <main id="main" className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
        <Outlet />
      </main>

      {/* Layar sempit: navigasi bawah */}
      <nav className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-3 border-t border-border bg-card/95 backdrop-blur sm:hidden" aria-label="Utama">
        {[...NAV, { to: '/profile', label: 'Profil', end: true }].map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => cx('py-3.5 text-center text-[13px] font-medium', isActive ? 'text-primary' : 'text-muted-foreground')}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
