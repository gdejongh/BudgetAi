package com.budget.budgetai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BudgetaiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BudgetaiApplication.class, args);
	}

}
