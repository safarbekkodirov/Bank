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

## Учурдагы абал: **PHASE 2 — Database Foundation**

Эми иштеп жатат: PostgreSQL 16 (Docker'да) + Flyway миграциялары + 3 таблица.

Азыр жок: Java Entity, Repository, Service, Controller, Authentication, бизнес-логика, Frontend.

---

# PHASE 2 — Database

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
| 3 | Registration / Login | ⬜ |
| 4 | Accounts | ⬜ |
| 5 | Deposit / Withdraw / Transfer | ⬜ |
| 6 | Transaction History | ⬜ |
| 7 | Admin | ⬜ |
| 8 | Frontend | ⬜ |
| 9 | Testing | ⬜ |
| 10 | README / Docs | ⬜ |
