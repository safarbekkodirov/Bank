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
| springdoc-openapi (Swagger UI) | 2.8.17 |
| Spring Boot Actuator | (Spring Boot bilen biige keleýär) |

## Учурдагы абал: **PHASE 10D — Database Failure Handling**

Эми иштеп ятат: эсеп ачабыз, акча салýarys, çekýäris, başga esäbe geçirýäris we
öz operasiýalarymyň **taryhyny sahypa sahypa** görýäris. Ähli amallar
tokenden yzarlanýar — klient öz eýesini başgasynyň taryhyna geçirip bilmeýär.

Иşleýän funksiýalar: Registration, JWT login, Bank Account, Deposit, Withdraw,
Transfer (iki esap arasynda), Transaction History (pagination + filter), we
**Admin API** (ROLE_ADMIN — diňe okaýan administrator paneli).

🔠 **PHASE 10A**: ähli REST endpointler **OpenAPI 3** bilen documented
(Swagger UI). Täze biznes-funksiýa **ýok** — diňe dokumentasiýa.

💾 **PHASE 10B**: PostgreSQL **backup / restore** we recovery
(deployment gollanmasy — aşakda). Täze biznes-funksiýa we täze
schema **ýok** — diňe işläp durmagyň ynamlylygy.

📈 **PHASE 10C**: **Actuator** — `/actuator/health`, `/actuator/metrics`.
Kolgonuň janlylygyny we sanlaryny görmek (aşakda). Täze biznes-funksiýa
we täze schema **ýok** — diňe göze görnüş we log.

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
| 10A | API Documentation + OpenAPI (Swagger UI) | ✅ |
| 10B | Database Backup, Recovery & Operational Reliability | ✅ |
| 10C | Observability (Actuator, Health, Metrics, Logging) | ✅ |
| 10D | Database Failure Handling & Auth Resilience (503) | ✅ |
| 10 | Frontend | ⬜ |

---

# Database Backup and Restore

PHASE 10B. Täze biznes-funksiýa we täze migration **ýok** — bu faza
diňe «маалымат ушул базада бармы?» soragyna jogap berýär.

## 🔑 Önünde: backend-iň ýoly

```bash
docker compose up -d      # PostgreSQL 16 + «minibank» bazasy
./mvnw spring-boot:run    # backend → http://localhost:8080
```

## Backup

```bash
./scripts/backup-db.sh
```

Çyktysy: `backups/minibank_YYYYmmdd_HHMMSS.dump`

Aty özüňiz bilen bellenmek üçin:

```bash
./scripts/backup-db.sh before_upgrade.dump
```

**Format: `.dump` (`pg_dump -Fc`)** — kompressirlenen, parallel
`pg_restore` bolýan, bitewi atlandyrylýan format.

## Restore

```bash
./scripts/restore-db.sh <backup-file>
```

Bu — **DRY-RUN**: diňe näme boljakdyny görkezýär, **эч нерсе
ýok etmeýär**.

Chyreally ýerine çalyşmak üçin:

```bash
./scripts/restore-db.sh backups/minibank_20260930_140639.dump --yes-please
```

### ⚠️ BU BUJURUK MAZLMATY ÖÇÜRÄR

| Ýagdaý | Netije |
|---|---|
| `--yes-please` **ýok** | **Эч нерсе өзгэрмеýär** (dry-run) |
| `--yes-please` **bar** | `users`, `accounts`, `transactions`, `flyway_schema_history` **өчürilýär** we backup'tan **gaýtadan ýazylýar** |

> 🧡 **Bu ýer giňişleýin gollanma.** Bu lokal okuw maksatly
> Mini Bank. Eger bu ýerde hakyky/öndürüş bazasy bolalsa,
> indiki hatda çalyşmaň.

## Restart

```bash
docker compose restart postgres
docker compose ps          # «healthy» bolmagyny garaşyň
```

**Netije:** maýat namalar **`postgres_data` atly volume**-da
saklanýar, şonuň üçin restart hiç näme ýitirmeýär.

> ⚠️ `docker compose down` — konteýneri öçürýär, maýlumat galýar.
> `docker compose down -v` — hem konteýneri, **hem maýlumaty
> ýok edýär**. Iň ýygy «barmak ýitirim» sebäbi şu.

## Verify

### 1) Maýlumat barymy

```bash
docker exec -it minibank-postgres \
  psql -U minibank -d minibank \
  -c "select count(*) from users;"
```

### 2) Backup bitewmi? (içindäki objektleri görkezýär)

```bash
docker cp backups/<fayl>.dump minibank-postgres:/tmp/v.dump
docker exec minibank-postgres pg_restore --list /tmp/v.dump
docker exec minibank-postgres rm -f /tmp/v.dump
```

Bolmalı tablisalar: `users`, `accounts`, `transactions`,
`flyway_schema_history` (+ `account_number_seq` sequence).

### 3) Flyway migration ýagdaýy

```bash
docker exec minibank-postgres psql -U minibank -d minibank \
  -c "select version, description, success
      from flyway_schema_history order by installed_rank;"
```

Ähli 5 sany `success = t` bolmaly.

### 4) Çekemeler (constraints) ýagdaýy

```bash
docker exec minibank-postgres psql -U minibank -d minibank \
  -c "select conrelid::regclass, conname, contype
      from pg_constraint where connamespace='public'::regnamespace
      order by 1;"
```

15 sany çeke bolmaly: 4 PRIMARY KEY, 2 UNIQUE, 2 FOREIGN KEY,
6 CHECK + Flyway PK.

### 5) API işläýärmi?

```bash
./mvnw spring-boot:run
```

Logda görkezilmelidir:

```text
Successfully validated 5 migrations
Schema "public" is up to date. No migration necessary.
```

Soňra token bilen:

```bash
curl localhost:8080/api/users/me   -H "Authorization: Bearer <TOKEN>"
curl localhost:8080/api/accounts/me -H "Authorization: Bearer <TOKEN>"
```

## 🔐 Howpsuzlyk

| Düzgün | Nähili |
|---|---|
| Parol scriptde **ýok** | `.env`-ден gelýär; konteýneriň **içki soketi** `trust` — parol hiç haçan buýrukça geçmeýär |
| `.env` Git'e **gitmeýär** | `.gitignore` |
| Backup Git'e **gitmeýär** | `.gitignore`: `backups/` |
| ❌ `*.sql` globally **ýok** | Flyway migration faýllary `*.sql` — olar Git'de galmaly! |
| Production credentials **ýok** | Bu ýörite lokal okuw gollanmasy |

---

# PHASE 10A — API Documentation & OpenAPI

Jany biznes-funksiýa **ÝOK**. Bu faza diňe bar bolan API'yi
**dokumentasiýa** etmek bilen meşgullan — endpointleri, DTO'lary,
HTTP kodlary we parametrlere düşündiriş goşuldy.

## Näme goşuldy

| Näme | Faýl / Ýer |
|---|---|
| springdoc-openapi-starter-webmvc-ui **2.8.17** | `pom.xml` |
| `Mini Bank API` / `1.0.0` / süröçbet | `config/OpenApiConfig.java` |
| JWT Bearer security scheme | `config/OpenApiConfig.java` |
| `@Schema` — 13 DTO | `dto/**` |
| `@Operation` + `@ApiResponses` — 14 endpoint | `controller/**` |
| `@Parameter` — 7 filter parametri | `AccountController`, `AdminController` |
| `/v3/api-docs` + `/swagger-ui` ачык жол | `config/SecurityConfig.java` |
| 18 documentation test | `integration/OpenApiDocumentationTest.java` |

## Swagger UI nirede?

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON (çig süröçbet):

```text
http://localhost:8080/v3/api-docs
```

## 🔑 JWT Swagger UI'de nähili ulanylýar?

1. **Authorize** basyň → **bearerAuth** saýlaň → **Authorize**.
2. Ilki gezek hökmany hökmany ýok: tokeni **Authorize** gutusyna
   ýazmasaňyz, diňe `register` we `login` işleýär.
3. **Try it out** basyň → **Execute**.
4. Token şu ýerde ýazylyp bilner —
   `POST /api/auth/login` → `AuthResponse` → `accessToken`.

> 🟡 «Bearer» sözüni **ýazmaň**. Headeriň özi `Authorization: Bearer
> <token>` görnüşinde ýygylandyrýar. Diňe tókendiň özüni ýazyň.

## Endpoint kategoriýalary

| Kategoriýa | Endpointler |
|---|---|
| **Auth** ( açyk) | `POST /api/auth/register`, `POST /api/auth/login` |
| **User** | `GET /api/users/me` |
| **Account** | `POST /api/accounts`, `GET /api/accounts/me`,<br>`POST /api/accounts/me/deposit`, `.../withdraw`, `.../transfer`,<br>`GET /api/accounts/me/transactions` |
| **Admin** (ROLE_ADMIN) | `GET /api/admin/users`, `.../users/{id}`,<br>`.../accounts`, `.../accounts/{id}`, `.../accounts/{id}/transactions` |

Jemi: **14 endpoint**.

## 🔴 Korag ulgamy ÜÝTGEMEDIK

| Ýagdaý | Netije |
|---|---|
| Token ýok | **401** |
| Token nädogry/üçük | **401** |
| `ROLE_USER` → admin endpoint | **403** |
| `ROLE_ADMIN` → admin endpoint | **200** |
| `/api/docs-does-not-exist` token ýok | **401** |

Dokumentasiýa diňe `/v3/api-docs/**` we `/swagger-ui/**` üçin açyk.
`/api/**` — doly ýapyk.

## 🔐 Näme DOKUMENTASIÝA-da ÝOK?

Parol hash · JWT syr · SQL/Hibernate jikme-jiklikleri · stack
trace · fayl ýollary — hiç haçan. `/v3/api-docs` faýlyny
gözden geçirip, barlamak bolýar.

## Testler

**323/323 passed** (305 bar + 18 täze).

| Faýl | Sany |
|---|---|
| `OpenApiDocumentationTest` | 18 |

`OpenApiDocumentationTest` şulary barlaýar: `/v3/api-docs` → 200,
boş däl, `Mini Bank API` title, `bearerAuth` (http/bearer/JWT),
---

# PHASE 10B — Database Backup, Recovery & Operational Reliability

Jany biznes-funksiýa **ÝOK**, jany schema **ÝOK** (V6 ýok).
Bu faza diňe «маалымат ýitirmese we gaýt alynsa» soragyna
subut goşýar.

## Näme goşuldy

| Näme | Ýer |
|---|---|
| `backup-db.sh` | `scripts/backup-db.sh` |
| `restore-db.sh` | `scripts/restore-db.sh` |
| 5 recovery test | `integration/DatabaseBackupRestoreTest.java` |
| `backups/` ignore | `.gitignore` |

## 🔴 Restore — 3 gatly goraýyş

1. `--yes-please` **ýok** → diňe görkezýär, **эч нерсе өзгэрмеýär**
2. İşläp başlamazdan **öň** açyk WARNING çykýar
3. Interaktiw bolsa `yes` diýip ýazdyrmaly

## 🧡 Tapylan we düzedilen ýalňyş

🔴 **Restore'ň barlagy şertli ýalňyşlyk döretdi.** `pg_restore
--list` каждары атдан soň ээсиň ady gelýär:
`TABLE public users    minibank`. Ilki ýazylan ýagdaýda biz
`...users$` diýip barladyk — bu **hiç haçan** gabat gelmeýär,
şonuň üçin chynlygy doly backup «tablisa ýok» diýip hasaplanýardy.

Düzeltme: `TABLE( DATA)? public <at>` — şema **we** maglumat
hem barlanýar.

## Testler

**328 toplum** (323 bar + 5 täze). 5 täze test
`PHASE10B_DOCKER_TESTS=true` berilende işleýär, ýoksa howpsuzça
skip bolýar.

| Test | Näme barlaýar |
|---|---|
| 1 | backup faýl döredýär, 0 däl, `backups/` içinde |
| 2 | `pg_restore --list` ony **okaýar** (4 tablisa) |
| 3 | **restore «--yes-please» bolmasa bir setir hem üýtgemeýär** |
| 4 | ýok/boş/buzulan faýl → **non-zero** |
| 5 | ýazylmaýan maksat → **non-zero** |

> 🧡 3-nji test **mutatsiýa bilen barlanady**: howpsuzlyk
> ýapagy aýrylanda test hakykata **DÜŞÝÄR** (ýeňil barlag däl).

## Subutlar

- Restart → snapshot **düýb-eýt görnüşde şaýat**
- Wipe → restore → snapshot **düýb-eýt görnüşde şaýat**
- 15 çeke, 12 «ýazmak» synagy — hemmesi **blockingdi**
- Flyway: `Successfully validated 5 migrations`

14 endpointiň hemmesi, 13 DTO shemasy, `ApiError` we `PageResponse`
atlandyryşlarynyň dogrylygy, walidasiýanyň görünmegi we ýalňyş
ýazylan şemalar — şeýle hem «dokumentasiýa açyk, API ýapyk».

Flyway migration: **5** (üýtgemeýär). `ddl-auto=validate`,
`open-in-view: false` — saklanýar.

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

---

# PHASE 10C — Observability & Operational Monitoring

Jany biznes-funksiýa **ÝOK**, jany schema **ÝOK** (V6 ýok), jany
endpoint (mazmuny boýunça) **ÝOK**. Bu faza diňe «işläp durýarmy?»
we «näme bolup biler?» diýen iki sowaga jogap berýär.

## Näme goşuldy

| Näme | Faýl / Ýer |
|---|---|
| `spring-boot-starter-actuator` (**3.5.16**, parent-dan) | `pom.xml` |
| `management.endpoints.web.exposure.include: health,info,metrics` | `application.yml` |
| `show-details: never`, `show-components: never` | `application.yml` |
| `server.shutdown: graceful` + `timeout-per-shutdown-phase: 30s` | `application.yml` |
| `logging.level.*` (root INFO, framework WARN) | `application.yml` |
| `/actuator/**` howpsuzlyk kadasy (`permitAll` + `denyAll`) | `config/SecurityConfig.java` |
| `RequestLoggingFilter` (method, path, status, wagt) | `config/RequestLoggingFilter.java` |
| 5xx logyna **method** goşuldy | `exception/GlobalExceptionHandler.java` |
| 19 täze observability test | `integration/ObservabilityTest.java` |

🔴 **Aktuator wersiýasy ýok** — `spring-boot-starter-parent` oňa
özüýeti berýär. Spring Boot ýokary wersiýa geçse, pom hem şonuň
bile birге ösýär.

## Health endpoint

```bash
curl localhost:8080/actuator/health
```

```json
{"status":"UP"}
```

**Diňe şu bir söz.** `show-details: never` sebäpli jogapda DB
ady, `jdbc:postgresql://...`, ulanyjyň ady ýa-da fayl ýoly
**çykmaýär** — olar diňe logda (serweriň içinde) görnüp durýar.

> 🟡 PostgreSQL öçen bolsa: `{"status":"DOWN"}` + HTTP **503**.
> Hiç haçan maglumat sızmaýar, diňe «DOWN».

## Metrics endpoint

```bash
curl localhost:8080/actuator/metrics
curl localhost:8080/actuator/metrics/http.server.requests
curl 'localhost:8080/actuator/metrics/http.server.requests?tag=uri:/api/accounts/me'
```

**Jemi 93 metrika**, şol sanda:

| Metrika | Näme görkezýär |
|---|---|
| `http.server.requests` | Her endpoint boýunça ulgam sanawy, wagt, 4xx/5xx |
| `hikaricp.connections.*` | DB pooluň janlylygy |
| `jvm.memory.*`, `jvm.gc.*` | Ýat we «garbage collector» |
| `process.cpu.usage`, `disk.free` | Prosessor we disk |

🧡 **Metrikanyň ady** — Spring Boot-ň öz ady: `http.server.requests`
(`/actuator/metrics` sanawyndan barlanyp alynýar).

### HTTP metrikasy barlanyşy (subut)

```text
GET /api/accounts/me  x5
/actuator/metrics/http.server.requests?tag=uri:/api/accounts/me
   COUNT: 6.0 -> 11.0   (DELTA = 5) ✅
```

### 🧡 Näme üçin täze «biznes metrikalary» goşulmady?

Tapşyrma `minibank.operations.deposit` ýaly sanawlar hörlanyşy
mümkin diýipdi. Biz **goşmadyk** — sebäbi:

1. Spring Boot-ň `http.server.requests` **haýsy endpointiň**
   näçe gezek çyryldygyny eýeýär: `uri=/api/accounts/me/deposit`,
   `.../withdraw`, `.../transfer`, `uri=/api/auth/login`.
   Şol 4 sany talap **şu metrikanyň içinde bar**.
2. Täze sanaw goşmak = `MoneyService`, `TransferService`,
   `AuthService` üçüne-de `Counter.increment()` çagyrmak —
   ol **işjeň (biznes) koduny` üýtgetmek** bolar.
3. Tapşyrma: «işjeň logikany sebepsiz ýazma».

Netije: **arassa arhiitektura** — kwartil derejesi ýok, biznes
kodunda bir setir hem üýtgenmedi.

## 🔐 Actuator howpsuzlygy

**Açyk (3 tanä, monitoring üçin):**

| Endpoint | Nähili ulanylýar |
|---|---|
| `/actuator/health` | «Kolgon janmy?» — load balancer şu ýyly sorýar |
| `/actuator/metrics` | Sanlar — grafik gurmak üçin |
| `/actuator/info` | Wersiýa — häzirizir `{}` (build-info ýok) |

**🔴 AÇYK DÄL — hemmeki üçin (anonim hem, ADMIN hem):**

| Endpoint | Näme açardy |
|---|---|
| `/actuator/env` | **ÄHLI `.env` bahalary** — DB paroly, JWT syry |
| `/actuator/configprops` | Konfigurasiýanyň shemasy |
| `/actuator/heapdump` | 🧠 Ýatyşyň **içindäki** ähli zat |
| `/actuator/threaddump` | Ulgamyň içki ýagdaýy |
| `/actuator/loggers` | Log derejesini üýtgetmek |
| `/actuator/beans`, `/mappings` | Gury kod shemasy |
| `/actuator` (sanaw) | Ähli actuator endpointleriň sanawy |

```java
.requestMatchers("/actuator/health", ..., "/actuator/metrics/**").permitAll()
.requestMatchers("/actuator/**").denyAll()
```

🧡 `denyAll()` — `hasRole("ADMIN")` **ýok**: sebäbi admin-e açyk
bolsa-da, bu endpointler **maşyna** üçin, olary açmak onuň
garaşsyzlygyna bagly bolmaly däl.

### 🔐 Näme DÖWÜLETMEÝÄR?

Parol · `passwordHash` · BCrypt (`$2a$`) · JWT tókenniň özi ·
DB paroly · JDBC URL · `.env` bahalary.

Barlanýan zat — **söz däl, hakyky baha**: test `.env`-däki
**aniki** paroly we **aniki** JWT syryny alýar we her jogapda
**ýok** diýip tassyklaýar.

> 🟡 `/v3/api-docs`-da «Authorization: Bearer \<token\>» **sözü**
> bar — bu **dokumentasiýanyň düşündirişi** (OpenApiConfig),
> hakyky tókén däl. Şonuň üçin test şuny «ýalňyş» diýip hasapmaýar.

## Request logging

Her `/api/**` soragy üçin **bir setir**:

```text
GET /api/accounts/me -> 200 (6 ms)
POST /api/accounts -> 201 (74 ms)
GET /api/admin/users -> 200 (21 ms)
```

Faýl: `config/RequestLoggingFilter.java`.

⛔ **HIÇ HAÇAN ýazylmaýar:** `Authorization` header · request body ·
query string. Sebäbi: birinjilerde JWT tókéni, ikinjide açyk parol
bolardy. (Barlag: logda `Authorization` — **0**, `Bearer` — **0**.)

🧡 `/swagger-ui/**` we `/actuator/**` loglanmaýar: olar 20+ faýl
sorag berýär, monitorki sorag bolsa sekunda 10 gezek — olaryň
«hakyky» gaby bolup durmagyň öňi ýaşar.

## Error logging

| Ýagdaý | Log (server) | Jogap (klient) |
|---|---|---|
| 4xx/409 | `warn` — sebäbi bilen | `ApiError` (PHASE 9) |
| 5xx | `error` + **stack trace** + method + jol | `500` + `ApiError` |

**5xx jogaby** — dälildirilme üçin ýazyk ýazylmaz:

```json
{"status":500,"error":"Internal Server Error",
 "message":"An unexpected error occurred. Please try again later.",
 "path":"/api/..."}
```

Stack trace · SQL · `jdbc:` · fayl ýoly · Java klassynyň ady —
**hiç haçan** jogapda çykmaýar (PHASE 9 `ErrorContractTest` #7).

## 🔑 Log derejeleri

| Komponent | Dereje | Näme üçin |
|---|---|---|
| `com.minibank` | **INFO** | Request we katalogy — görkezmek gerek |
| `root` | INFO | «Tomcat started on port 8080» — gerekli |
| `org.springframework.web` | **WARN** | Her soragyň 3-4 setirini basyp ýatýar |
| `org.springframework.security` | **WARN** | TRACE-de **Authorization header** çykýar! |
| `org.hibernate` | WARN | SQL tekstiniň ýykymy |
| `org.hibernate.orm.jdbc.bind` | WARN | ⛔ SQL **bind parametrleri** (`Secret123!`) |
| `org.flywaydb` | INFO | «Successfully validated 5 migrations» |

🧡 Biz DEBUG-i öçürmek bilen **näme ulanyp almyz** — öçürmek
bilen näme ulanmaýarys: DEBUG-de parollar we tókénler çykýar.

## Graceful shutdown

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**«Илеги» кептөөлөм.** Ctrl+C basanda «immediate» bolmagy
mumkin: Tomcat **işleýän** soragyny kesip taşlap, akça girizilgen
eýede «hesapda ýok» ýagdaýyny döredip bilerdi.

`graceful`: **täze sorag kabul edilmeýär**, işleýän soraglar
**bitýänçä garaşylýar** (çenli 30 sekunt), soňra arassa ýapylýar.

## 🧡 Tapylan we düzedilen ýalňyş

🔴 **Health 30 sekunt «käm edip» jogap berýärdi.** PostgreSQL
öçen halatynda `/actuator/health` **HİÇ ÝOGSAÝAN** ýaly 30 sekunt
 garaşyp, soň ýene-diňe `DOWN` diýip jogap berýärdi. Sebäbi:
HikariCP pooly «30 sekunt garaş» diýip sazlanan. Monitoring üçin bu
ýeterlik däl — load balancer öz wagtynda «öli» diýip hasaplar.

**Düzeltme:** HikariCP **connection-timeout** 3 sekunt edildi
(health çalgyna «jakyn» jogap berýär), munuň üstüne
`validation-timeout` goşuldy. Iň ýaşy ýagdaýda `/actuator/health`
**3 sekuntda** `DOWN` diýip jogap berýär.

🔴 **PHASE 10B-niň restore testleri** aktuator goşulandan soň hem
`PHASE10B_DOCKER_TESTS=true` bilen doly işledi — 5/5 passed.

## Database sagatlygy

```bash
docker compose stop postgres     # ⛔ -v DÄL!
curl localhost:8080/actuator/health
#  {"status":"DOWN"}   HTTP 503

docker compose start postgres
curl localhost:8080/actuator/health
#  {"status":"UP"}     HTTP 200
```

**Subut:** `up -> down -> up` — we maglumat ýitmedi
(`users` tablisasy doly saklandy).

> ⛔ `docker compose down -v` **HIÇ HAÇAN** bu testde ulanylmaýar —
> ol **tom ýok edýär**.

## Testler

**347 jemi** (342 bar + 5 täze). 5 sany PHASE 10B testi
`PHASE10B_DOCKER_TESTS=true` berilende işleýär, ýoksa howpsuzça
skip bolýar.

| Faýl | Sany |
|---|---|
| `ObservabilityTest` | **19** |

`ObservabilityTest` şulary barlaýar: health 200/UP · details sızmaýar
· metrics 200 · `http.server.requests` bardyr we **ýuwulýar** ·
sensitive endpointler anonim hem, ADMIN hem girip bilmeýär · eski
4 endpointiň 401/403/200矩阵y · OpenAPI üýtgemedi · hakyky parol we
JWT syry hiç ýerde sızmaýar.

## Subutlar

```text
/actuator/health   -> 200 {"status":"UP"}     ✅
/actuator/metrics  -> 200 (93 metrika)       ✅
/actuator/info     -> 200 {}                 ✅
/v3/api-docs       -> 200                    ✅
/swagger-ui/index.html -> 200                ✅
/actuator/env      -> 401 (anonim) / 403 (ADMIN)  ✅
```

**Howpsuzlyk matritsasy (çyn JAR, hakyky tókén bilen):**

| Endpoint | tokensiz | nädogry | USER | ADMIN |
|---|---|---|---|---|
| `/api/users/me` | 401 | 401 | 200 | 200 |
| `/api/accounts/me` | 401 | 401 | 200 | 404\* |
| `/api/accounts/me/transactions` | 401 | 401 | 200 | 404\* |
| `/api/admin/users` | 401 | 401 | **403** | **200** |

\* Admin-iň öz eşegi ýok — bu biznes düzgüni, korag däl.

---

# PHASE 10D — Database Failure Handling & Authentication Resilience

Jany biznes-funksiýa **ÝOK**, jany schema **ÝOK** (V6 ýok),
JWT format **ÜÝTGEMEDI** (HS512, issuer `minibank`, claims — hemmesi
özgärdi). Bu faza diňe bir ýalňyşy düzedýär — ol PHASE 10C-de
«out-of-scope» diýlip goýulýardy.

## 🔴 NÄME ÜÇIN BU FAZA?

PHASE 10C-de şeýle bir ýagdaý açyldy:

```text
docker compose stop postgres
curl -H "Authorization: Bearer <VALID TOKEN>" /api/users/me
```

**Jogaby: 401.** Emma ol **NÄDOGRY** — sebäbi:

```text
401 diýmek  →  «SENIŇ TOKEŇIŇ NÄDOGRY»      (authentication failure)
                ↯ emma şonda token SHOL DOGRYDI
503 diýmek  →  «SEN DOGRY, emma MEN PESELDIM» (infrastructure failure)
```

🔴 **Bu näme üçin howply?** Klient 401 görse, ol ýalňyşlyk
düşünip **tokenini täzeleýär** — ýene bir gezek hödürleýär,
ýene 401 alýar, we soňunda **ulanyjy NYKDAN çykýar**. Sebäbi
«Reset password» düwmesi işlemeýär: parol dogry, sorag ýok.
Ondaşyk kömekçi bu hadysany «Nätijede haýsy ýagdaýda bolýar?»
diýip tapyp bilmeýär.

## Failure classification

| Ýagdaý | Before (10C) | **After (10D)** |
|---|---|---|
| Token ýok | 401 | **401** |
| Token malformed | 401 | **401** |
| Token tampered | 401 | **401** |
| Token expired | 401 | **401** |
| Wrong signature | 401 | **401** |
| Wrong issuer | 401 | **401** |
| Valid token + user ýok | 401 | **401** |
| USER → admin | 403 | **403** |
| ADMIN → admin | 200 | **200** |
| **Valid token + DB outage** | ~~401~~ | **503** ✅ |
| **Public endpoint + DB outage** | ~~500~~ | **503** ✅ |
| **Admin endpoint + DB outage** | ~~401~~ | **503** ✅ |

## Ýalňyşyň kökü (root cause)

```java
// JwtAuthenticationFilter — (6)-nji ädim
User user = userRepository.findById(userId).orElse(null);
//        ↑ bu ýerde DB öçen bolsa Exception çykýar
```

Ýalňyş **filter-da** döredilýär — ol Controller-den öň geçýär.
Şonuň üçin `@RestControllerAdvice` (GlobalExceptionHandler)
oňa **ýetip bilmeýär**: logda «Servlet.service() for servlet
[dispatcherServlet] threw exception» diýip görünýär, soň
`/error` çenli ýetip, SecurityContext ýok bolany üçin
**401** döredilýärdi.

## Näme üçin 503, 500 däl?

* **500** = «программа өзи ýalňyş etdi» → ýagdaýy düzetmeli
* **503** = «программа саz, ýöne ýokary ýagdaý döredildi» →
  klient «soňra gaýtalaýyn» diýip bilýär

## 🔴 Exception classification — näme BILEN, näme BILEN DÄL

**Faýl:** `security/JwtAuthenticationFilter.java`

```java
} catch (DataAccessException | TransactionException | PersistenceException ex) {
    // → 503
} catch (JwtException | IllegalArgumentException ex) {
    // → 401 (existing behaviour, ÜÝTGEÝÄR)
}
```

🧡 **Näme üçin ÜÇ familiýa?**

| Exception | Kim ýatýar |
|---|---|
| `DataAccessException` | Spring data-access: connection, timeout, lock |
| `TransactionException` | **CannotCreateTransactionException** |
| `PersistenceException` | JPA/Hibernate |

🔴 **`TransactionException` näme üçin möhüm?** Bu fazanyň
iň möhüm tapawudy. PHASE 10C-de çykan hakyky ýalňyş şuýlady:

```text
org.springframework.transaction.CannotCreateTransactionException:
Could not open JPA EntityManager for transaction
```

Ol `DataAccessException` **EMES** — ol `TransactionException`.
Iki familiýa «garaşynda» (sibling) — ikisi-de
`NestedRuntimeException`-den gelip çykýar. Şonuň üçin diňe
`DataAccessException` diýse, hakyky outage ýagdaýy **süzülüp
geçýärdi** we faza **hiç işlemeýärdi».

⛔ **`catch (Exception e)` ÝAZYLMAÝAR.** Sebäbi onda şular hem
503 bolardy: `NullPointerException`, `IllegalStateException`,
progrämmanyň öz ýalňyşy — ýagny «ýokaryda bir ýokary ýagdaý»
diýip ýalňyş görkezilýärdi. Bu **test #12** bilen gözegçilik
edilýär.

## 503 jogaby

```json
{
  "timestamp": "2026-01-01T10:10:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Service temporarily unavailable. Please try again later.",
  "path": "/api/users/me",
  "fieldErrors": []
}
```

🔴 **Jogapda NÄME ÝOK:** SQL · `jdbc:` · DB ady · ulanyjyň
ady · parol · stack trace · `org.hibernate...` · fayl ýoly ·
JWT tókéni.

## Logging

```text
Database unavailable while authenticating request GET /api/users/me
Database unavailable while handling POST /api/auth/login
```

⛔ Logda **ýok**: tókén · `Authorization` header · parol · DB paroly.
(Subut: logda `Bearer eyJ` — **0**, `Password123!` — **0**.)

## 🔑 Hikari timeout — 3 sekunt

PHASE 10C-däki sazlamak **saklanypdyr**:

```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 3000
```

Bu **möhüm**: hökmany sazlamak bolmasy, DB öçende sorag
**30 sekunt** garaşyp, soň ýene-de 401/500 bermek bilen
gijikdirýärdi.

**Subut (çyn JAR, real öçürme):**

```text
/api/users/me  valid token, DB down = 503   (took 3027 ms)  ✅
```

## Health — 10C behaviour saklanypdyr

```bash
docker compose stop postgres
curl localhost:8080/actuator/health     # {"status":"DOWN"}  HTTP 503
docker compose start postgres
curl localhost:8080/actuator/health     # {"status":"UP"}    HTTP 200
```

## Testler

**19 täze test** (17 hemişe + 2 docker-opt-in).

| Faýl | Sany | Näme barlaýar |
|---|---|---|
| `DatabaseFailureAuthenticationTest` | 17 | 401/503 bölüşi, ýalňyş sızmaýar, programmа ýalňyşy 503 **däl** |
| `DatabaseOutageDockerTest` | 2 | Hakyky `docker compose stop/start` (opt-in) |

> 🟡 `DatabaseOutageDockerTest` diňe
> `PHASE10D_DOCKER_TESTS=true` bilen işleýär. `@AfterAll`
> **hemişe** Postgres-i gaýtadan işledýär.
> ⛔ `docker compose down -v` **hiç haçan**.

### Iki usul — näme üçin ikisi?

| Usul | Näme barlaýar |
|---|---|
| `@MockitoSpyBean` | Filter-iň **logikasyny** (her ýerde işleýär) |
| Docker | **Realistikaný** (hakyky stack-de ýüze çykýarmy?) |

🧡 «Ýalňyş ýok diýip öwrenip, hakykata işlemeýän ýagdaý» —
bu iň howply ýagdaý. Şonuň üçin ikisi hem gerek.

## Error contract

| Kod | Many | Üýtgedi mi? |
|---|---|---|
| 400 | Validation | ❌ |
| 401 | Authentication failure | ❌ |
| 403 | Role | ❌ |
| 404 | Not found | ❌ |
| 405 | Method | ❌ |
| 409 | Conflict (duplicate email) | ❌ |
| 415 | Media type | ❌ |
| 500 | Unexpected | ❌ |
| **503** | **Database unavailable** | ✅ **TÄZE** |

🔴 **409 üýtgemeýär!** `DataIntegrityViolationException`
`DataAccessException`-yň «çoky» — Spring iň ýakyn handler-i
saýlaýar, şonuň üçin **409** saklanýar (test #6, #7).

## OpenAPI

503 diňe **2 endpoint**-e goşuldy — olar hakykata DB bilen
işleşýär:

* `GET /api/users/me` (JWT filter DB soragyny edýär)
* `POST /api/auth/login`

**Ähli endpoint'lere awtomatik goşulmady** — bu gowudyr:
dokumentasiýa hakykata ýatmalydyr, ýogsam «her ýerde 503
bolýar» diýip ýalňyş ýazýarys.

---

# 🖥️ Frontend (Phase 10)

React + Vite. Backend менен **бир эле репозиторий** ичинде.

## Иштетүү

```bash
# 1) Backend көтөрүңүз
docker compose up -d
./mvnw spring-boot:run          # → http://localhost:8080

# 2) Аяктын терезесинде frontend
cd frontend
npm install                      # бир жолу
npm run dev                      # → http://localhost:5173
```

## 🧡 ПОРТ 5173 БОШ ЭМЕС болсо

Vite «Port 5173 is in use, trying another one…» деп, **өзү
**5174**'кө өтөт**. Анда браузерде 5174 ачыңыз. Бул ката эмес —
сиздин компьютериңизде башка Vite иштеп жаткан болот.

## НЕГЕ 5173 аркылуу (proxy)

```js
// vite.config.js
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080' }
  }
}
```

🧡 Браузердеги `/api` деген сурақ 5173'кө барат, Vite аны
жашыруун **тынчынча** 8080'ге жөнөтөт. Себеби мына ушул:
эгерде түз `http://localhost:8080/api` деп жазсак, браузер
**CORS** деп токтотот (эки башка порт = эки башка «сайт»).
Proxy болсо — CORS **жок**.

## Беттер

| Бет | Эмне кылат |
|---|---|
| `/login` | Кириү (токен `localStorage`ке сакталат) |
| `/register` | Катталуу → автоматты логин |
| `/` | Баланс · салуу · чыгаруу · которуу |
| `/history` | Тарых: пагинация + түр/сумма фильтрі |
| `/admin` | Колдонуучулар жана эсептер (тек `ROLE_ADMIN`) |

## 🔐 Коопсуздук

* Токен **эч качан** `console.log` кылынбайт, URL'де жазылбайт.
* **401** келсе → токен ташталып, колдонуучу автоматты чыгат.
* **404** (`/api/accounts/me`) → «эсебиң жок» деген жашыл абал,
  ката эмес. «Эсеп ачуу» баскычы чыгат.
* **503** → «серверге убакыт кетти» деген билдирүү. Бул **401
  эмес**: токен догру, бирок база жеткиликсиз (PHASE 10D).

## 🧡 Багыр — «type»ден, «amount»тен эмес

Backend сумманы **абсолют** сактат: чыгаруу үчүн да `200.0`
(тегин белгисиз). Ошондуктан:

```js
INCOMING = new Set(['DEPOSIT', 'TRANSFER_IN'])
// DEPOSIT, TRANSFER_IN   → + жашыл
// WITHDRAW, TRANSFER_OUT → − кызыл
```

⚠️ `amount > 0 ? '+' : ''` десек — **WITHDRAW «+» болуп
көрүнөт**, колдонуучу «мен салдым» деп ойлойт.

## Build

```bash
npm run build     # → dist/
npm run preview   # чыккан версияны көрүү
```
