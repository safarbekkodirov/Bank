package com.minibank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring Boot контекстин көтөрүү тести.
 *
 * Бул тест бизнес-логиканы текшербейт. Ал бир гана бир нерсени айтат:
 * "бардык @Configuration / @Component класстар жана конфигурациялар
 *  туура жыйылып, колдонмо катасыз иштеп баштап жатабы?"
 *
 * Ката болсо — бул тест кызыл болот, антпесе Spring чыгара жыйынтык пайда болот.
 */
@SpringBootTest
class BankApplicationTests {

    @Test
    void contextLoads() {
    }
}
