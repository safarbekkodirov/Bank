import { useEffect, useState } from 'react'
import { AdminApi, userStore } from '../api/client.js'
import ErrorBanner from '../components/ErrorBanner.jsx'

export default function AdminPage() {
  const [users, setUsers] = useState(null)
  const [accounts, setAccounts] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = () => {
    setLoading(true)
    setError(null)
    Promise.all([AdminApi.users(), AdminApi.accounts()])
      .then(([u, a]) => { setUsers(u); setAccounts(a) })
      .catch((ex) => setError(ex.status === 403
        ? Object.assign(new Error('⛔ Бул баракка уруксат жок (ROLE_ADMIN керек)'), { code: 'FORBIDDEN' })
        : ex))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  if (userStore.get()?.role !== 'ADMIN') {
    return <div className="box center"><h2>⛔ Уруксат жок</h2>
      <p className="muted">Бул баракка ROLE_ADMIN керек.</p></div>
  }

  return (
    <>
      <ErrorBanner error={error} />
      {loading && <div className="spinner">Жүктөлүүдө…</div>}

      {users && (
        <div className="box mb">
          <h2>Колдонуучулар ({users.content.length})</h2>
          <table>
            <thead>
              <tr><th>ID</th><th>Аты</th><th>Email</th><th>Роль</th></tr>
            </thead>
            <tbody>
              {users.content.map((u) => (
                <tr key={u.id}>
                  <td className="mono">{u.id}</td>
                  <td>{u.fullName}</td>
                  <td className="small">{u.email}</td>
                  <td><span className="type-badge type-TRANSFER_OUT">{u.role}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {accounts && (
        <div className="box">
          <h2>Эсептер ({accounts.content.length})</h2>
          <table>
            <thead>
              <tr><th>№</th><th>Эсеп</th><th>Валюта</th><th>Баланс</th><th>Ээси</th></tr>
            </thead>
            <tbody>
              {accounts.content.map((a) => (
                <tr key={a.id}>
                  <td className="mono">{a.id}</td>
                  <td className="mono">{a.accountNumber}</td>
                  <td>{a.currency}</td>
                  <td className="mono">{a.balance}</td>
                  <td className="small">{a.user?.email || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
