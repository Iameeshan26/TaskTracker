package com.tasktracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tasktracker.repository.UserDirectory;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDirectory users(PasswordEncoder e) {
    return new UserDirectory(
        List.of(
            User.withUsername("admin")
                .password(e.encode("Admin-demo-2026!"))
                .roles("ADMIN")
                .build(),
            User.withUsername("manager")
                .password(e.encode("Manager-demo-2026!"))
                .roles("MANAGER")
                .build(),
            User.withUsername("alice").password(e.encode("Alice-demo-2026!")).roles("USER").build(),
            User.withUsername("bob").password(e.encode("Bob-demo-2026!")).roles("USER").build()));
  }

  @Bean
  AuthenticationManager manager(UserDetailsService users, PasswordEncoder e) {
    var p = new org.springframework.security.authentication.dao.DaoAuthenticationProvider(users);
    p.setPasswordEncoder(e);
    return new ProviderManager(p);
  }

  @Bean
  HttpSessionSecurityContextRepository contextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  public static void error(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    new ObjectMapper()
        .writeValue(response.getOutputStream(), Map.of("error", code, "message", message));
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http, HttpSessionSecurityContextRepository repository)
      throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/login", "/api/csrf", "/error")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .securityContext(c -> c.securityContextRepository(repository))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (q, r, x) -> error(r, 401, "UNAUTHORIZED", "Please sign in"))
                    .accessDeniedHandler(
                        (q, r, x) -> {
                          var a = SecurityContextHolder.getContext().getAuthentication();
                          boolean anonymous =
                              a == null
                                  || a
                                      instanceof
                                      org.springframework.security.authentication
                                          .AnonymousAuthenticationToken;
                          error(
                              r,
                              anonymous ? 401 : 403,
                              anonymous ? "UNAUTHORIZED" : "FORBIDDEN",
                              anonymous ? "Please sign in" : "Invalid or missing CSRF token");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((q, r, a) -> r.setStatus(204)))
        .build();
  }
}
