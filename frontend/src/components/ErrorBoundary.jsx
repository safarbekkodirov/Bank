import { Component } from 'react'

/**
 * 🔴 REACT ERROR BOUNDARY
 *
 * 🧡 БУ НЕГЕ КЕРЕК?
 *
 *    React'те компонент иштеп жатканда КАТА САЛСА, эмне
 *    болот? Бүт экран АК болуп кала берет — көк текст,
 *    эчтеңе жок. Колдонуучу браузерди ыйлап, «тилимди
 *    жаңыртсам болобу?» деп ойлойт.
 *
 *    Биз бул жерде ТОКТОТПАЙЫЗ, КӨРСӘТӨМӨЗ:
 *        Экранды толук ыйлап, эмес — «Бир нерсе туура
 *        кетти» деп, «Үйгө кайтуу» баскычын беремиз.
 *
 * 🧡 «ҮЙГӨ КАЙТУУ» БЫЗГЕ ЭМНЕ КЫЛАТ?
 *    Так кылып: window.location='/' — барардын адресин
 *    өзгөртөт. Колдонуучуну токени тазалоо үчүн «Чыгуу» баскычын
 *    баса алат (эгер ката жашырун турун алса).
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    // 🔴 Биз аны БРАУЗЕР КОНСОЛУНА салбайбыз. Себеби:
    //    консолго чыккан нерсе оңдөлүнбөйт, ал жөн гана
    //    жашынып тура берет. Чыныгы чечүү — колдонуучуга
    //    түшүнүктүү билдирүү көрсөтүү.
    console.warn('UI катасы:', error?.message, info?.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children

    return (
      <div className="center-screen">
        <div className="card center">
          <h1>Бир нерсе туура кетти</h1>
          <p className="subtitle">
            Беттү көрсөтүүдө ката чыкты. Бул маалыматты сактап,
            аны көрсөтүп берүүбүз — катанын текстин кичигине чейин.
          </p>
          <div className="alert alert-error small mono" style={{ textAlign: 'left' }}>
            {String(this.state.error?.message || this.state.error)}
          </div>
          <button className="btn-primary"
                  onClick={() => { window.location.href = '/' }}>
            Үйгө кайтуу
          </button>
        </div>
      </div>
    )
  }
}
