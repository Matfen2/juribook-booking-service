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
 * ⚠️ Reconstruit à partir de ma dernière version connue complète
 * + ajout de la route documents, vérifie contre ton
 * fichier réel s'il a évolué depuis sans que je le revoie (ex: routes
 * liées à un sprint que je n'aurais pas eu l'occasion de patcher
 * directement dessus).
 *
 * Routes :
 *   GET    /api/lawyers/{lawyerId}/availabilities        → public
 *   POST   /api/lawyers/{lawyerId}/availabilities        → LAWYER
 *   DELETE /api/lawyers/{lawyerId}/availabilities/{id}   → LAWYER
 *   GET    /api/lawyers/{lawyerId}/slots                  → public
 *   POST   /api/lawyers/{lawyerId}/slots                  → LAWYER
 *   DELETE /api/lawyers/{lawyerId}/slots/{id}              → LAWYER
 *   POST   /api/lawyers/{lawyerId}/slots/block             → LAWYER
 *   POST   /api/lawyers/{lawyerId}/slots/{id}/unblock      → LAWYER
 *   GET    /api/lawyers/{lawyerId}/bookings                → LAWYER (tableau de bord)
 *   POST   /api/bookings                                   → CLIENT (réserver)
 *   GET    /api/bookings                                   → CLIENT (historique)
 *   GET    /api/bookings/{id}                               → public (détail, inter-services)
 *   POST   /api/bookings/{id}/documents                     → CLIENT (upload document)
 *   PATCH  /api/bookings/{id}/confirm                      → LAWYER (confirmer)
 *   PATCH  /api/bookings/{id}/reject                       → LAWYER (refuser)
 *   PATCH  /api/bookings/{id}/cancel                       → CLIENT ou LAWYER (annuler, règle 24h)
 *   POST   /api/waitlist/{lawyerId}                        → CLIENT (s'inscrire)
 *   GET    /api/waitlist/{lawyerId}                        → public (consulter)
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
                    .requestMatchers(HttpMethod.GET, "/api/waitlist/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/bookings/*").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    // ── Routes LAWYER - disponibilités récurrentes
                    .requestMatchers(HttpMethod.POST,   "/api/lawyers/*/availabilities").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.DELETE, "/api/lawyers/*/availabilities/*").hasRole("LAWYER")
                    // ── Routes LAWYER - créneaux ponctuels et congés
                    .requestMatchers(HttpMethod.POST,   "/api/lawyers/*/slots").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.DELETE, "/api/lawyers/*/slots/*").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.POST,   "/api/lawyers/*/slots/block").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.POST,   "/api/lawyers/*/slots/*/unblock").hasRole("LAWYER")
                    // ── Route LAWYER - tableau de bord réservations
                    .requestMatchers(HttpMethod.GET, "/api/lawyers/*/bookings").hasRole("LAWYER")
                    // ── Routes CLIENT - réservation, historique, documents
                    .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("CLIENT")
                    .requestMatchers(HttpMethod.GET,  "/api/bookings").hasRole("CLIENT")
                    .requestMatchers(HttpMethod.POST, "/api/bookings/*/documents").hasRole("CLIENT")
                    // ── Routes LAWYER - confirmation/refus
                    .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/confirm").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/reject").hasRole("LAWYER")
                    // ── Route CLIENT ou LAWYER - annulation
                    .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/cancel").hasAnyRole("CLIENT", "LAWYER")
                    // ── Route CLIENT - liste d'attente
                    .requestMatchers(HttpMethod.POST, "/api/waitlist/*").hasRole("CLIENT")
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