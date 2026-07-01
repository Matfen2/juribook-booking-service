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
 * Routes :
 *   GET    /api/lawyers/{lawyerId}/availabilities        → public (consulter les dispos)
 *   POST   /api/lawyers/{lawyerId}/availabilities        → LAWYER (déclarer ses dispos)
 *   DELETE /api/lawyers/{lawyerId}/availabilities/{id}   → LAWYER (désactiver)
 *   GET    /api/lawyers/{lawyerId}/slots                  → public (consulter les créneaux)
 *   POST   /api/lawyers/{lawyerId}/slots                  → LAWYER (créneau ponctuel)
 *   DELETE /api/lawyers/{lawyerId}/slots/{id}              → LAWYER (supprimer)
 *   POST   /api/lawyers/{lawyerId}/slots/block             → LAWYER (bloquer une période)
 *   POST   /api/lawyers/{lawyerId}/slots/{id}/unblock      → LAWYER (débloquer)
 *   POST   /api/bookings                                   → CLIENT
 *   PATCH  /api/bookings/{id}/confirm                      → LAWYER
 *   PATCH  /api/bookings/{id}/reject                       → LAWYER
 *   GET    /actuator/health, /swagger-ui/**                → public
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
                    .requestMatchers(HttpMethod.GET, "/api/lawyers/*/availabilities").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/lawyers/*/slots").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    // ── Routes LAWYER — disponibilités récurrentes
                    .requestMatchers(HttpMethod.POST,   "/api/lawyers/*/availabilities").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.DELETE, "/api/lawyers/*/availabilities/*").hasRole("LAWYER")
                    // ── Routes LAWYER — créneaux ponctuels et congés (Sprint 3.3)
                    .requestMatchers(HttpMethod.POST,   "/api/lawyers/*/slots").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.DELETE, "/api/lawyers/*/slots/*").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.POST,   "/api/lawyers/*/slots/block").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.POST,   "/api/lawyers/*/slots/*/unblock").hasRole("LAWYER")
                    // ── Routes CLIENT — réservation
                    .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("CLIENT")
                    // ── Routes LAWYER — confirmation/refus (Sprint 4.4)
                    .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/confirm").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/reject").hasRole("LAWYER")
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
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}