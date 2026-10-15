package com.mosbach.tipprunde.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.mosbach.tipprunde.security.AuthInterceptor;
import com.mosbach.tipprunde.security.CurrentUserIdArgumentResolver;

// KI-Log #1: CORS fuer das lokale Frontend
@Configuration
public class WebConfig implements WebMvcConfigurer {

	// Origins kommen aus app.cors.allowed-origins (kommagetrennt)
	private final String[] allowedOrigins;
	private final AuthInterceptor authInterceptor;

	public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins, AuthInterceptor authInterceptor) {
		this.allowedOrigins = allowedOrigins;
		this.authInterceptor = authInterceptor;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("Authorization", "Content-Type")
				.maxAge(3600);
	}

	// Token-Prüfung für alle Endpunkte unter /api, die Ausnahmen stehen im AuthInterceptor
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
	}

	// @CurrentUserId in Controller-Methoden
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new CurrentUserIdArgumentResolver());
	}
}
