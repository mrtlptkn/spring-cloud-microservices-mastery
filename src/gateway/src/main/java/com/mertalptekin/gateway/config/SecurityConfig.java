package com.mertalptekin.gateway.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {

        // Converter tipleri: Jwt -> AbstractAuthenticationToken
        Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter = jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // 1️⃣ realm_access.roles kontrolü
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) realmAccess.get("roles");
                authorities.addAll(
                        roles.stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList())
                );
            }

            return new JwtAuthenticationToken(jwt, authorities);
        };

        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthConverter);
    }


    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        http.authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/product-service/**")
                        .authenticated() // product service istek atmak için sadece authenticated olmak yeterli

                        // Order Service routelarına gelecek isteklerde access token lazım ve admin olmak lazım.
                        .pathMatchers("/order-service/**").permitAll()
//                        .hasAuthority("microservices-admin")
//                        .anyExchange()
//                        .authenticated()
                ).oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter()))
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }



}
