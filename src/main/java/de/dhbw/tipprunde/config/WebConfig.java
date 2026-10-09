package de.dhbw.tipprunde.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// KI-Log #1: CORS fuer das lokale Frontend
@Configuration
public class WebConfig implements WebMvcConfigurer {

	// Origins kommen aus app.cors.allowed-origins (kommagetrennt)
	private final String[] allowedOrigins;

	public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("Authorization", "Content-Type")
				.maxAge(3600);
	}
}
