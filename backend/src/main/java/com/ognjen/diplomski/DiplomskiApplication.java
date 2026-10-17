package com.ognjen.diplomski;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class DiplomskiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DiplomskiApplication.class, args);
	}
}
