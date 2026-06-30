package juribook.booking_service.config;

import juribook.booking_service.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration Spring Security du booking-service.
 *
 * CORS intégré directement dans SecurityFilterChain via .cors(),
 * pas de bean CorsFilter séparé. Un CorsFilter externe entre en
 * conflit avec SecurityFilterChain dans Spring Boot 4 et empêche
 * son chargement (leçon tirée du lawyer-service et de l'auth-service).
 *
 * Routes prévues (affinées au fil des sprints 3.2+) :
 *   GET  /api/availabilities/lawyer/{lawyerId}  → public (consulter les dispos)
 *   GET  /api/slots/lawyer/{lawyerId}           → public (consulter les créneaux)
 *   POST /api/availabilities                    → LAWYER (déclarer ses dispos)
 *   POST /api/bookings                          → CLIENT (réserver — Sprint 3.2)
 *   GET  /actuator/health, /swagger-ui/**       → public
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                    // ── Routes publiques ────────────────────────
                    .requestMatchers(HttpMethod.GET, "/api/availabilities/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/slots/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    // ── Routes LAWYER — gestion de ses disponibilités
                    .requestMatchers(HttpMethod.POST, "/api/availabilities").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.PUT,  "/api/availabilities/**").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.DELETE, "/api/availabilities/**").hasRole("LAWYER")
                    // ── Routes CLIENT — réservation (Sprint 3.2)
                    .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("CLIENT")
                    // ── Tout le reste → authentification requise
                    .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}