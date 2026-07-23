package io.github.somehow.mysite.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
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
import org.springframework.http.HttpMethod;

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
                .securityContext(securityContext -> securityContext.requireExplicitSave(false))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/v1/auth/login",
                                "/v1/auth/register",
                                "/v1/auth/refresh"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v1/articles",
                                "/v1/articles/archive"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v1/articles/{id}"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v1/categories",
                                "/v1/categories/query",
                                "/v1/categories/tree",
                                "/v1/categories/id/*",
                                "/v1/categories/{slug}"
                        ).permitAll()
                        .requestMatchers(
                                "/v1/tags/**",
                                "/v1/search/**",
                                "/v1/site/**",
                                "/actuator/**",
                                "/doc.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/favicon.ico"
                        ).permitAll()
                        .requestMatchers(
                                "/uploads/**"
                        ).permitAll()
                        // RAG AI 聊天：允许匿名提问（visitorId 归属 + 限流保护）
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v1/rag/chat/stream"
                        ).permitAll()
                        // RAG 对话历史：需登录
                        .requestMatchers(
                                "/v1/rag/conversations/**"
                        ).authenticated()
                        // RAG 知识库列表：允许匿名浏览（博客读者可见知识库）
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v1/rag/knowledge-bases",
                                "/v1/rag/knowledge-bases/{id}"
                        ).permitAll()
                        // RAG 知识库管理：仅 ADMIN
                        .requestMatchers(
                                "/v1/rag/knowledge-bases/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/v1/categories/*/children"
                        ).authenticated()
                        // 文章管理：ADMIN + CREATOR 可创建/编辑/删除
                        .requestMatchers(
                                HttpMethod.POST, "/v1/articles"
                        ).hasAnyRole("ADMIN", "CREATOR")
                        .requestMatchers(
                                HttpMethod.PUT, "/v1/articles/**"
                        ).hasAnyRole("ADMIN", "CREATOR")
                        .requestMatchers(
                                HttpMethod.DELETE, "/v1/articles/**"
                        ).hasAnyRole("ADMIN", "CREATOR")
                        // 分类管理：仅 ADMIN
                        .requestMatchers(
                                HttpMethod.POST, "/v1/categories"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.PUT, "/v1/categories/{id}"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.DELETE, "/v1/categories/{id}"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/v1/categories/batch/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/v1/categories/*/status"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/v1/categories/sort"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/v1/admin/users/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v1/comments/article/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/v1/comments"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/v1/comments/*"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/v1/comments/*/like"
                        ).permitAll()
                        .requestMatchers(
                                "/v1/admin/comments/**"
                        ).hasRole("ADMIN")
                        // 合集公开读接口：允许未登录读者浏览合集和文章导航
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v1/collections",
                                "/v1/collections/{id}"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/v1/articles/{id}/navigation"
                        ).permitAll()
                        // 合集管理接口：仅 ADMIN 角色可操作
                        .requestMatchers(
                                HttpMethod.POST, "/v1/collections"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.PUT, "/v1/collections/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.DELETE, "/v1/collections/**"
                        ).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(401);
                            Result<Void> result = new Result<Void>()
                                    .setCode(ErrorCode.SECURITY_NOT_AUTHENTICATED.code())
                                    .setMessage(ErrorCode.SECURITY_NOT_AUTHENTICATED.message());
                            objectMapper.writeValue(response.getWriter(), result);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(403);
                            Result<Void> result = new Result<Void>()
                                    .setCode(ErrorCode.SECURITY_ACCESS_DENIED.code())
                                    .setMessage(ErrorCode.SECURITY_ACCESS_DENIED.message());
                            objectMapper.writeValue(response.getWriter(), result);
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
