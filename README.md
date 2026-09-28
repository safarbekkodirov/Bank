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

## Учурдагы абал: **PHASE 3B — Login + JWT**

Эми иштеп жатат: чыныгы REST коопсуздугу. Логин → JWT токен → токен менен
корголгон эндпоинт `/api/users/me`.

Азыр жок: Account, Transaction, акча операциялары, Admin, Frontend.

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

## Командалар

```bash
docker compose up -d      # PostgreSQL
./mvnw spring-boot:run    # http://localhost:8080
./mvnw test               # 40 тест
./mvnw clean package
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
| 4 | Accounts | ⬜ |
| 5 | Deposit / Withdraw / Transfer | ⬜ |
| 6 | Transaction History | ⬜ |
| 7 | Admin | ⬜ |
| 8 | Frontend | ⬜ |
| 9 | Testing | ⬜ |
| 10 | README / Docs | ⬜ |
