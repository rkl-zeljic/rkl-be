package com.rkl.backend.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:5173",
                "http://localhost:3000",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:3000",
                "https://rklzeljic.com",
                "https://rkl-fe.livelysmoke-ef1fb120.westeurope.azurecontainerapps.io"
            )
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
