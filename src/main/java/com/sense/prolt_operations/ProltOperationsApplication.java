package com.sense.prolt_operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
public class ProltOperationsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProltOperationsApplication.class, args);
	}

}
