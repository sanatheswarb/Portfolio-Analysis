package com.cursor_springa_ai.playground.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI portfolioAnalysisOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Portfolio Analysis API")
                        .description("AI-powered Indian equity portfolio analysis using Zerodha and Ollama LLM")
                        .version("1.0.0"));
    }
}
