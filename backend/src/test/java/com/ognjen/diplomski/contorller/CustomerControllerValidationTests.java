package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.auth.ApiExceptionHandler;
import com.ognjen.diplomski.dto.CustomerRequest;
import com.ognjen.diplomski.service.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CustomerControllerValidationTests {

    private CustomerService service;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CustomerService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = standaloneSetup(new CustomerController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void createAndEditRejectTheSameMalformedPib() throws Exception {
        String payload = payload("Object", "Company", "12345A789");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("PIB mora sadržati tačno 9 cifara."));

        mockMvc.perform(put("/api/customers/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("PIB mora sadržati tačno 9 cifara."));

        verifyNoInteractions(service);
    }

    @Test
    void createAndEditAcceptNamesAtTheMaximumLength() throws Exception {
        String objectName = "O".repeat(300);
        String companyName = "C".repeat(300);
        String payload = payload(objectName, companyName, "123456789");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/customers/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(service).create(eq(null), any(CustomerRequest.class));
        verify(service).update(eq(null), eq(10L), any(CustomerRequest.class));
    }

    private static String payload(String objectName, String companyName, String pib) {
        return """
                {
                  "objectName": "%s",
                  "companyName": "%s",
                  "pib": "%s",
                  "phone": "",
                  "email": "",
                  "address": "Address",
                  "googleMapsUrl": "",
                  "note": ""
                }
                """.formatted(objectName, companyName, pib);
    }

    private static HandlerMethodArgumentResolver authenticationPrincipalResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(
                        org.springframework.security.core.annotation.AuthenticationPrincipal.class
                );
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer modelAndViewContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return null;
            }
        };
    }
}
