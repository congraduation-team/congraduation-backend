package com.example.congraduation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
		"com.example.congraduation",
		"sejong.abeek"
})
@EntityScan(basePackages = {
		"com.example.congraduation",
		"sejong.abeek"
})
@EnableJpaRepositories(basePackages = {
		"com.example.congraduation",
		"sejong.abeek"
})
public class CongraduationApplication {

	public static void main(String[] args) {
		SpringApplication.run(CongraduationApplication.class, args);
	}

}
