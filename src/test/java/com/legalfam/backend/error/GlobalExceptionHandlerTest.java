package com.legalfam.backend.error;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.error.exception.InvalidRequestException;
import com.legalfam.backend.error.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsInvalidRequestToStandardError() throws Exception {
        mockMvc.perform(get("/errors/invalid-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Missing required field")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/errors/invalid-request")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void mapsMalformedJsonToStandardError() throws Exception {
        mockMvc.perform(post("/errors/malformed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("malformed_json")))
                .andExpect(jsonPath("$.message", is("Malformed request body")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/errors/malformed")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @RestController
    private static class ThrowingController {
        @org.springframework.web.bind.annotation.GetMapping("/errors/invalid-request")
        String invalidRequest() {
            throw new InvalidRequestException("Missing required field");
        }

        @PostMapping("/errors/malformed")
        String malformed(@RequestBody DummyBody body) {
            return body.value();
        }
    }

    private record DummyBody(String value) {
    }
}
