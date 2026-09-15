package com.campuslab.bookings.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configura este microservicio como Resource Server de OAuth2: valida el JWT emitido
 * por el Identity Provider (ej. Keycloak / ms-auth) contra el issuer configurado en
 * application.yml (spring.security.oauth2.resourceserver.jwt.issuer-uri) y traduce
 * el claim de roles del token en GrantedAuthority con prefijo ROLE_, que es lo que
 * @PreAuthorize("hasAnyRole(...)") espera.
 */
@Configuration
@EnableMethodSecurity // habilita @PreAuthorize en los controladores/servicios
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless consumida por otros servicios/SPAs con JWT
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/actuator/health")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/actuator/health/**")).permitAll()
                        // Si algun indicador de salud reporta DOWN, /actuator/health
                        // responde con status != 2xx y Tomcat reenvia internamente a
                        // /error para renderizarlo; ese reenvio es una peticion nueva
                        // que vuelve a pasar por este filtro. Sin permitirla tambien,
                        // cae en anyRequest().authenticated() y el cliente recibe un
                        // 401 que oculta el verdadero error (ej. 503).
                        .requestMatchers(new AntPathRequestMatcher("/error")).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Traduce el claim "roles" del JWT (ej: ["ADMIN", "TECNICO"]) a authorities
     * "ROLE_ADMIN", "ROLE_TECNICO", requerido por hasAnyRole(...).
     * Ajustar el nombre del claim ("roles") segun lo que efectivamente emita el
     * Identity Provider real (puede venir anidado, ej. realm_access.roles en Keycloak).
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> combinarAuthorities(jwt, authoritiesConverter));
        return converter;
    }

    private Collection<GrantedAuthority> combinarAuthorities(Jwt jwt, JwtGrantedAuthoritiesConverter base) {
        Collection<GrantedAuthority> authorities = base.convert(jwt);
        if (authorities == null || authorities.isEmpty()) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                return roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList());
            }
        }
        return authorities;
    }
}
