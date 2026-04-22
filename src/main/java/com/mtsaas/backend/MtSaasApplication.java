package com.mtsaas.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class MtSaasApplication {

	public static void main(String[] args) {
		String databaseUrl = System.getenv("DATABASE_URL");
		if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
			String jdbcUrl = databaseUrl.replaceFirst("^(postgres|postgresql)://", "jdbc:postgresql://");
			System.setProperty("spring.datasource.url", jdbcUrl);
		}
		SpringApplication.run(MtSaasApplication.class, args);
	}

}
