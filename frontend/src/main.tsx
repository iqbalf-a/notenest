import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { API_BASE_URL } from './api/client'

// VITE_API_BASE_URL kosong = dummy data. MSW mencegat /api/* di service worker,
// jadi komponen tidak tahu datanya palsu (docs/FRONTEND-BRIEF.html §1.7).
async function enableMocks() {
  if (API_BASE_URL) return
  const { worker } = await import('./mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true })
}

enableMocks().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
