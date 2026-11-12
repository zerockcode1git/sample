package com.example.sample.product.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 상품 수정 요청 (multipart의 {@code product} 파트, JSON). 수정 가능 항목은 상품명, 판매여부, 재고수량, 이미지입니다.
 *
 * @param version    클라이언트가 조회했던 시점의 버전. 그 사이 다른 변경이 있었다면 수정이 거부됩니다.
 * @param keepImages 유지할 기존 이미지 (upload 값: 파일 ID 또는 URL). null이면 기존 이미지를 모두 유지,
 *                   빈 배열이면 모두 삭제. 최종 이미지 = 유지한 이미지(이 순서) + 새로 올린 이미지, 최대 3개
 */
@Schema(description = "상품 수정 정보")
public record ProductUpdateRequest(
        @Schema(description = "상품명 (필수, 200자 이하)", example = "무선 마우스 V2", requiredMode = Schema.RequiredMode.REQUIRED)
        String pname,
        @Schema(description = "판매 여부", example = "true")
        boolean onSale,
        @Schema(description = "재고 수량 (0 이상)", example = "7")
        int stock,
        @Schema(description = "조회했던 시점의 버전 (조회 응답의 version)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        long version,
        @ArraySchema(arraySchema = @Schema(description = """
                유지할 기존 이미지. 조회 응답의 upload 값(URL) 또는 파일 ID({uuid}.{확장자}).
                생략(null)하면 기존 이미지 모두 유지, []이면 모두 삭제. 목록에 없는 기존 이미지는 수정 후 삭제됩니다."""),
                schema = @Schema(example = "28d00fd0-c6ee-453f-8e6c-0584d806f8e7.png"))
        List<String> keepImages) {

    /** 이미지는 그대로 두고 상품 정보만 수정 */
    public ProductUpdateRequest(String pname, boolean onSale, int stock, long version) {
        this(pname, onSale, stock, version, null);
    }
}
