package com.mtsaas.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URI;
import java.net.URISyntaxException;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class MtSaasApplication {

	public static void main(String[] args) {
		String databaseUrl = System.getenv("DATABASE_URL");
		if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
			try {
				URI dbUri = new URI(databaseUrl);
				if (dbUri.getUserInfo() != null) {
					String[] credentials = dbUri.getUserInfo().split(":");
					System.setProperty("spring.datasource.username", credentials[0]);
					if (credentials.length > 1) {
						System.setProperty("spring.datasource.password", credentials[1]);
					}
				}
				String jdbcUrl = "jdbc:postgresql://" + dbUri.getHost() + ':' + dbUri.getPort() + dbUri.getPath();
				
				if (!jdbcUrl.contains("sslmode=")) {
					jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
				}
				
				System.setProperty("spring.datasource.url", jdbcUrl);
				System.out.println("✓ Auto-corrected DATABASE_URL and extracted credentials for JDBC.");
			} catch (URISyntaxException e) {
				System.err.println("❌ Failed to parse DATABASE_URL: " + e.getMessage());
			}
		}
		SpringApplication.run(MtSaasApplication.class, args);
	}

}
