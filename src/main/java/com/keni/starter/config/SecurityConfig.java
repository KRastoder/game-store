
package com.keni.starter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

//IMPORTANT FOR NOW WE ARE DISABLING AUTH FEATURES LATER WE WILL ADD THEM

@EnableWebSecurity
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csfr -> csfr.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable());

    return http.build();

  }

}
