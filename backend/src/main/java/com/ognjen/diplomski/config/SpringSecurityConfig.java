package com.ognjen.diplomski.config;

import com.ognjen.diplomski.auth.ApiErrorWriter;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static com.ognjen.diplomski.security.Permissions.*;

@Configuration
@EnableMethodSecurity
public class SpringSecurityConfig {

    @Bean
    UserDetailsService userDetailsService(AppUserRepository userRepository) {
        return username -> userRepository.findByEmailIgnoreCase(username)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiErrorWriter errorWriter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/error", "/api/health", "/products/**").permitAll()
                        .requestMatchers("/api/auth/register").denyAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/alerts").authenticated()
                        .requestMatchers("/api/ai/admin/**").hasRole("CEO")
                        .requestMatchers("/api/ai/**").access(org.springframework.security.authorization.AuthorizationManagers.allOf(
                                org.springframework.security.authorization.AuthorityAuthorizationManager.hasAnyRole("SALES_REP", "MANAGER", "CEO"),
                                org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority(CUSTOMERS_READ)))
                        .requestMatchers(HttpMethod.GET, "/api/customers/**").hasAuthority(CUSTOMERS_READ)
                        .requestMatchers(HttpMethod.POST, "/api/customers/**").hasRole("SALES_REP")
                        .requestMatchers(HttpMethod.PUT, "/api/customers/**").hasRole("SALES_REP")
                        .requestMatchers(HttpMethod.PATCH, "/api/customers/**").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/requisitions/**").hasAuthority(REQUISITIONS_READ)
                        .requestMatchers(HttpMethod.POST, "/api/requisitions/*/email").hasRole("BOOKKEEPER")
                        .requestMatchers(HttpMethod.POST, "/api/requisitions/**").hasRole("SALES_REP")
                        .requestMatchers(HttpMethod.PUT, "/api/requisitions/**").hasAnyRole("SALES_REP", "MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/requisitions/**").hasAnyRole("SALES_REP", "MANAGER", "BOOKKEEPER")
                        .requestMatchers(HttpMethod.GET, "/api/products/available").hasAnyRole("SALES_REP", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/products").hasAnyRole("CEO", "BOOKKEEPER")
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("CEO")
                        .requestMatchers(HttpMethod.PATCH, "/api/products/*/deactivate").hasRole("CEO")
                        .requestMatchers(HttpMethod.GET, "/api/sales-reports/**").hasAuthority(SALES_REPORTS_READ)
                        .requestMatchers(HttpMethod.GET, "/api/goods-requests/**").hasAuthority(GOODS_REQUESTS_READ)
                        .requestMatchers(HttpMethod.POST, "/api/goods-requests/**").hasRole("SALES_REP")
                        .requestMatchers(HttpMethod.PATCH, "/api/goods-requests/*/status").hasRole("BOOKKEEPER")
                        .requestMatchers(HttpMethod.GET, "/api/advertising-materials/available").hasAuthority(ADVERTISING_MATERIALS_READ)
                        .requestMatchers(HttpMethod.GET, "/api/advertising-materials/distributions").hasAuthority(ADVERTISING_MATERIALS_READ)
                        .requestMatchers(HttpMethod.GET, "/api/advertising-materials").hasRole("CEO")
                        .requestMatchers(HttpMethod.POST, "/api/advertising-materials/distributions").hasRole("SALES_REP")
                        .requestMatchers(HttpMethod.POST, "/api/advertising-materials").hasRole("CEO")
                        .requestMatchers(HttpMethod.PATCH, "/api/advertising-materials/*/active").hasRole("CEO")
                        .requestMatchers(HttpMethod.GET, "/api/employees/**").hasAuthority(EMPLOYEES_READ)
                        .requestMatchers(HttpMethod.POST, "/api/employees/**").hasRole("CEO")
                        .requestMatchers(HttpMethod.PATCH, "/api/employees/**").hasRole("CEO")
                        .requestMatchers(HttpMethod.GET, "/api/finance/**").hasAuthority(FINANCE_READ)
                        .requestMatchers(HttpMethod.GET, "/api/production/**").hasAuthority(PRODUCTION_READ)
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                        "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "Access is denied")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

}
