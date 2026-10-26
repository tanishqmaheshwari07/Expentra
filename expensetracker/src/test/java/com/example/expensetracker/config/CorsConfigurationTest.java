package com.example.expensetracker.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsConfigurationTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class DefaultCorsConfigTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Default CORS allows localhost preflight")
        void preflight_DefaultLocalhost_Allowed() throws Exception {
            mockMvc.perform(options("/api/users/login")
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "app.cors.allowed-origins=https://app.example.com"
    })
    class CustomCorsConfigTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("Configured origin passes preflight")
        void preflight_ConfiguredOrigin_Allowed() throws Exception {
            mockMvc.perform(options("/api/users/login")
                            .header("Origin", "https://app.example.com")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
        }

        @Test
        @DisplayName("Non-configured origin is rejected")
        void preflight_UnconfiguredOrigin_Rejected() throws Exception {
            mockMvc.perform(options("/api/users/login")
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
        }
    }
}
