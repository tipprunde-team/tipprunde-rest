package com.mosbach.tipprunde.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

// Standalone-Test mit Hilfs-Controller, daher keine Datenbank noetig
class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void apiExceptionUsesGivenStatusCodeAndMessage() throws Exception {
		mockMvc.perform(get("/test/conflict"))
				.andExpect(status().isConflict())
				.andExpect(content().json("{\"error\":{\"code\":\"EMAIL_TAKEN\",\"message\":\"E-Mail ist schon vergeben.\"}}"));
	}

	@Test
	void invalidBodyReturnsValidationError() throws Exception {
		mockMvc.perform(post("/test/echo").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.message").value("Name fehlt."));
	}

	@Test
	void brokenJsonReturnsValidationError() throws Exception {
		mockMvc.perform(post("/test/echo").contentType(MediaType.APPLICATION_JSON).content("{kaputt"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void emptyBodyReturnsValidationError() throws Exception {
		mockMvc.perform(post("/test/echo").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unknownPathReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/gibtsnicht"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void wrongMethodReturns405() throws Exception {
		mockMvc.perform(post("/test/conflict"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void unexpectedErrorHidesDetails() throws Exception {
		mockMvc.perform(get("/test/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("geheim"))));
	}

	@RestController
	@RequestMapping("/test")
	static class TestController {

		record EchoRequest(@NotBlank(message = "Name fehlt.") String name) {
		}

		@GetMapping("/conflict")
		String conflict() {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "E-Mail ist schon vergeben.");
		}

		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("geheimes Interna");
		}

		@PostMapping("/echo")
		String echo(@Valid @RequestBody EchoRequest request) {
			return request.name();
		}
	}
}
