package com.example.sample.common.config;

import com.example.sample.product.controller.ProductController;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * 현재는 상품 등록(POST /api/products)만 문서에 노출합니다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Sample API")
                .version("v1")
                .description("상품 등록 API 문서. 이미지는 Supabase Storage(Public 버킷 `images`)에 저장됩니다."));
    }

    // 문서에 넣을 API만 고릅니다. 다른 API를 문서화할 때 여기 조건을 늘립니다.
    @Bean
    public GroupedOpenApi productRegisterApi() {
        return GroupedOpenApi.builder()
                .group("product-register")
                .displayName("상품 등록")
                .pathsToMatch("/api/products")
                .addOpenApiMethodFilter(method ->
                        method.getDeclaringClass() == ProductController.class && method.getName().equals("register"))
                .build();
    }
}
