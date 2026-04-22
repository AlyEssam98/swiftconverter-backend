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
			// Add sslmode=require if not already present in the URL
			if (!jdbcUrl.contains("sslmode=")) {
				jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
			}
			// Force set both properties to ensure Spring picks up the corrected one
			System.setProperty("spring.datasource.url", jdbcUrl);
			System.setProperty("DATABASE_URL", jdbcUrl); 
			System.out.println("✓ Auto-corrected DATABASE_URL to JDBC format with SSL: " + jdbcUrl);
		}
		SpringApplication.run(MtSaasApplication.class, args);
	}

}
