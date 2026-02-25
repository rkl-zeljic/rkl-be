package com.rkl.backend.filter

import com.rkl.backend.config.RklConfig
import com.rkl.backend.repository.JwtKeyCacheRepository
import com.rkl.backend.service.user.UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.http.HttpMethod
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val securityConfig: RklConfig.Security,
    private val userService: UserService,
    private val jwtKeyCacheRepository: JwtKeyCacheRepository,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        // We use .cors { } which automatically looks for a bean named 'corsConfigurationSource'
        return if (!securityConfig.enabled) {
            http
                .cors { }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .csrf { it.disable() }
                .formLogin { it.disable() }
                .build()
        } else {
            http
                .cors { }
                .csrf { it.disable() }
                .authorizeHttpRequests {
                    securityConfig.permitSpecificEndpoints.forEach { entry ->
                        val httpMethod = HttpMethod.valueOf(entry.method.uppercase())
                        it.requestMatchers(httpMethod, entry.path).permitAll()
                    }
                    it.requestMatchers(*securityConfig.permitAllEndpoints.toTypedArray()).permitAll()
                        .anyRequest().authenticated()
                }
                .addFilterBefore(
                    JwtAuthFilter(securityConfig, userService, jwtKeyCacheRepository),
                    UsernamePasswordAuthenticationFilter::class.java
                )
                .formLogin { it.disable() }
                .build()
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()

        if (securityConfig.corsEnabled) {
            // Production/Strict Mode
            config.allowedOrigins = securityConfig.allowedOrigins
            config.allowCredentials = true
        } else {
            // "Disabled" / Local Dev Mode (Allow Everything)
            config.allowedOrigins = listOf("*")
            config.allowCredentials = false // Note: Credentials must be false if origin is "*"
        }

        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        config.allowedHeaders = listOf("*")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}