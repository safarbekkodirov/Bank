// ==========================================================================
//  API КЛИЕНТ — бардык суроолор БУ ЖЕРДЕН ӨТӨТ
// ==========================================================================

const TOKEN_KEY = 'minibank_token'
const USER_KEY = 'minibank_user'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }
}

export const userStore = {
  get: () => {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  },
  set: (u) => localStorage.setItem(USER_KEY, JSON.stringify(u))
}

/**
 * 🧡 401 → автоматты чыгуу
 *
 *    Токен 15 мүн ичинде өчөт. Ошондо backend 401 берет.
 *    Эгерде биз аны «каттала элек» деп көрсөтсөк, колдонуучу
 *    түкүнөп турат. Ошондуктан: 401 келсе → токен таштоо.
 */
function handleUnauthorized() {
  tokenStore.clear()
  // Браузердин башка жагында калган абалды тазалоо үчүн:
  window.dispatchEvent(new Event('minibank:unauthorized'))
}

/**
 * @param {string} path  — мисалы '/api/users/me'
 * @param {object} opts  — { method, body, auth }
 */
export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' }

  if (auth) {
    const token = tokenStore.get()
    if (!token) throw new Error('NO_TOKEN')
    headers['Authorization'] = `Bearer ${token}`
  }

  // 🔴 fetch ӨЗҮНЕ ҚАЛТЫРМАЙДЫ.
  //    Сервер жабык болсо, HTTP коду ЖОК — браузер `TypeError`
  //    сатат («Failed to fetch»). Экен экен текшербесек,
  //    колдонуучу англисче «Failed to fetch» көрүп, ойлойт:
  //    «менем браузерди бузуп алдымбы?» Биз аны кармап,
  //    кыргызча түшүнүктүү билдирүүгө айлантырабыз.
  let res
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined
    })
  } catch {
    const err = new Error(
      'Серверге жетүү мүмкүн болгон жок. Backend иштеп жатырма? ' +
      'Терминалда `./mvnw spring-boot:run` иштетиңиз.')
    err.code = 'NETWORK'
    throw err
  }

  if (res.status === 401 && auth) {
    handleUnauthorized()
    throw new Error('UNAUTHORIZED')
  }

  // 🔴 503 = база жеткиликсиз (PHASE 10D). Бул 401 ЭМЕС —
  //    токен догру, бирок серверге убакыт керек.
  if (res.status === 503) {
    const err = new Error(
      '⏳ Серверге убакыт кетти (база жеткиликсиз). ' +
      'Бир нече секунддан кийин кайталап көрүңүз.')
    err.code = 'SERVICE_UNAVAILABLE'
    throw err
  }

  // 🟡 Жапобу JSON ЭМЕС болсо (мисалы nginx 502 html берет)
  //    — JSON.parse укмайт. Биз аны жумшак кармайбыз.
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }

  if (!res.ok) {
    const message = data?.message || `Сервер ${res.status} катасын кайтарды.`
    const err = new Error(message)
    err.status = res.status
    err.data = data
    throw err
  }

  return data
}

// ==========================================================================
//  БАРДЫК ЧЫГАРУУЛАР — аттары backend менен БИРДЕЙ
// ==========================================================================

export const AuthApi = {
  register: (payload) =>
    api('/api/auth/register', { method: 'POST', body: payload, auth: false }),
  login: (payload) =>
    api('/api/auth/login', { method: 'POST', body: payload, auth: false })
}

export const UserApi = {
  me: () => api('/api/users/me')
}

export const AccountApi = {
  create: (currency) => api('/api/accounts', { method: 'POST', body: { currency } }),
  mine: () => api('/api/accounts/me'),
  deposit: (amount, description) =>
    api('/api/accounts/me/deposit', { method: 'POST', body: { amount, description } }),
  withdraw: (amount, description) =>
    api('/api/accounts/me/withdraw', { method: 'POST', body: { amount, description } }),
  transfer: (toAccountNumber, amount, description) =>
    api('/api/accounts/me/transfer',
        { method: 'POST', body: { toAccountNumber, amount, description } }),
  transactions: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) q.append(k, v)
    })
    const query = q.toString()
    return api(`/api/accounts/me/transactions${query ? '?' + query : ''}`)
  }
}

export const AdminApi = {
  users: () => api('/api/admin/users'),
  accounts: () => api('/api/admin/accounts')
}
