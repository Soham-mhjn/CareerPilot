package com.careerpilot.config;
import com.careerpilot.repository.UserRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByEmailIgnoreCase(username).map(u -> User.withUsername(u.email).password(u.passwordHash).roles("USER").build()).orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${careerpilot.cors.allowed-origins:http://localhost:5173}") String origins) throws Exception {
        http.cors(cors -> cors.configurationSource(req -> {
            CorsConfiguration c = new CorsConfiguration();
            c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList());
            c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            c.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
            c.setAllowCredentials(true);
            return c;
        }));
        http.csrf(csrf -> csrf.csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler()).ignoringRequestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll().requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll().anyRequest().authenticated());
        http.formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.logoutUrl("/api/v1/auth/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)).invalidateHttpSession(true).deleteCookies("JSESSIONID"));
        http.exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> res.sendError(401, "Authentication required")));
        return http.build();
    }
}







