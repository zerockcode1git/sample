package com.example.sample.common.config;

import com.example.sample.product.controller.ProductController;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * 현재는 상품 등록, 조회(목록/단건), 수정, 삭제를 문서에 노출합니다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Sample API")
                .version("v1")
                .description("상품 등록/조회/수정/삭제 API 문서. 이미지는 Supabase Storage(Public 버킷 `images`)에 저장됩니다."));
    }

    // 문서에 넣을 ProductController 메서드. 다른 API를 문서화할 때 여기에 추가합니다.
    private static final Set<String> DOCUMENTED_METHODS = Set.of("register", "get", "list", "modify", "remove");

    @Bean
    public GroupedOpenApi productApi() {
        return GroupedOpenApi.builder()
                .group("product")
                .displayName("상품 (등록/조회/수정/삭제)")
                .pathsToMatch("/api/products", "/api/products/*")
                .addOpenApiMethodFilter(method ->
                        method.getDeclaringClass() == ProductController.class && DOCUMENTED_METHODS.contains(method.getName()))
                .build();
    }
}
