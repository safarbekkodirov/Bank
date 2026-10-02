import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthApi, tokenStore, userStore } from '../api/client.js'

export default function LoginPage({ onLogin }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const res = await AuthApi.login({ email, password })
      tokenStore.set(res.accessToken)
      userStore.set(res.user)
      onLogin(res.user)
      navigate('/')
    } catch (ex) {
      setError(ex.message || 'Ката кетти')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="center-screen">
      <div className="card">
        <h1>🏦 Mini Bank</h1>
        <p className="subtitle">Системага кириңиз</p>

        {error && <div className="alert alert-error">{error}</div>}

        <form onSubmit={submit}>
          <label>Email</label>
          <input type="email" value={email} required
                 onChange={(e) => setEmail(e.target.value)}
                 placeholder="ali@example.com" autoComplete="email" />

          <label>Пароль</label>
          <input type="password" value={password} required
                 onChange={(e) => setPassword(e.target.value)}
                 placeholder="••••••••" autoComplete="current-password" />

          <button className="btn-primary" disabled={busy}>
            {busy ? 'Күтүлүдө…' : 'Кириү'}
          </button>
        </form>

        <p className="small muted center" style={{ marginTop: 16 }}>
          Эсебиңиз жокпу? <Link to="/register" style={{ color: 'var(--accent)' }}>Катталуу</Link>
        </p>
      </div>
    </div>
  )
}
