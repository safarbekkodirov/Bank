import { useEffect, useState } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { tokenStore, userStore, UserApi } from './api/client.js'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import HistoryPage from './pages/HistoryPage.jsx'
import AdminPage from './pages/AdminPage.jsx'
import Layout from './components/Layout.jsx'

/**
 * 🧡 ТОКЕН ӨТКӨН БОЛСО → ЛОГИН БЕТКЕ КАЙТЫМЫ?
 *
 *    «/me» сурагын жабо жок экенин текшеремиз. Себеби:
 *    токен localStorage'те калган болот, бирок 15 мүн ичинде
 *    өчөт. Эгерде тек localStorage'ке ишенип коюлса,
 *    колдонуучу «иштеп жаткан» баракты көрүп, бирок ар канчай
 *    сурагында 401 алып, «эмне үчүн?» деп ойлойт.
 */
export default function App() {
  const [user, setUser] = useState(userStore.get())
  const [checking, setChecking] = useState(!!tokenStore.get())

  useEffect(() => {
    // 🔴 401 → колдонуучуну автоматты чыгарабыз.
    const onUnauthorized = () => { setUser(null); setChecking(false) }
    window.addEventListener('minibank:unauthorized', onUnauthorized)
    return () => window.removeEventListener('minibank:unauthorized', onUnauthorized)
  }, [])

  useEffect(() => {
    if (!tokenStore.get()) { setChecking(false); return }

    // 🔴 Же 401 келсе — токен таштайбыз, колдонуучуну чыгарабыз.
    //    «/me» сурагы бизге анын токени ЖАШЫ экенин айтат.
    UserApi.me()
      .then((u) => { userStore.set(u); setUser(u) })
      .catch(() => { tokenStore.clear(); setUser(null) })
      .finally(() => setChecking(false))
  }, [])

  if (checking) {
    return <div className="spinner">Тектерилүүдө…</div>
  }

  return (
    <Routes>
      <Route path="/login"    element={user ? <Navigate to="/" /> : <LoginPage onLogin={setUser} />} />
      <Route path="/register" element={user ? <Navigate to="/" /> : <RegisterPage onLogin={setUser} />} />
      <Route element={user ? <Layout user={user} onLogout={() => { tokenStore.clear(); setUser(null) }} /> : <Navigate to="/login" />}>
        <Route path="/"          element={<DashboardPage />} />
        <Route path="/history"   element={<HistoryPage />} />
        <Route path="/admin"     element={<AdminPage />} />
      </Route>
    </Routes>
  )
}
