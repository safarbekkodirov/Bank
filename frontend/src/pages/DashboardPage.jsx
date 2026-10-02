import { useEffect, useState } from 'react'
import { AccountApi } from '../api/client.js'
import ErrorBanner from '../components/ErrorBanner.jsx'

export default function DashboardPage() {
  const [account, setAccount] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState('')
  const [tab, setTab] = useState('deposit')

  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [toAccountNumber, setToAccountNumber] = useState('')
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  /**
   * 🧡 ЭСЕП НОМУРУН КӨЧҮРҮҮ
   *
   * navigator.clipwright — «коопсуз контекст» деп аталат:
   * HTTPS эмес (http://localhost — иштейт, бирок IP менен
   * кирсе иштемейт) болсо, браузер аны БЛОККО сатат.
   * Ошондуктан биз аваптык жол да бар — эгер башка бир
   * браузерде иштебей калса, колдонуучу «көчүрүү жок» деп
   * карап калбасун.
   */
  const copyNumber = async (text) => {
    try {
      await navigator.clipboard.writeText(text)
    } catch {
      const el = document.createElement('textarea')
      el.value = text
      document.body.appendChild(el)
      el.select()
      try { document.execCommand('copy') } catch { /* эч нэрсе эмес */ }
      document.body.removeChild(el)
    }
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const load = () => {
    setLoading(true)
    setError(null)
    AccountApi.mine()
      .then(setAccount)
      .catch((ex) => {
        // 🧡 404 = эсеп жок (катталуунун 2-кадамы) — БУ КАТА ЭМЕС,
        //    бул «жашыл абал»: колдонуучуга эсеп ачууну көрсөтөбүз.
        if (ex.status === 404) setAccount(null)
        else setError(ex)
      })
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const submit = async (e) => {
    e.preventDefault()
    setError(null); setNotice(''); setBusy(true)
    try {
      if (tab === 'deposit') {
        const r = await AccountApi.deposit(amount, description)
        setNotice(`✅ ${r.amount} ${r.currency} салынды. Жаңы баланс: ${r.balance}`)
      } else if (tab === 'withdraw') {
        const r = await AccountApi.withdraw(amount, description)
        setNotice(`✅ ${r.amount} ${r.currency} чыгарылды. Жаңы баланс: ${r.balance}`)
      } else {
        const r = await AccountApi.transfer(toAccountNumber, amount, description)
        setNotice(`✅ ${r.amount} ${r.currency} которулду.\n    Үйлүш: ${r.fromBalance} → ${r.toBalance}`)
      }
      setAmount(''); setDescription(''); setToAccountNumber('')
      load()
    } catch (ex) {
      // 🔴 503 = база жеткиликсиз (PHASE 10D) — «сен натыйжасыз»
      //    ЭМЕС, «сервер убакыт талап кылууда». ErrorBanner аны
      //    «күтүңүз» түсү менен, желги катаны «🔌» менен көрсөтөт.
      setError(ex)
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <div className="spinner">Жүктөлүүдө…</div>

  if (!account) {
    return (
      <div className="box center">
        <h2>Сизде банк эсеби жок</h2>
        <p className="muted mb">Акча салуу үчүн эсеп ачыңыз.</p>
        <button className="btn-primary" style={{ maxWidth: 260, margin: '0 auto' }}
                onClick={() => AccountApi.create('RUB').then(load)}>
          Эсеп ачуу (RUB)
        </button>
      </div>
    )
  }

  return (
    <>
      <div className="balance-card">
        <div className="balance-label">Баланс</div>
        <div className="balance-amount">{account.balance} {account.currency}</div>

        {/* 🔴 ЭСЕП НОМУРУ — КӨЧҮРҮҮЧҮ.
            БУ НЕГЕ МЫКТЫҢ:
            «Которуу» үчүн КАЙДАЙ ЭСЕПКЕ жөнөтүү керек? Биз эсеп
            номурун эч кайда табабыз — эгер көрүп турбаса,
            «которуу» функциясы колдонулбас эле. Ошондуктан
            эсебибиз номурун КӨРСӨТҮП, көчүрүүгө мүмкүнчүлүк
            беребиз: эки колдонуучу бири-бирине сыноо үчүн
            ушул номурду колдоно алышат. */}
        <div className="balance-meta">
          Эсеп номуру:{' '}
          <span className="mono">{account.accountNumber}</span>
          <button
            className="btn-secondary btn-sm"
            style={{ marginLeft: 10 }}
            onClick={() => copyNumber(account.accountNumber)}
          >
            {copied ? '✓ Көшүрүлдү' : 'Көшүрүү'}
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-success">{notice}</div>}

      <div className="box">
        <div className="tabs">
          <button className={tab === 'deposit'  ? 'active' : ''} onClick={() => setTab('deposit')}>💰 Салыруу</button>
          <button className={tab === 'withdraw' ? 'active' : ''} onClick={() => setTab('withdraw')}>💸 Чыгаруу</button>
          <button className={tab === 'transfer'  ? 'active' : ''} onClick={() => setTab('transfer')}>🔄 Которуу</button>
        </div>

        <form onSubmit={submit}>
          {tab === 'transfer' && (
            <>
              <label>Кайсы эсепке (18 цифра)</label>
              <input required minLength={18} maxLength={18} pattern="[0-9]{18}"
                     value={toAccountNumber}
                     onChange={(e) => setToAccountNumber(e.target.value)}
                     placeholder="408178100000000001" className="mono" />
            </>
          )}

          <label>Сумма ({account.currency})</label>
          {/* 🧡 inputMode="decimal" — телефондо цифралык клавиатура */}
          <input type="number" step="0.0001" min="0.0001" required
                 value={amount}
                 onChange={(e) => setAmount(e.target.value)}
                 placeholder="1000.00" />

          <label>Сүрөттөмө (миаsal, катуу)</label>
          <input value={description} maxLength={500}
                 onChange={(e) => setDescription(e.target.value)}
                 placeholder="Айлык төлөм" />

          <button className="btn-primary" disabled={busy}>
            {busy ? 'Күтүлүдө…'
              : tab === 'deposit'  ? 'Салыруу'
              : tab === 'withdraw' ? 'Чыгаруу'
              : 'Которуу'}
          </button>
        </form>
      </div>
    </>
  )
}
