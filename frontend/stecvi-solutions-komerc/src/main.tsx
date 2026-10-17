import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './style.css'
import App from './app/app'
import { AuthProvider } from './features/auth/auth_provider'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>,
)
