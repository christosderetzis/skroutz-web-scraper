package org.skroutz.scraper.skroutzwebscraper.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig{

    @Autowired
    private JwtConverter jwtConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/scraper/**", "/jobs/**", "/category-schemas/**").hasRole("SUPER_ADMIN")
                        .anyRequest().permitAll())
                .securityMatcher("/scraper/**", "/jobs/**", "/category-schemas")
                .oauth2ResourceServer(
                        (oauth2)-> oauth2.jwt(
                                jwt-> jwt.jwtAuthenticationConverter(jwtConverter)
                        ))
                .sessionManagement(
                        session-> session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }
}