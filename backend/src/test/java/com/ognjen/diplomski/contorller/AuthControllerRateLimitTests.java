package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.auth.ApiExceptionHandler;
import com.ognjen.diplomski.security.LoginRateLimiter;
import com.ognjen.diplomski.service.impl.AuthServiceImpl;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerRateLimitTests {

    private AuthServiceImpl authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthServiceImpl.class);
        LoginRateLimiter limiter = new LoginRateLimiter(Duration.ofMinutes(15), 2, 100, 100);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, limiter))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returns429AndDoesNotTrustClientSuppliedForwardedAddresses() throws Exception {
        performLogin("198.51.100.1").andExpect(status().isOk());
        performLogin("198.51.100.2").andExpect(status().isOk());

        performLogin("198.51.100.3")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));

        verify(authService, times(2)).login(any());
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(String forwardedAddress)
            throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr("192.0.2.10");
                    return request;
                })
                .header("X-Forwarded-For", forwardedAddress)
                .contentType("application/json")
                .content("{\"email\":\"person@example.com\",\"password\":\"password\"}"));
    }
}
