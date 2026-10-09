package de.dhbw.tipprunde.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PingControllerTest {

	// Standalone: kein Spring-Kontext, daher keine Datenbank noetig
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PingController()).build();

	@Test
	void pingReturnsStatusOk() throws Exception {
		mockMvc.perform(get("/api/ping"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"status\":\"ok\"}"));
	}
}
