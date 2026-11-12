package com.example.sample.product.controller;

import com.example.sample.common.dto.PageResponse;
import com.example.sample.common.exception.ErrorResponse;
import com.example.sample.common.exception.InvalidInputException;
import com.example.sample.product.dto.ProductCreateRequest;
import com.example.sample.product.dto.ProductResponse;
import com.example.sample.product.dto.ProductSearchCondition;
import com.example.sample.product.dto.ProductUpdateRequest;
import com.example.sample.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 상품 REST API. 목록의 page는 1부터 시작합니다 (내부에서 JPA의 0 기반 값으로 변환).
 */
@Tag(name = "상품", description = "상품 API")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;

    /**
     * 상품 등록 (multipart/form-data).
     *
     * @param request {@code product} 파트: 상품 정보 JSON (Content-Type: application/json)
     * @param images  {@code images} 파트: 이미지 0~3개, 각 3MB 미만, jpg/jpeg/png/gif/webp
     */
    @Operation(
            summary = "상품 등록 (이미지 0~3개 포함)",
            description = """
                    `multipart/form-data`로 상품 정보(`product`, JSON)와 이미지(`images`, 0~3개)를 함께 보냅니다.

                    - 이미지: jpg/jpeg/png/gif/webp, 각 **3MB 미만**, 최대 3개 (요청 전체 10MB 이하)
                    - 이미지는 Supabase Storage `products/yyyy/MM/dd/{uuid}_{원본이름}.{ext}`에 저장되고, 응답의 img1~img3은 Public URL입니다.
                    - 검증은 업로드 전에 모두 끝나므로, 잘못된 파일이 하나라도 있으면 아무것도 저장되지 않습니다.
                    - DB 저장이 실패하면 이미 올린 이미지는 삭제됩니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = {
                            @Encoding(name = "product", contentType = MediaType.APPLICATION_JSON_VALUE),
                            @Encoding(name = "images", contentType = "image/png, image/jpeg, image/gif, image/webp")
                    })),
            responses = {
                    @ApiResponse(responseCode = "201", description = "등록 성공",
                            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                    @ApiResponse(responseCode = "400", description = "입력값 또는 파일 오류",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                                    @ExampleObject(name = "빈 상품명", value = """
                                            {"status":400,"code":"INVALID_INPUT","message":"상품명은 비어 있을 수 없습니다."}"""),
                                    @ExampleObject(name = "음수 재고", value = """
                                            {"status":400,"code":"INVALID_STOCK","message":"재고 수량은 0 이상이어야 합니다. 요청값=-1"}"""),
                                    @ExampleObject(name = "이미지가 아님", value = """
                                            {"status":400,"code":"INVALID_FILE","message":"이미지 파일만 업로드할 수 있습니다. 파일=memo.txt, 형식=text/plain"}"""),
                                    @ExampleObject(name = "3MB 이상", value = """
                                            {"status":400,"code":"INVALID_FILE","message":"이미지는 3MB 미만이어야 합니다. 파일=big.png, 크기=3145728 bytes"}"""),
                                    @ExampleObject(name = "4개 이상", value = """
                                            {"status":400,"code":"INVALID_FILE","message":"이미지는 최대 3개까지 업로드할 수 있습니다. 요청=4개"}""")
                            })),
                    @ApiResponse(responseCode = "502", description = "Storage 업로드 실패",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples =
                            @ExampleObject(value = """
                                    {"status":502,"code":"STORAGE_ERROR","message":"파일 업로드에 실패했습니다. 파일=front.png"}""")))
            })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> register(
            @Parameter(description = "상품 정보 (JSON)", required = true)
            @RequestPart("product") ProductCreateRequest request,
            @Parameter(description = "상품 이미지 0~3개 (순서대로 img1~img3)",
                    array = @ArraySchema(schema = @Schema(type = "string", format = "binary"), maxItems = 3))
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.register(request, images));
    }

    @GetMapping("/{pno}")
    public ProductResponse get(@PathVariable Long pno) {
        return productService.get(pno);
    }

    /**
     * 목록 + 검색.
     *
     * @param page    1부터 시작
     * @param size    1~100
     * @param keyword 상품명 또는 설명에 포함된 문자열 (선택)
     */
    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String keyword) {
        if (page < 1) {
            throw new InvalidInputException("page는 1 이상이어야 합니다. 요청값=" + page);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidInputException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다. 요청값=" + size);
        }
        ProductSearchCondition condition = new ProductSearchCondition(keyword, null, null);
        return productService.list(condition, PageRequest.of(page - 1, size));
    }

    @PutMapping("/{pno}")
    public ProductResponse modify(@PathVariable Long pno, @RequestBody ProductUpdateRequest request) {
        return productService.modify(pno, request);
    }

    @DeleteMapping("/{pno}")
    public ResponseEntity<Void> remove(@PathVariable Long pno) {
        productService.remove(pno);
        return ResponseEntity.noContent().build();
    }
}
