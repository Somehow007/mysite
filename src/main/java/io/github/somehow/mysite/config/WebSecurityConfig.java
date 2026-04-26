package io.github.somehow.mysite.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.security.CustomUserDetailsService;
import io.github.somehow.mysite.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.disable())
                .userDetailsService(customUserDetailsService)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/v1/auth/login",
                                "/v1/auth/register",
                                "/v1/auth/refresh",
                                "/v1/articles",
                                "/v1/articles/archive",
                                "/v1/articles/{id:\\d+}"
                        ).permitAll()
                        .requestMatchers(
                                "/v1/categories/query",
                                "/v1/categories/tree",
                                "/v1/categories/id/*",
                                "/v1/categories/*",
                                "/v1/tags/**",
                                "/v1/search/**",
                                "/v1/site/**",
                                "/doc.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/favicon.ico"
                        ).permitAll()
                        .requestMatchers(
                                "/v1/categories/*/children"
                        ).authenticated()
                        .requestMatchers(
                                "/v1/categories"
                        ).hasRole("DEVELOPER")
                        .requestMatchers(
                                "/v1/categories/batch/**"
                        ).hasRole("DEVELOPER")
                        .requestMatchers(
                                "/v1/categories/*/status"
                        ).hasRole("DEVELOPER")
                        .requestMatchers(
                                "/v1/categories/sort"
                        ).hasRole("DEVELOPER")
                        .requestMatchers(
                                "/v1/admin/users/**"
                        ).hasRole("DEVELOPER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(401);
                            Result<Void> result = new Result<Void>()
                                    .setCode("A000001")
                                    .setMessage("未登录或Token已过期");
                            objectMapper.writeValue(response.getWriter(), result);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(403);
                            Result<Void> result = new Result<Void>()
                                    .setCode("A000003")
                                    .setMessage("权限不足，无法访问该资源");
                            objectMapper.writeValue(response.getWriter(), result);
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
