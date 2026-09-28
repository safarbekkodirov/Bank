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
| JUnit 5 | (Spring Boot Test менен биге келед) |

## Учурдагы абал: **PHASE 1 — Project Skeleton**

Эми иштеп жаткан нерсе — бош Spring Boot колдонмо. Азыр жок:
Authentication, Database, Entity, бизнес-логика, Frontend.

## Иштетүү

Maven системасы орнотулган болушу талап кылынбайт. Долбоор `./mvnw` колдонот.

```bash
./mvnw spring-boot:run
```

Колдонмо иштегенде: **http://localhost:8080**

## Тесттерди иштетүү

```bash
./mvnw test
```

## Толук build (jar файл)

```bash
./mvnw clean package
```

Натыйжа: `target/minibank-0.0.1-SNAPSHOT.jar`

```bash
java -jar target/minibank-0.0.1-SNAPSHOT.jar
```

## Файлдардын структурасы

```
src/main/java/com/minibank/
└── BankApplication.java      ← кириш чеги (@SpringBootApplication)

src/main/resources/
└── application.yml           ← конфигурация (порт, аты)

src/test/java/com/minibank/
└── BankApplicationTests.java ← Spring контекстин текшерүүчү тест
```

## Этаптар

| PHASE | Мазмуну | Статус |
|---|---|---|
| 0 | Чөйрөнү текшерүү | ✅ |
| 1 | Project Skeleton | ✅ |
| 2 | PostgreSQL + Flyway + таблицалар | ⬜ |
| 3 | Registration / Login | ⬜ |
| 4 | Accounts | ⬜ |
| 5 | Deposit / Withdraw / Transfer | ⬜ |
| 6 | Transaction History | ⬜ |
| 7 | Admin | ⬜ |
| 8 | Frontend | ⬜ |
| 9 | Testing | ⬜ |
| 10 | README / Docs | ⬜ |
