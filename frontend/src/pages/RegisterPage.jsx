import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthApi, tokenStore, userStore } from '../api/client.js'
import ErrorBanner from '../components/ErrorBanner.jsx'

export default function RegisterPage({ onLogin }) {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await AuthApi.register({ fullName, email, password })
      // 🔴 Катталуу анык 201 берет, бирок ТОКЕН БЕРБЕЙТ.
      //    Ошондуктан кийин дароо логин жасабыз.
      const res = await AuthApi.login({ email, password })
      tokenStore.set(res.accessToken)
      userStore.set(res.user)
      onLogin(res.user)
      navigate('/')
    } catch (ex) {
      setError(ex)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="center-screen">
      <div className="card">
        <h1>Катталуу</h1>
        <p className="subtitle">Жаңы эсеп түзүңүз</p>

        <ErrorBanner error={error} />

        <form onSubmit={submit}>
          <label>Аты-жөнү</label>
          <input value={fullName} required minLength={2} maxLength={150}
                 onChange={(e) => setFullName(e.target.value)}
                 placeholder="Алиев Али" />

          <label>Email</label>
          <input type="email" value={email} required
                 onChange={(e) => setEmail(e.target.value)}
                 placeholder="ali@example.com" autoComplete="email" />

          <label>Пароль</label>
          <input type="password" value={password} required minLength={8} maxLength={72}
                 onChange={(e) => setPassword(e.target.value)}
                 placeholder="8+ белги" autoComplete="new-password" />

          <button className="btn-primary" disabled={busy}>
            {busy ? 'Күтүлүдө…' : 'Катталуу'}
          </button>
        </form>

        <p className="small muted center" style={{ marginTop: 16 }}>
          Эсебиңиз барбы? <Link to="/login" style={{ color: 'var(--accent)' }}>Кириү</Link>
        </p>
      </div>
    </div>
  )
}
