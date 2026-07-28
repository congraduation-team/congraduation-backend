package com.example.congraduation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
		"com.example.congraduation",
		"sejong.abeek"
})
public class CongraduationApplication {

	public static void main(String[] args) {
		SpringApplication.run(CongraduationApplication.class, args);
	}

}
