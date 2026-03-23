package com.project.nulldown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NulldownApplication {

	public static void main(String[] args) {
		SpringApplication.run(NulldownApplication.class, args);
	}

}
