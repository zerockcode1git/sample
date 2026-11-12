package com.example.sample.product.controller;

import com.example.sample.common.dto.PageResponse;
import com.example.sample.common.exception.ErrorResponse;
import com.example.sample.product.dto.ProductCreateRequest;
import com.example.sample.product.dto.ProductListResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 상품 REST API. 목록의 page는 1부터 시작합니다 (내부에서 JPA의 0 기반 값으로 변환). 목록은 최대 20개씩.
 */
@Tag(name = "상품", description = "상품 API")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;

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
                    - 이미지는 Supabase Storage `products/{uuid}.{확장자}`에 저장됩니다.
                    - 응답의 `img1~img3`은 **원본 파일 이름**, `upload1~upload3`은 **업로드된 파일 주소(Public URL)**입니다.
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
            @Parameter(description = "상품 이미지 0~3개 (순서대로 1~3번: img=원본 이름, upload=업로드 주소)",
                    array = @ArraySchema(schema = @Schema(type = "string", format = "binary"), maxItems = 3))
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.register(request, images));
    }

    @Operation(
            summary = "상품 단건 조회",
            description = "상품 번호로 한 건을 조회합니다. `img1~3`은 원본 파일 이름, `upload1~3`은 이미지 Public URL입니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                    @ApiResponse(responseCode = "400", description = "pno가 숫자가 아님",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples =
                            @ExampleObject(value = """
                                    {"status":400,"code":"INVALID_INPUT","message":"'pno' 값이 올바르지 않습니다. 요청값=abc"}"""))),
                    @ApiResponse(responseCode = "404", description = "상품 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples =
                            @ExampleObject(value = """
                                    {"status":404,"code":"PRODUCT_NOT_FOUND","message":"상품을 찾을 수 없습니다. pno=999999"}""")))
            })
    @GetMapping("/{pno}")
    public ProductResponse get(@Parameter(description = "상품 번호", example = "449") @PathVariable Long pno) {
        return productService.get(pno);
    }

    /**
     * 목록 + 검색.
     *
     * @param page    1부터 시작
     * @param size    1~100
     * @param keyword 상품명 또는 설명에 포함된 문자열 (선택)
     */
    @Operation(
            summary = "상품 목록 조회 + 검색",
            description = """
                    상품 목록을 페이지 단위로 조회합니다. 정렬은 최신 등록순(pno 내림차순) 고정입니다.

                    - `page`는 **1부터** 시작합니다. 1보다 작으면(0, 음수) **1로 조정**합니다. 마지막 페이지를 넘기면 `content`가 빈 배열입니다.
                    - `size`는 **최대 20**입니다. 20보다 크면 20으로, 1보다 작으면 기본값 10으로 조정합니다. 실제 적용된 값은 응답의 `page`, `size`로 확인합니다.
                    - `keyword`는 상품명 또는 상품 설명에 포함된 문자열로 검색합니다 (대소문자 무시, pg_trgm 인덱스 사용).
                    - 이미지는 원본 대신 첫 번째 이미지의 **썸네일**(최대 300px JPEG) 주소만 `thumbnail`로 내려줍니다.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "page/size가 숫자가 아님",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples =
                            @ExampleObject(value = """
                                    {"status":400,"code":"INVALID_INPUT","message":"'page' 값이 올바르지 않습니다. 요청값=abc"}""")))
            })
    @GetMapping
    public PageResponse<ProductListResponse> list(
            @Parameter(description = "페이지 번호 (1부터, 1 미만은 1로 조정)", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (최대 20, 초과 시 20)", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "검색어: 상품명 또는 설명에 포함 (선택)", example = "마우스")
            @RequestParam(required = false) String keyword) {
        // 범위를 벗어나면 오류 대신 가까운 값으로 조정 (page 음수/0 → 1, size 20 초과 → 20)
        int safePage = Math.max(page, 1);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        ProductSearchCondition condition = new ProductSearchCondition(keyword, null, null);
        return productService.list(condition, PageRequest.of(safePage - 1, safeSize));
    }

    @Operation(
            summary = "상품 수정 (이미지 교체 포함)",
            description = """
                    `multipart/form-data`로 수정 정보(`product`, JSON)와 새 이미지(`images`, 0~3개)를 보냅니다.

                    - 수정 항목: 상품명, 판매여부, 재고, 이미지. `version`은 조회 응답의 값을 그대로 보냅니다 (다르면 409).
                    - `keepImages`: 유지할 기존 이미지의 `upload` 값(URL 또는 `{uuid}.{확장자}`).
                      **생략하면 기존 이미지 모두 유지**, `[]`이면 모두 삭제.
                    - 최종 이미지 = 유지한 이미지(keepImages 순서) + 새 이미지, **최대 3개**.
                    - 처리 순서: 새 이미지 업로드 → DB 수정 → (성공 시) 유지하지 않은 기존 이미지 삭제.
                      DB 수정이 실패하면 새로 올린 이미지만 삭제되고 기존 이미지는 그대로입니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = {
                            @Encoding(name = "product", contentType = MediaType.APPLICATION_JSON_VALUE),
                            @Encoding(name = "images", contentType = "image/png, image/jpeg, image/gif, image/webp")
                    })),
            responses = {
                    @ApiResponse(responseCode = "200", description = "수정 성공",
                            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                    @ApiResponse(responseCode = "400", description = "입력값 또는 이미지 오류",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                                    @ExampleObject(name = "다른 상품의 이미지", value = """
                                            {"status":400,"code":"INVALID_INPUT","message":"이 상품의 이미지가 아닙니다. keepImages=abc.png"}"""),
                                    @ExampleObject(name = "3개 초과", value = """
                                            {"status":400,"code":"INVALID_FILE","message":"이미지는 최대 3개까지 가능합니다. 유지 2개 + 새 이미지 2개"}"""),
                                    @ExampleObject(name = "음수 재고", value = """
                                            {"status":400,"code":"INVALID_STOCK","message":"재고 수량은 0 이상이어야 합니다. 요청값=-1"}""")
                            })),
                    @ApiResponse(responseCode = "404", description = "상품 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "버전 불일치 (조회 이후 다른 변경 있음)",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples =
                            @ExampleObject(value = """
                                    {"status":409,"code":"PRODUCT_CONFLICT","message":"다른 사용자가 상품을 먼저 변경했습니다. 다시 조회한 뒤 시도해 주세요. pno=449"}"""))),
                    @ApiResponse(responseCode = "502", description = "Storage 업로드 실패",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping(value = "/{pno}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse modify(
            @Parameter(description = "상품 번호") @PathVariable Long pno,
            @Parameter(description = "수정 정보 (JSON)", required = true)
            @RequestPart("product") ProductUpdateRequest request,
            @Parameter(description = "새 이미지 0~3개 (유지 이미지 뒤에 순서대로 추가)",
                    array = @ArraySchema(schema = @Schema(type = "string", format = "binary"), maxItems = 3))
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return productService.modify(pno, request, images);
    }

    @Operation(
            summary = "상품 삭제 (업로드된 이미지 포함)",
            description = """
                    상품을 삭제하고, 업로드된 이미지(upload1~3)도 Storage에서 삭제합니다.

                    - DB 삭제를 먼저 처리하고, **성공(커밋)한 뒤에만** 이미지 파일을 삭제합니다.
                    - DB 삭제가 실패하면 이미지는 그대로 남습니다.
                    - 이미지 삭제가 실패해도 상품 삭제는 유지되며 서버 로그에 기록됩니다.
                    """,
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공 (본문 없음)"),
                    @ApiResponse(responseCode = "400", description = "pno가 숫자가 아님",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "상품 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples =
                            @ExampleObject(value = """
                                    {"status":404,"code":"PRODUCT_NOT_FOUND","message":"상품을 찾을 수 없습니다. pno=999999"}"""))),
                    @ApiResponse(responseCode = "409", description = "다른 요청이 먼저 변경함",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @DeleteMapping("/{pno}")
    public ResponseEntity<Void> remove(@Parameter(description = "상품 번호") @PathVariable Long pno) {
        productService.remove(pno);
        return ResponseEntity.noContent().build();
    }
}
