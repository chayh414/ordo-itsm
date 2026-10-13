package com.ordo.itsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ItsmApplication {

	public static void main(String[] args) {
		SpringApplication.run(ItsmApplication.class, args);
	}

}
