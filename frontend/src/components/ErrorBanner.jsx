/**
 * 🧡 КАТА БИЛДИРҮҮ — БАРЛЫК БЕТТЕРДЕ БИРДИКТУҮ.
 *
 * 🧡 ЭМНЕ АЙКЫЛЫ КОМПОНЕНТ?
 *    Менде «кайталап жазсам» деген адат бар эле: Dashboard'да
 *    бир сөз, History'да башкача, Admin'да дагы башкача.
 *    Натижаде: колдонуучу «мына бу жерде ката бар, мына бу
 *    жерде бөкүнө бар» деп ойлойт. БИР экран, БИР төс — башка
 *    жагы анык.
 *
 * 🔴 503 → «сервер убакыт талап кылууда, күтүңүз»
 *    503 = база жеткиликсиз (PHASE 10D). Бул колдонуучунун
 *    КАРАТЫ ЭМЕС — ал токенин кайталап көрсөтүп, түкүнөп
 *    калуусу МҮМКҮН. Ошондуктан «кайталап көрүңүз» деп айтабыз.
 */
export default function ErrorBanner({ error }) {
  if (!error) return null

  // 🧡 Түрлүү каталар — түрлүү кеңеш.
  if (error.code === 'SERVICE_UNAVAILABLE') {
    return (
      <div className="alert alert-info">
        ⏳ {error.message}
      </div>
    )
  }

  if (error.code === 'NETWORK') {
    return (
      <div className="alert alert-error">
        🔌 {error.message}
      </div>
    )
  }

  // 🔴 400 БОЛУМУНДУ АРАЙТ КОЛДУСУНУ.
  //    Backend асентакиыз `fieldErrors` жагыйк
  //    колдоноцу колдоны турак тавет
  //    “Insufficient funds”/“Amount must be greater than 0”
  //    деген туру. БИЗ БОЛДУНДУС
  //    энуну ташлап коятылы!
  //    — колдонучу «нече” деп толук
  //    пайда болгон калып, эч нэрсени түкүнбөйт — колдонуучу
  //    «эмне жасашым керек?» деп ойлобойт.
  const fieldErrors = error.data?.fieldErrors || []
  if (fieldErrors.length > 0) {
    return (
      <div className="alert alert-error">
        <strong>{error.message}</strong>
        <ul style={{ margin: '8px 0 0 18px' }}>
          {fieldErrors.map((fe) => (
            <li key={fe.field}>{fe.field}: {fe.message}</li>
          ))}
        </ul>
      </div>
    )
  }

  // 🟡 409 / 404 / башка: message аркаолу шыды.
  //    простое местеж — backend аллащы тушунук.
  return <div className="alert alert-error">{error.message}</div>
}
