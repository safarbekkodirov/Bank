package com.minibank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Mini Bank — кириш чеги (entry point).
 *
 * Бул класс:
 * 1) Spring Boot колдонмосун иштетит.
 * 2) Бул класстын папкасын (com.minibank) жана анын ичиндеги бардык
 *    @Component, @Service, @Repository, @Controller деген аннотациялуу
 *    класстарды автоматтык издөп, контекстке каттатат.
 *
 * @SpringBootApplication = @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan
 *
 * ⚠️ exclude = UserDetailsServiceAutoConfiguration
 *   Бул жөнүгө туура жана АЧЫК КАЙЛАНЫШТЫ:
 *
 *   Spring Security китепканасы коюлганда, эгер биз өзүбүздүн
 *   "колдонуучу табышту" классын бербесек, Spring ӨЗҮ демкилгиз
 *   колдонуучу жасайт:
 *       "Using generated security password: 4e5b21ce-..."
 *       колдонуучу: user / пароль: ошол рандом номур
 *
 *   БИЗДЕ БУЛ КЕРЕК ЭМЕС: биздин колдонуучулар маалымат базасында
 *   (users таблицасы), логин өзүбүз AuthService'те. Ошондуктан
 *   бул автоматтык эрежени ӨЧҮРӨБҮЗ.
 *
 *   🧠 Эмне кооптук болмок эле? Функционалдуу эмес — демкилгиз колдонуучу
 *      биздин SecurityConfig'теги чыпкалар аркылуу кире алмак эле
 *      (formLogin/httpBasic өчүрүлгөн). Бирок эгер кийин биро кемил
 *      жол кошсо, кимдир чыныгы эмес колдонуучуну киргизе алар эле.
 *      Аны ачык-ачык өчүрүү = кийин көңүл бөлүүнүн азыратат.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BankApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApplication.class, args);
    }
}
