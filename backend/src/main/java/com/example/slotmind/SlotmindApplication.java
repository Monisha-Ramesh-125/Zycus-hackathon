package com.example.slotmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@EnableResilientMethods
@SpringBootApplication
public class SlotmindApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlotmindApplication.class, args);
	}

}
