package com.banking.notificatonservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class NotificatonServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificatonServiceApplication.class, args);
	}

}
