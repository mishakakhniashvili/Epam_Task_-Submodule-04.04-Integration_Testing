package com.epam.trainerworkload.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((
                                request,
                                response,
                                exception
                        ) -> {
                            log.warn(
                                    "Operation failed: status=401, message=Authentication is required"
                            );
                            response.sendError(
                                    HttpServletResponse.SC_UNAUTHORIZED,
                                    "Authentication is required"
                            );
                        })
                        .accessDeniedHandler((
                                request,
                                response,
                                exception
                        ) -> {
                            log.warn(
                                    "Operation failed: status=403, message=Required scope is missing"
                            );
                            response.sendError(
                                    HttpServletResponse.SC_FORBIDDEN,
                                    "Required scope is missing"
                            );
                        })
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                )
                .build();
    }
}
