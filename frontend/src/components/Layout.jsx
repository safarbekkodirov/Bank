import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { tokenStore } from '../api/client.js'

export default function Layout({ user, onLogout }) {
  const navigate = useNavigate()

  const logout = () => {
    tokenStore.clear()
    onLogout()
    navigate('/login')
  }

  return (
    <>
      <header className="topbar">
        <h1>🏦 Mini Bank</h1>
        <nav className="nav">
          <NavLink to="/"         className={({ isActive }) => isActive ? 'active' : ''}>Эсеп</NavLink>
          <NavLink to="/history"  className={({ isActive }) => isActive ? 'active' : ''}>Тарых</NavLink>
          {user.role === 'ADMIN' && (
            <NavLink to="/admin" className={({ isActive }) => isActive ? 'active' : ''}>Админ</NavLink>
          )}
        </nav>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span className="topbar-user">
            {user.fullName} · <span className="mono">{user.role}</span>
          </span>
          <button className="btn-secondary btn-sm" onClick={logout}>Чыгуу</button>
        </div>
      </header>
      <div className="container">
        <Outlet />
      </div>
    </>
  )
}
