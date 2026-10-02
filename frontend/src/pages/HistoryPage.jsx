import { useEffect, useState } from 'react'
import { AccountApi } from '../api/client.js'
import ErrorBanner from '../components/ErrorBanner.jsx'

const TYPES = ['', 'DEPOSIT', 'WITHDRAW', 'TRANSFER_IN', 'TRANSFER_OUT']

/** 🧡 Кирек — «+» жана жашыл түс. Чыгаруу — «−» жана кызыл. */
const INCOMING = new Set(['DEPOSIT', 'TRANSFER_IN'])

export default function HistoryPage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [page, setPage] = useState(0)

  const [filter, setFilter] = useState({ type: '', minAmount: '', maxAmount: '' })

  const load = (p = page) => {
    setLoading(true)
    setError(null)
    AccountApi.transactions({ page: p, size: 10, ...filter })
      .then((d) => { setData(d); setPage(d.page) })
      .catch((ex) => setError(ex))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load(0) }, [])

  const applyFilter = (e) => { e.preventDefault(); load(0) }

  return (
    <div className="box">
      <h2>Операциялар тарыхы</h2>

      <form onSubmit={applyFilter} style={{ marginBottom: 18 }}>
        <div className="grid grid-3">
          <div>
            <label>Түрү</label>
            <select value={filter.type} onChange={(e) => setFilter({ ...filter, type: e.target.value })}>
              {TYPES.map((t) => (
                <option key={t} value={t}>{t || 'Бары'}</option>
              ))}
            </select>
          </div>
          <div>
            <label>Эң аз сумма</label>
            <input type="number" step="0.0001" value={filter.minAmount}
                   onChange={(e) => setFilter({ ...filter, minAmount: e.target.value })}
                   placeholder="0" />
          </div>
          <div>
            <label>Эң көп сумма</label>
            <input type="number" step="0.0001" value={filter.maxAmount}
                   onChange={(e) => setFilter({ ...filter, maxAmount: e.target.value })}
                   placeholder="100000" />
          </div>
        </div>
        <button className="btn-secondary btn-sm">Фильтрлеү</button>
      </form>

      <ErrorBanner error={error} />
      {loading && <div className="spinner">Жүктөлүүдө…</div>}

      {data && !loading && (
        data.content.length === 0 ? (
          <p className="muted center" style={{ padding: 24 }}>Операция жок</p>
        ) : (
          <>
            <table>
              <thead>
                <tr>
                  <th>Убакты</th>
                  <th>Түрү</th>
                  <th>Сумма</th>
                  <th>Баланс</th>
                  <th>Сүрөттөмө</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((t) => (
                  <tr key={t.id}>
                    <td className="small muted">
                      {new Date(t.createdAt).toLocaleString('ky-KG')}
                    </td>
                    <td><span className={`type-badge type-${t.type}`}>{t.type}</span></td>
                    {/* 🔴 БАГЫР — «type»ден, «amount»тен ЭМЕС!
                        Backend сумманы АБСОЛЮТ сактат: чыгаруу
                        үчүн да «200.0» (тегин белгисиз). Багырды
                        ТҮР түкүмү аныктайт:
                          DEPOSIT, TRANSFER_IN   → кире (+)
                          WITHDRAW, TRANSFER_OUT → чыгаруу (−)
                        Эгер «amount > 0 ? '+' : ''» десек,
                        WITHDRAW «+» болуп көрүнөт — БУ КАТА,
                        колдонуучу «мен салдым» деп ойлойт. */}
                    <td className={INCOMING.has(t.type) ? 'amount-pos' : 'amount-neg'}>
                      {INCOMING.has(t.type) ? '+' : '−'}{Math.abs(Number(t.amount))}
                    </td>
                    <td className="mono">{t.balanceAfter}</td>
                    <td className="small muted">{t.description || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* 🧡 «Бош бет» = ката ЭМЕС. Бул жакшы абал:
                backend content:[] жана totalPages:0 кайтарат. */}
            <div className="pager">
              <button className="btn-secondary btn-sm"
                      disabled={data.first}
                      onClick={() => load(page - 1)}>← Алдыңкы</button>
              <span className="pager-info">
                {data.page + 1} / {Math.max(data.totalPages, 1)} бет
                (жалпы {data.totalElements})
              </span>
              <button className="btn-secondary btn-sm"
                      disabled={data.last}
                      onClick={() => load(page + 1)}>Кийинки →</button>
            </div>
          </>
        )
      )}
    </div>
  )
}
