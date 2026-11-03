package com.mosbach.tipprunde.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// KI-Log MA-02 "Baue das Spring-Boot-Grundgerüst mit DB-Verbindung über Umgebungsvariablen, CORS und /api/ping."
@RestController
@RequestMapping("/api")
public class PingController {

	@GetMapping("/ping")
	public Map<String, String> ping() {
		return Map.of("status", "ok");
	}
}
