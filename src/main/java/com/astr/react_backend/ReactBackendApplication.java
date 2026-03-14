package com.astr.react_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ReactBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReactBackendApplication.class, args);
	}

}
