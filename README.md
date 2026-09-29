# Mini Bank

> ⚠️ **Бул окуу долбоору.** Чыныгы банк эмес, чыныгы акча иштебейт, портфолио үчүн жасалган.

## Максат

Java Backend, Spring Boot, PostgreSQL, REST API, Authentication жана банк
бизнес-логикасын үйрөнүү үчүн жасалган кичине долбоор.

## Технологиялар

| Технология | Версия |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Maven | 3.9.16 (Maven Wrapper аркылуу) |
| Spring Web | (Spring Boot менен биге келед) |
| PostgreSQL | 16 (Docker аркылуу) |
| Flyway | (Spring Boot менен биге келед) |
| JUnit 5 | (Spring Boot Test менен биге келед) |

## Учурдагы абал: **PHASE 9 — Integration Hardening & Reliability**

Эми иштеп ятат: эсеп ачабыз, акча салýarys, çekýäris, başga esäbe geçirýäris we
öz operasiýalarymyzyň **taryhyny sahypa sahypa** görýäris. Ähli amallar
tokenden yzarlanýar — klient öz eýesini başgasynyň taryhyna geçirip bilmeýär.

Иşleýän funksiýalar: Registration, JWT login, Bank Account, Deposit, Withdraw,
Transfer (iki esap arasynda), Transaction History (pagination + filter), we
**Admin API** (ROLE_ADMIN — diňe okaýan administrator paneli).

Азыр ýok: Frontend.

🔴 **PHASE 8 read-only**: admin üçin `GET` endpointler bar
(`/api/admin/**`). Emma admin **akça hereketlendiremez**, **role
üýtgedip bilmez**, **kimse ýok edip bilmez** — bu ýatdaşdyr
(27/28-njy bendler).

---

# PHASE 4 — Bank Account Domain

## 3. Архитектура: Account кандай User менен байланышканы

```
┌──────────────┐         ┌────────────────────┐
│    users     │ 1     * │     accounts       │
├──────────────┤◄───────│────────────────────│
│ id (PK)      │         │ id (PK)            │
│ email        │         │ user_id (FK, NOT   │
│ password_hash│         │        NULL)        │
│ role         │         │ account_number     │
└──────────────┘         │  (UNIQUE)          │
                         │ currency (RUB)     │
                         │ balance NUMERIC    │
                         │  (19,4) >= 0       │
                         │ created/updated_at │
                         └─────────┬──────────┘
                                   │ 1
                                   │ *
                         ┌─────────▼──────────┐
                         │   transactions     │  (Phase 5)
                         └────────────────────┘
```

**Байланыш: `@ManyToOne`** — эсептин бир ээси бар, ээсинин көп эсеби болушу мүмкүн.
(Азыр бизнес-эреже «бир эсеп» деп чектеди — маалымат базасында `UNIQUE(user_id)`.)

## 🧠 `@ManyToOne(fetch = LAZY)` эмне үчүн?

Алтын эсеп тизимдегенде, анын колдонуучусу **керилбейт**. `LAZY` = «чыныгы керек
болгондо гана базадан суроо». `EAGER` болгонда — 100 эсеп = 100 колдонуучу суроосу
(N+1 маселеси, кийинки этапта).

## 🛡️ «КОЗГӨ ЧЫГАРБАЙТЫЗ» — эндпоинт дизайны

Биз атайын **мындай эндпоинттер ЖАЗБАЙТ**:
```
GET /api/accounts/{id}        ❌
GET /api/accounts?userId=53   ❌
GET /api/accounts/me          ✅ МЫНА БУ
```

**Себеби (IDOR — Insecure Direct Object Reference):** эгер `{id}` болсо, колдонуучу
`1, 2, 3...` деп маалдап, **башкалардын эсебин** көрө алар эле. Бизге кыз: чын
эсептер ЖОК болсо, эле «сен 1-айлампага кирдиң, бул 2-адамдын акчасы» деп
чыгарыбыз. «/me» = «сен өзүңдүн гана эсебиңди көрөсүң». ID сүрөттөн **эмес**,
токенден келинет (тест 9 буруну текшерет).

## 4. Database: эмне өзгөртүлдү

| Миграция | Эмне кылды | Себеби |
|---|---|---|
| **V2** | `UNIQUE (user_id)` + `DROP INDEX ix_accounts_user_id` | «Бир колдонуучуга бир эсеп» + параллелдүүлүк |
| **V3** | `CREATE SEQUENCE account_number_seq` | Эсеп номурун коопсуз түзүү |
| **V4** | `currency: CHAR(3) → VARCHAR(3)` | Hibernate validate келишсиздиги |

### 🛡️ V2 — Эмне үчүн UNIQUE(user_id) Java текшерүүсүнөн КАЧАТ?

Java'да `existsByUserId()` бар, бирок ал **эртегин көккүлдөнүн тоңбайт**:
```
Суроо #1: "эсеп барбы?" -> ЖОК
         ↓ (экөө бир убакта келди)
Суроо #2: "эсеп барбы?" -> ЖОК   ← экөө тең!
         ↓
Экөөнү INSERT -> ЭКИ ЭСЕП ТҮЗҮЛДӨ  💥
```
Бул **TOCTOU** («текшердиң, андан колдондуң») темиги. Демек эрежени
**маалымат базасында** түзөм — ал эч качан бузулбайт (эки сервер, эки адам).

🧡 **Индекс көккүлдөнү:** мурунку `ix_accounts_user_id` (карапайым) **алып
ташталды**. Себеби: UNIQUE индекс **ичинен да** издөө үчүн иштейт — эки индекс
бир иш кылып, экинчиси бош оруун ээлеп жүрөт.

### 🔢 V3 — эсеп номуру: эсептөгүч эмес, рандом эмес

Формат: `40817810` (банк коду, 8) + эсептөгүч (10) = **18 белги**.

| Өлчөм | Натыйжа |
|---|---|
| ❌ Жөнөкөй эсептөгүч (AtomicLong) | Сервер өчсө → номур **кайталанат** |
| ❌ Рандом 18 белги | 3.5 триллион вариант, бирок кубултуу керек |
| ✅ **DB эсептөгүчү** | Сервер өчсө да жашат; 2 сервер = 1 эсептөгүч; **эч качан дубль бербейт** |

Ошондуктан бизге **retry керек эмес** — эсептөгүч эч качан кайталош болбогон.

### 🛑 V4 — Hibernate validate тапкан чыныгы ката

```
found [bpchar (CHAR)], but expecting [varchar(3) (VARCHAR)]
```

V1'де `CHAR(3)` жазган элем, Java `String` болсо Hibernate `VARCHAR` күткөн.
`ddl-auto=validate` **иштеп, колдонмону токтотту** — «Java менен SQL бири-бирин
текшерет» деген чечимимиз чыныгыдан иштеди.

**Эмне VARCHAR кылыш керек, эмес Java'ны CHAR'га өтгөнүбүз?**
1. БАШКА БААРЫ колонка VARCHAR — бир таблицада эки түрлүү катуу көрүнөт
2. PostgreSQL'де `CHAR(n)` — **боштук менен толтурат** (blank padding):
   `'RU'` → DB'де `'RU '`. Кийин «эмне үчүн эсептелди?» таңсыздыктары
3. VARCHAR кылганда Java тарапта эч нерсе өзгөрбөй

## 5. API

| Endpoint | Код | Маалымат |
|---|---|---|
| `POST /api/accounts` | **201** | Жаңы эсеп (`balance: 0.0000`, `currency: RUB`) |
| `GET /api/accounts/me` | **200** | Өз эсебим |
| | **404** | Эсеп жок |
| | **409** | Эсеп мурунтан бар |
| | **401** | Токенсиз |

Request body **талап кылынбайт** — эсептин маалыматы колдонуучуга эмес,
**токенге** жараша түзүлөт.

### PHASE 5-7 goşulan endpoinтler

| Endpoint | Код | Маалымат |
|---|---|---|
| `POST /api/accounts/me/deposit` | **200** | Акча салуу (жurnal: DEPOSIT) |
| `POST /api/accounts/me/withdraw` | **200** | Акça çekmek (jurnal: WITHDRAW) |
| | **409** | Akça ýetmeýär |
| `POST /api/accounts/me/transfer` | **200** | Başga esäbe geçirme (TRANSFER_OUT + TRANSFER_IN) |
| | **409** | Akça ýetmeýär / öz-e özüne geçirme |
| | **404** | Esap ýok |
| `GET /api/accounts/me/transactions` | **200** | Öz taryhy (pagination + filter) |
| | **400** | Nädogry parametr (`page<0`, `size>100`, `from>to`...) |
| | **404** | Esap ýok |

#### PHASE 7 — Transaction History

```http
GET /api/accounts/me/transactions?page=0&size=20&type=DEPOSIT
    &from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z
    &minAmount=100&maxAmount=1000
```

**Parametrlar** (hemmesi ýasy): `page` (0), `size` (20, iň ýokary 100),
`type`, `from`, `to`, `minAmount`, `maxAmount`.

**Jogap**: `{ content: [...], page, size, totalElements, totalPages, first, last }`
— tertip `created_at DESC, id DESC` (iň täze birinji, tertip anyk).

🔴 `/api/accounts/{id}/transactions` **ýok** — taryh diňe öz eýesine görünýär.
---

# PHASE 8 — Admin API + Role-Based Access Control

ADMIN üçin aýratyn, goragly **okaýan** (read-only) API. RBAC
Spring Security arkaly: `/api/admin/**` -> `hasRole("ADMIN")`.

## RBAC düzgüni

| Endpoint | USER | ADMIN | Token ýok |
|---|---|---|---|
| `/api/accounts/me` | 200 | 200 | 401 |
| `/api/accounts/me/transactions` | 200 | 200 | 401 |
| `/api/admin/users` | **403** | 200 | 401 |
| `/api/admin/users/{id}` | **403** | 200 | 401 |
| `/api/admin/accounts` | **403** | 200 | 401 |
| `/api/admin/accounts/{id}` | **403** | 200 | 401 |
| `/api/admin/accounts/{id}/transactions` | **403** | 200 | 401 |

🔴 **Rol bazadan alynýar, token-den däl.** `JwtAuthenticationFilter`
koldugyny bazadan okaýar — token-däki `role` claim ýetirilýär,
emma çözgüt üçin **ulanylmaýar**.

## Endpointler

| Endpoint | Kod | Maglumat |
|---|---|---|
| `GET /api/admin/users` | **200** | Ähli kollonuwçular (pagination, search) |
| `GET /api/admin/users/{id}` | **200** | Bir kollonuwçu / 404 |
| `GET /api/admin/accounts` | **200** | Ähli es epler (user maglumaty bilen) |
| `GET /api/admin/accounts/{id}` | **200** | Bir esap / 404 |
| `GET /api/admin/accounts/{id}/transactions` | **200** | Esabyň taryhy (filter bilen) |
| | **400** | `page<0`, `size>100`, nädogry `type`, `from>to` |
| | **401** | Token ýok / buzulgan |
| | **403** | USER roly bilen |

**Parametrler** (hemmesi ýasy):
- `/users`: `page` (0), `size` (20, max 100), `search` (at/email, harpuly däl)
- `/accounts`: `page`, `size`, `userId`
- `/accounts/{id}/transactions`: `page`, `size`, `type`, `from`, `to`, `minAmount`, `maxAmount`

**Sertleşme** — tertip `createdAt DESC, id DESC` (deterministik).
Klient erkin tertip bermeýär.

🔴 **Parol hasy** `AdminUserResponse`-da **hiç haçan** ýok. Testler barlaýar.

🔴 **Maglumat syzdyrmak ýok**: 403-de umumy jogap — esebiň ýa-da
kollonuwçynyň bar/ýokdygy aýrylmaýar.

🟡 **N+1 ýok**: `Account.user` LAZY, şonuň üçin `JOIN FETCH`
ulanylýar — 20 esep = 1 sorag, 21 sorag däl.




---

# PHASE 3B — Login + JWT

## Архитектура: сүрөт кантип «таанылат»

```
        Authorization: Bearer <токен>
                    │
                    ▼
┌────────────────────────────────┐
│  JwtAuthenticationFilter       │  ← СҮРӨТ Controller'га ЖЕТПЕЙ ТУРАТ
│  1. "Bearer" header барбы?     │  Жок -> өткөрөбүз (ачык жер иштесин)
│  2. Кыр тамгасы туурабы?       │  Жок -> өткөрөбүз
│  3. exp өткөбү?                │  Өткөн -> өткөрөбүз
│  4. sub -> id -> маалымат базасы│  Жок -> өткөрөбүз
│  5. Роль -> ROLE_USER          │
│  6. SecurityContextке коёт     │
└───────────────┬────────────────┘
                ▼
┌────────────────────────────────┐
│  SecurityConfig                │
│  GET /api/users/me             │  Токенсиз -> 401 JSON
│  authenticated                 │  Токен менен -> Controller
└───────────────┬────────────────┘
                ▼
┌────────────────────────────────┐
│  UserController.getCurrentUser │  SecurityUtils.getCurrentUserId()
│  → UserService → маалымат базасы│  (ID URL'ден ЭМЕС, токенден)
└────────────────────────────────┘
```

## 🧠 JWT = «кол китепче»

```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiI1MyIs....EKHYs1N0ay1QV1-aOZc0_Wv_Ng4OSQ
└──── баш ────┘ └──── борбор ─────┘ └────── кол тамга ──────┘
  алгоритм        МААЛЫМАТ (текст)      HMAC, сырсыз ТЕКШЕРИЛБЕЙТ
```

Чыныгы токенибиздин **борбор** бөлүгү:

```json
{
  "sub": "53",                      ← колдонуучунун id
  "role": "USER",                   ← колдонуучунун роли
  "iss": "minibank",                ← ким берди
  "iat": 1790597264,                ← качан түзүлдү
  "exp": 1790598164                 ← 900 секунддан кийин өлөт
}
```

> **Жогоруда пароль ЖОК, пароль хэши ЖОК, email ЖОК.** (Тест 21)

## 🛡️ User enumeration'дан коргоо

| Ката | Биздин жообубуз |
|---|---|
| Пароль туура эмес | `"Invalid email or password"` |
| Бундай email жок | `"Invalid email or password"` ← **БИРДЕЙ** |

**🛡️ Убакыт аркылуу коргоо:** чыныгы BCrypt ~50-100 мс иштейт. Эгер email жок болсо
салыштыруу өткөрүлбөй, тез жооп берер эле. Биз ошондо **чыныгы эмес** хэш менен
салыштырууну жасап, убакытты бирдей кылабыз — чыгаруучу убакытты өлчөп эле
эсеп жогун аныктап албасы.

## 🧠 CSRF эмне үчүн өчүрүлдү?

Биз **cookie ЭМЕС**, `Authorization: Bearer` header колдонобуз. Браузер header'ды
**автоматтык кошпойт** — ошондуктан CSRF'ге мүмкүнчүлүк жок.

> ⚠️ Эгер кийин сессияга (cookie) өтсөк — CSRF кайра **күйгүзүшү** керек.

## Логин баскыч-баскыч

```
(1) email = request.email()                // LoginRequest'те тазаланган
(2) user = findByEmail(email)              // маалымат базасы
(3) жок -> чын эмес хэш салыштыруу + 401   // 🛡️ timing коргоосу
(4) matches(password, hash) -> жалган -> 401
(5) token = jwtService.generateToken(user)
(6) AuthResponse(token, "Bearer", 900, userResponse)
```

---

# PHASE 3A — User жана Registration

## Файлдардын структурасы (3B кошулгасы менен)

```
src/main/java/com/minibank/
├── BankApplication.java
│   └── exclude UserDetailsServiceAutoConfiguration ← демкилгиз колдонуучуну өчүрүү
├── controller/
│   ├── AuthController.java        ← /register + /login
│   └── UserController.java        ← /api/users/me  🆕
├── service/
│   ├── UserService.java
│   └── AuthService.java           ← 🆕 логин логикасы
├── repository/UserRepository.java
├── entity/  User.java, Role.java, BaseEntity.java
├── dto/
│   ├── auth/  RegisterRequest, LoginRequest 🆕, AuthResponse 🆕
│   ├── user/  UserResponse
│   └── common/  ApiError
├── mapper/UserMapper.java
├── exception/  🆕 InvalidCredentials, Unauthorized, UserNotFound
├── security/                      ← 🆕 БАРЫГЫ ЖАҢЫ
│   ├── JwtProperties.java         ← jwt.secret (сырсыз), jwt.expiration
│   ├── JwtService.java            ← токен жасоо + текшерүү
│   ├── JwtAuthenticationFilter.java  ← ар бир сүрөткө
│   ├── AuthenticatedUser.java     ← id, email, role
│   ├── SecurityUtils.java         ← «мен киммин?»
│   ├── RestAuthenticationEntryPoint.java ← 401 JSON
│   └── RestAccessDeniedHandler.java     ← 403 JSON
└── config/
    ├── SecurityConfig.java        ← 🆕 чыпка тартиби, ким кире алат
    ├── PasswordConfig.java
    └── JpaAuditingConfig.java
```

## Токен мөөрүтү: 15 минут

`JWT_ACCESS_TOKEN_EXPIRATION=15m` → `expiresIn: 900` секунд.

**Эмне 15 мүн?**
- Узак = токен уурганда, уурганы өтө узак иштейт
- Кыска = колдонуучу тез-тез логин кылышы керек

Чын чечим — **refresh token** (кийинки этап): «15 мүн бүттү, бирок колдонуучу
кайра колдоно алат». Азыр жөн гана access token — бул окуу долбоору үчүн туура.

## 🔐 Spring'дин демкилгиз колдонуучусу ӨЧҮРҮЛДҮ

`spring-boot-starter-security` коюлганда, эгер өзүбүздүн `UserDetailsService`
берилбесе, Spring **өзү** колдонуучу жасайт:

```
Using generated security password: 4e5b21ce-33b6-4516-acc1-...
колдонуучу: user / пароль: ошол рандом номур
```

Бизде бул керек эмес (колдонуучулар маалымат базасында), ошондуктан
`BankApplication` ичинен `UserDetailsServiceAutoConfiguration` **exclude** кылдык.

> 🧠 Функционалдуу эмес эле (formLogin/httpBasic өчүрүлгөн), бирок «чыныгы эмес
> колдонуучу» деген ой кийин көңүл бүлгөн болмок.

## Токенге эмнени САЛБАЙБИЗ?

| Салуу | Жооп | Себеби |
|---|---|---|
| `password` | ❌ | Сыр — токен уургаса, пароль да кетет |
| `passwordHash` | ❌ | Паролду тартууга мүмкүн болмок деп ойлошчулар бар |
| `email` | ❌ | Зар болсо жараша, керек эмес |
| `sub` (id) | ✅ | «Мен киммин?» — бул керек |
| `role` | ✅ | Укуктарды чыпка берет |
| `exp` / `iat` / `iss` | ✅ | Мөөрүт жана чыгаруучу |

---

## Бүт чөйрө (request кантип жүрөт)

```
POST /api/auth/register  {"fullName":"...","email":"...","password":"..."}
        │
        ▼
┌──────────────────┐   @Valid: @NotBlank/@Email/@Size
│  AuthController  │   (калган кириш текшерүүлөрү)
└────────┬─────────┘
         │  RegisterRequest (DTO) — Entity ЭМЕС
         ▼
┌──────────────────┐   @Transactional
│   UserService    │   exists? -> encode -> save
└────────┬─────────┘
         │  User (Entity)
         ▼
┌──────────────────┐   Spring Data SQL жазат
│  UserRepository  │   SELECT ... / INSERT ...
└────────┬─────────┘
         │
         ▼
   ┌───────────┐
   │PostgreSQL │  users
   └───────────┘
         │
         ▼
   UserResponse (DTO) ← UserMapper, пароль ЖОК
```

## Файлдардын структурасы

```
src/main/java/com/minibank/
├── BankApplication.java
├── controller/AuthController.java      ← JSON → DTO → Service
├── service/UserService.java             ← БИЗНЕС-ЛОГИКА
├── repository/UserRepository.java       ← SQL (жазылган эмес, тууралуш)
├── entity/
│   ├── User.java                        ← users таблицасы
│   ├── Role.java                        ← USER / ADMIN
│   └── BaseEntity.java                  ← createdAt / updatedAt
├── dto/
│   ├── auth/RegisterRequest.java        ← КИРИШ (валидация + тазалоо)
│   ├── user/UserResponse.java           ← ЧЫГАРУУ (пароль жок)
│   └── common/ApiError.java             ← каталардын форматы
├── mapper/UserMapper.java               ← User → UserResponse
├── exception/
│   ├── EmailAlreadyRegisteredException.java
│   └── GlobalExceptionHandler.java      ← 400 / 409 / 500
└── config/
    ├── PasswordConfig.java              ← BCryptPasswordEncoder Bean
    └── JpaAuditingConfig.java           ← @EnableJpaAuditing
```

## 🧠 Entity ≠ DTO (эң маанилүү эреже)

| Класс | Кимге көрүнөт | Пароль барбы? |
|---|---|---|
| `User` (entity) | Маалымат базасына гана | 🔴 `passwordHash` бар |
| `UserResponse` (DTO) | API аркылуу дүйнөгө | ✅ **Жок** |

Эгерде биз `User` тим эле API'ге кайтарсак — бир күн эсепке `apiToken` кошулганда,
ал **автоматтык** JSONга түшүп, тышын чыгмак. DTO = «көзөгө көрүнгөн талаалардын
анык тизми».

## 🧠 Эмне `@Enumerated(EnumType.STRING)`?

```
ORDINAL -> базада САН:     0, 1, 2
STRING  -> базада ТЕКСТ:  'USER', 'ADMIN'
```

Эгерде `ORDINAL` колдонсо, кийин enum'га `MANAGER` кошкондо:

```
Азыр:  USER=0, ADMIN=1
Кийин: USER=0, ADMIN=2        ← ADMIN 1 болуп ЖАШЫРЫЛДЫ
Жыйынтык: базада "1" деп жазылган колдонмонулар ЭМИ MANAGER болуп калды
```

Ошондуктан **башынан** `STRING` — оңдөтүү кийин эмес, башта.

## 🧠 Пароль: BCrypt кантип иштейт

```
Кирген:      Password123!
Хэш:         $2a$10$viqJMj80CGQLMa/hY9gBp.1I95Fs/.tC3jG4WVwoN582clZVJlaRu
             └┬┘ └┬┘ └──────────┬──────────────┘
              │   │             └── 22 тамга: SHA-256 эмпирикалык туз
              │   └── 10 = "күч" (2^10 = 1024 айлампа иштетилген)
              └── BCrypt белгиси
Жалпы узундук: 60 белги, бирок БИЗДИКИ 255 -> алгоритм өзгөрсө орун калабы
```

- **Бир багыттуу**: хэште «артка» бүрүү МҮМКҮН ЭМЕС. Демек DB ууруп, парольдүү
  колдонуучунун **анык** маалыматын алышы МҮМКҮН ЭМЕС.
- **Туз (соль)**: бир эле пароль эки жолу хэштелсе, хэш **башкаша** болот
  (тест 4б менен текширилген). Болбосо, эки эле парольдүү колдонуучуну
  сырткы чыгаруучулар таанып алышты.

## 🧠 Три катмардын валидациясы

| Катмар | Эмне текшерет | Ката | Мисал |
|---|---|---|---|
| **1. DTO `@Valid`** | Клиенттин жиберген нерсе туурабы | 400 | Бош ат, жаман email |
| **2. Service** | Бизнес-эрежелер | 409 | Бул email мурун катталган |
| **3. Database CHECK/UNIQUE** | Акыркы сыр | 409 | Java багып кетсе да DB кайтарат |

> 2-катмар иштегенде эле 3-катмар көзөмөлдөнбейт. Чыныгы колдонмодо эки адам
> тез жазып жатса, экөөнү тез өткөзүп жиберсе, **маалымат базасы** бизди
> токтотот. Ошондуктан `DataIntegrityViolationException` катасы да 409 менен
> кармалып турат.

## 🧠 КИРИШ тазалоо (trim/lowercase) — кайда жана ЭМНЕ ҮЧҮН

Биз чыныгы сыноо чыктык: `"  HTTP.Case@Example.COM "` жатканда сервер **400** берди.
Себеби: `@Email` анотациясы асты-үстү боштукту **таныбайт**.

**Чечим:** тазалоо DTO'нун **компакт конструкторунда** (`RegisterRequest`) жасалды:

```java
public RegisterRequest {
    fullName = trim(...);                                   // "  Ali  " -> "Ali"
    email    = trim(...).toLowerCase(Locale.ROOT);         // "  A@B.COM " -> "a@b.com"
    // пароль ОЗГӨРҮЛБӨЙТ
}
```

- Бул код **объект түзүлгөн учун** иштейт, ДЕМЕК `@Valid` текшерүүсүнө
  тазаланган маалымат киредь.
- Пароль **тазаланбайт**: `" my pass "` менен `"my pass"` — эки БАШКА пароль.
  Тазаласак, колдонуучу өз тандаган паролду унутат.
- `Locale.ROOT` — "Turkish-I" катасынан коргоо: турец тилинде
  `toLowerCase("ALI")` -> `alı` болуп, базанын CHECK'и кайтарар эле.

## Тесттер: 40 тест (бардыгы өттү)

| Файл | Тесттер | Камтуусу |
|---|---|---|
| `BankApplicationTests` | 1 | Контекст + Spring Security |
| `UserServiceRegistrationTest` | 10 | PHASE 3A'дан |
| `AuthControllerRegistrationTest` | 10 | PHASE 3A'дан |
| `AuthLoginJwtTest` | **19** | 🆕 логин + JWT + коопсуздук |

🆕 `AuthLoginJwtTest` сиз берген **22 талапты** камтыйт (бир нече шарт бир тестте):

| # | Талап | Текшерүү |
|---|---|---|
| 1 | Туура email+пароль | 200 |
| 2 | Бош пароль | 401 |
| 3 | Бейтарап email | 401 |
| 4 | **Жооптор бирдей** | `message` таалары салыштырылды |
| 5 | Чоң/кичи тамга | `LOGIN-TEST@` иштеди |
| 6-7 | Пароль жок | Жоопто `password`, `$2a$` жок |
| 8-10 | `tokenType`, `expiresIn` | `Bearer`, `900`, 3 бөлүк |
| 11 | Токен менен `/me` | 200 |
| 12 | Токен жок | 401 + JSON |
| 13 | Бузулган токен | 401 |
| 14 | Өткөн мөөрүт | 401 (кол мене курулган токен) |
| 15 | Тамга топтош | 401 |
| 16 | DB'де жок колдонуучу | 401 |
| 17 | `USER` → `ROLE_USER` | ✓ |
| 18-19 | Register/Login ачык | ✓ |
| 20 | `/me` корголуу | `Basic ...` да 401 |
| 21 | Токенде пароль хэши жок | Payload **чечилди** текширилди |
| 22 | Сыр жооптордо жок | ✓ |

### 🧠 14-тест: мөөрүтү өткөн токенди КОЛ МЕНЕ курабыз

Чыныгы 15 минутты **күтүүгө** убакыт жок. Ошондуктан сыноо ушул сыр менен
өз убагынан 1 мүн өткөн токенди курат. Биз сыныкчыны колдонууга жарамайт —
бирок **чыныгы колдонмодун сыры менен** курабыз (тест 22 ошол сырды колдонот).

## Чыныгы HTTP сыноолору (curl)

```bash
# 1) Логин -> 200
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ali@example.com","password":"Password123!"}'

# 2) Токен менен /me -> 200
curl http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"

# 3) Токен ЖОК -> 401
curl http://localhost:8080/api/users/me

# 4) БУЗУЛГАН токен -> 401
curl http://localhost:8080/api/users/me -H "Authorization: Bearer invalid-token"

# 5) БАШКА ПАРОЛЬ -> 401 "Invalid email or password"
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ali@example.com","password":"WrongPassword"}'
```

Чыныгы натыйжа:

| Сыноо | Натыйжа |
|---|---|
| Логин (туура) | `HTTP 200`, `tokenType: Bearer`, `expiresIn: 900` |
| `/me` + токен | `HTTP 200` → `{"id":53,...,"role":"USER"}` |
| `/me` токенсиз | `HTTP 401` JSON |
| `/me` бузулган токен | `HTTP 401` JSON |
| `/me` тамга топтош | `HTTP 401` |
| `/me` башка сыр менен | `HTTP 401` |
| Бош пароль | `HTTP 401` `"Invalid email or password"` |
| Бейтарап email | `HTTP 401` `"Invalid email or password"` ← **бирдей** |

## 6. Коопсуздук: PHASE 3B тартиби БУЗУЛБАЙТ

`SecurityConfig` **бир сапка да өзгөрбөдү**. Себеби: эндпоинттер
`.anyRequest().authenticated()` астында — жаңы эндпоинт кошкон сайын
**ар дайым корголгон** болот. «Коопсуздук ээси» = чыпка.

```
POST /api/accounts    → JwtAuthenticationFilter → SecurityContext → 201
GET  /api/accounts/me → JwtAuthenticationFilter → SecurityContext → 200
   токенсиз          → чыпка таанымады → 401
```

| Сыноо | Натыйжа |
|---|---|
| Колдонуучу A өз эсебин көрөт | ✅ 200 |
| Колдонуучу B **A'нын эсебин** көрө албайт | ✅ (тест 8) |
| `/api/accounts/{id}` — эндпоинт **жок** | ✅ 404 (тест 9) |
| `?userId=` параметри **иштемийт** | ✅ (тест 9) |
| Токендиги колдонуучу өчкөн → 401 | ✅ (тест 10) |

## 7. Тесттер: **64/64 өттү**

| Файл | Тесттер |
|---|---|
| `AccountServiceTest` | **14** 🆕 |
| `AccountApiSecurityTest` | **10** 🆕 |
| `AuthLoginJwtTest` | 19 (3B'ден) |
| `UserServiceRegistrationTest` | 10 (3A'дан) |
| `AuthControllerRegistrationTest` | 10 (3A'дан) |
| `BankApplicationTests` | 1 |
| **Жыйынтык** | **64 passed, 0 failed** |

## 8. Чыныгы curl сыноолору

```bash
# 1) Катталуу
curl -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"fullName":"Байрамов Бай","email":"BAYR@Example.com","password":"Password123!"}'

# 2) Логин
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"bayr@example.com","password":"Password123!"}' \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['accessToken'])")

# 3) Эсеп ачуу -> 201
curl -X POST http://localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN"

# 4) Өз эсебим -> 200
curl http://localhost:8080/api/accounts/me -H "Authorization: Bearer $TOKEN"
```

Жооп:

```json
{"id":73,"accountNumber":"408178100000000067","currency":"RUB",
 "balance":0.0000,"createdAt":"2026-09-28T12:37:30.392860Z"}
```

| Сыноо | Натыйжа |
|---|---|
| `POST /api/accounts` (токен менен) | **201** |
| `GET /api/accounts/me` (токен менен) | **200** |
| Экинчи эсеп ачуу | **409** `already has a bank account` |
| `/me` токенсиз | **401** |
| `POST /accounts` токенсиз | **401** |
| `/accounts/73` (эндпоинт жок) | **404** `No endpoint` |
| Бузулган токен | **401** |
| Эки колдонуучу → эки ар башка эсеп | `...067` жана `...068` |
| `?userId=405` (башканын id) | **элек көзгөн эсебин** кайтарат |

## 9. Build

```
[INFO] Tests run: 64, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  12.187 s
```

```
Tomcat started on port 8080 (http) with context path '/'
Schema "public" is up to date. No migration necessary.
```

Flyway журналы:

```
 version |       description       | success
---------+-------------------------+---------
 1       | init                    | t
 2       | account one per user    | t
 3       | account number sequence | t
 4       | currency to varchar     | t
```

---

# PHASE 2 — Database (Сүрөттөмө)

## PostgreSQL эмне үчүн керек?

Маалымат **дискте** кабылдап турат. Java'ны өчүрсөң колдонмо өчөт, бирок маалымат калат.
Бир эле маалыматты көп колдонмону (user) жана эсепке бөлүүнү (account) PostgreSQL ичине
салып, **аны бардыгын көзөмөлдөйт**: уникалдуулук, байланыш, «терс баланс болбошун» сыяктуу
эрежелер. Java кода ката кылып дагы бир нерсе — маалымат базасы **акыркы сөз**.

## Docker Compose эмне үчүн керек?

PostgreSQL'ни компьютерге **орнотпосо** иштетип алабыз. `docker compose up -d` буйругу
аны контейнерде көтөрөт. Плюс кийин: `docker compose down` — колдонмону таза кылат,
маалымат **калат**.

## Flyway эмне үчүн керек?

Flyway — **схеманын ээси**. Таблицаларды SQL файлында (`V1__init.sql`) жазып,
колдонмо иштегенде автоматтык колдонмолго айкөкчү болуп берет.

- `spring.jpa.hibernate.ddl-auto: validate` → Hibernate **таблица түзбөйт**, текшерет гана
- `spring.jpa.open-in-view: false` → Controller'ге кайтарылган маалымат үчүн базага
  жашыруун суроо жүртпөйт

**Эң маанилүү эреже:** иштеп калган `V1__init.sql` файлын **өзгөртпө**. Flyway ар бир файлдын
checksum'ын сактат, өзгөрсөң иштеген колдонмо **ката менен токтойт**. Өзгөртүү керек болсо →
**жаңы `V2__....sql`** түз.

---

## Командалар

### 1. Биринчи жолу (же башка компьютерде)

```bash
cp .env.example .env      # чыныгы маалыматтарды .env ичине жазуу
docker compose up -d      # PostgreSQL көтөрүү (фондо)
docker compose ps         # "healthy" болгонун күтүү
```

### 2. Application иштетүү

```bash
./mvnw spring-boot:run
```
→ http://localhost:8080

### 3. Тест жана build

```bash
./mvnw test
./mvnw clean package
java -jar target/minibank-0.0.1-SNAPSHOT.jar
```

> ⚠️ Тест чыныгы PostgreSQLге туташат. `docker compose up -d` кылмаган болсоң, тест КАТА менен
> аяктат. Бул атайын — биз экитап каттамашы керек.

---

## Database токтотуу командалары

```bash
docker compose stop       # ⏸ Токтотуу. Маалымат КАЛАТ.
docker compose start      # ▶ Кайра ишгө түшүрүү

docker compose down       # ⏹ Контейнерди өчүрүү. Маалыдат КАЛАТ (volume сакталат).

docker compose down -v    # ⚠️⚠️⚠️  ЭСКЕРТҮҮ: БАРДЫГЫН ӨЧҮРҮҮ!
                          # Volume да жоголот -> users / accounts / transactions
                          # БАРДЫГЫ ЖОГОЛУП КЕТЕТ. Миграциялар кайра иштейт
                          # (таза база), бирок маалымат КАЙТАРЫЛБАЙТ.
```

> `-v` деген сөз **volume** ("том") дегенди билдирет. Бул буйрук — «өт өчүрүү»,
> кичигиреэ «маалымат өчүрүү». Керек болсо гана колдон.

## Database схемасы

```
┌──────────────────────────────┐
│           users              │
├──────────────────────────────┤
│ id             PK            │
│ full_name      NOT NULL      │
│ email          UNIQUE        │  (кичи тамга менен)
│ password_hash  NOT NULL      │  (BCrypt — азыр толук жазылбайт)
│ role           USER | ADMIN  │
│ created_at / updated_at      │  (TIMESTAMPTZ)
└──────────────┬───────────────┘
               │ 1 : N
               ▼
┌──────────────────────────────┐
│          accounts            │
├──────────────────────────────┤
│ id             PK            │
│ user_id        FK → users    │  ON DELETE RESTRICT
│ account_number UNIQUE        │
│ currency       RUB           │  (CHECK)
│ balance        NUMERIC(19,4) │  CHECK (balance >= 0)  🔴
│ created_at / updated_at      │
└──────────────┬───────────────┘
               │ 1 : N
               ▼
┌──────────────────────────────┐
│       transactions           │  ЫШАЙТ — кошулбайт жана өчүрүлбейт
├──────────────────────────────┤
│ id             PK            │
│ account_id     FK → accounts │  ON DELETE RESTRICT
│ type           DEPOSIT |     │
│                WITHDRAW |    │
│                TRANSFER      │
│ amount         NUMERIC(19,4) │  CHECK (amount > 0)  🔴
│ balance_after  NUMERIC(19,4) │
│ description    (nullable)    │
│ created_at     TIMESTAMPTZ   │
└──────────────────────────────┘

+ flyway_schema_history   ← Flyway өзүнүн журналы
```

### Байланыштар

```
users  1 ──────<  accounts  1 ──────<  transactions
```

### Негизги эрежелер

| Эреже | Түрү | Себеби |
|---|---|---|
| `email` UNIQUE | UNIQUE | Бир эсеп эки жолу ачылбасин |
| `email` кичи тамга | CHECK | `Ali@mail.com` = `ali@mail.com` |
| `role` USER/ADMIN | CHECK | Башка маани киргизбесин |
| `account_number` UNIQUE | UNIQUE | Эсеп номуру кайталанбасин |
| `balance >= 0` | **CHECK** | 🔴 Терс баланс — **маалымат базасынын өзүнө** тыяк |
| `currency = 'RUB'` | CHECK | Азыр бир гана валюта |
| `amount > 0` | **CHECK** | 🔴 0 жана терс сумма болбосун |
| `balance_after >= 0` | CHECK | Көрсөлгөн баланс да жетик болсун |
| FK `ON DELETE RESTRICT` | FK | **Акча өчүрүлбөсүн.** CASCADE коюлса, эсеп өчкөндө операциялар да өчөт |

### Индекстер жана алардын милдтери

| Индекс | Каай суроого жооп берет |
|---|---|
| `users_pkey`, `accounts_pkey`, `transactions_pkey` | Дал издөө: `WHERE id = 5` |
| `ux_users_email` (UNIQUE) | Логин учурунда эл табуу + уникалдуулук |
| `ux_accounts_account_number` (UNIQUE) | Эсеп номуру боюнча табуу |
| `ix_accounts_user_id` | «Менин эсептерим» тизми |
| `ix_transactions_account_id_created_at` | «Бул эсептин операциялары, эң жаңысы башта» |
| `ix_transactions_created_at` | «Бүгүн канча иштеди?» (ADMIN) |

> ⚠️ PostgreSQL чет ээ (`user_id`, `account_id`) колонкаларына **автоматтык индекс түзбөйт**
> (MySQL түзөт). Ошондуктан `ix_accounts_user_id` кол менен кошулду.

## Файлдардын структурасы

```
├── compose.yml                 ← PostgreSQL контейнери
├── .env                        ← чыныгы маалыматлар (Gitке КИРБЕЙТ)
├── .env.example                ← үлгү (Gitке киреди)

src/main/resources/
├── application.yml             ← БД жөндөөлөрү (${...} аркылуу)
└── db/migration/
    └── V1__init.sql            ← 3 таблица, эрежелер, индекстер
```

## Этаптар

| PHASE | Мазмуну | Статус |
|---|---|---|
| 0 | Чөйрөнү текшерүү | ✅ |
| 1 | Project Skeleton | ✅ |
| 2 | PostgreSQL + Flyway + таблицалар | ✅ |
| 3A | Registration (User, BCrypt, Entity) | ✅ |
| 3B | Login + JWT + Security | ✅ |
| 4 | Accounts (эсеп ачуу, 18 белгилик номер) | ✅ |
| 5 | Deposit / Withdraw | ✅ |
| 6 | Transfer (atomicity, deadlock goragy) | ✅ |
| 7 | Transaction History (pagination + filter) | ✅ |
| 8 | Admin API + Role-Based Access Control | ✅ |
| 9 | Integration Hardening & Reliability | ✅ |
| 10 | Frontend | ⬜ |
---

# PHASE 9 — Integration Hardening & Reliability

Jaña biznes-funksiýa **ÝOK**. Bu faza diňe bar bolan 8 fazany
**gözden geçirmäge**, ýalňyşlary tapmaga we subut bilen ýapmaga
gönükdirildi.

## Nämä edildi

**Error contract** — ähli jogaplar bir formatda (`ApiError`):
`timestamp, status, error, message, path, fieldErrors`.

| Status | Ýagdaý | Synag |
|---|---|---|
| 400 | ýalňyş ölçeg / ýarym JSON | 2 |
| 401 | token ýok / buzulgan | 2 |
| 403 | USER → admin endpoint | 1 |
| 404 | ýok endpoint / ýok esap | 2 |
| 405 | ýer bar, usul ýok | 1 |
| 409 | ýeterlik däl / esap ýok | 2 |
| 415 | nädogry Content-Type | 1 |
| 500 | çäksiz ýagdaý | 1 |

**Information leakage** — jogapta `stack trace`, `org.springframework.*`,
`java.lang.*`, `jdbc:postgresql://`, faýl ýoly, SQL çykarmaýar.
Jogap **diňe** öz ýazgymyz bilen çykýar.

**Security hardening** — JWT 7 hal (ýok/buzulgan/üýtgedilen/möhleti
geçen/başga imza/başga issuer/ýalňyş subject), mass assignment
(`role":"ADMIN"` ýazsa → `USER` galýar), IDOR, parol sızdırmak ýok.

**Money safety** — `BigDecimal` diňe, `compareTo` bilen deňeşdirme,
scale 4, ters balans hiç haçan bolmaýar (konkurrent çekimde-de).

**Transaction rollback** — 4 ýazuw (2 balans + 2 žurnal) hemişe
hemelelikde; ýalňyşda ÄHLISI gaýtarýar.

**Concurrency regression** — 20 parallel deposit → **2000**
(lost update ýok); A→B we B→A parallel → **deadlock ýok**;
30 parallel withdraw → balans **hiç haçan terse däl**.

**DB integrity** — FK `ON DELETE RESTRICT`: ýazgysy bolan esep/user
ýok edilmeýär (CASCADE bolsaydym — akçanyň barlagy ýitýärdi).

## Tapylan we düzedilen ýalňyş

🔴 **415 ýalňyşlyk ýagdaýda 500 berýärdi.** Klient `Content-Type:
text/plain` ýazsa, Spring `HttpMediaTypeNotSupportedException`
taşlaýardy — handler ýok ýaly, umumi `Exception` → **500**
("server döwüldi"). Handler goşuldu → **415** + regression test.

## Testler

**305/305 passed** (43 täze, 5 sany integration synak).

| Faýl | Sany |
|---|---|
| `ErrorContractTest` | 14 |
| `SecurityHardeningTest` | 20 |
| `AtomicityConcurrencyRegressionTest` | 6 |
| `EndToEndIntegrationTest` | 2 |
| `DatabaseIntegrityTest` | 1 |

Flyway migration: **5** (üýtgemeýär). `ddl-auto=validate`,
`open-in-view: false` — saklanýar.
