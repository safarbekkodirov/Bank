package com.minibank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 */
@SpringBootApplication
public class BankApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApplication.class, args);
    }
}
