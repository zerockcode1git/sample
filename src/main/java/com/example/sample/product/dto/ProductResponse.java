package com.example.sample.product.dto;

import com.example.sample.product.entity.ProductEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.function.Function;

@Schema(description = "상품 정보")
public record ProductResponse(
        @Schema(description = "상품 번호", example = "52") Long pno,
        @Schema(description = "상품명", example = "무선 마우스") String pname,
        @Schema(description = "상품 설명", example = "2.4GHz 무선, 저소음 클릭") String pdesc,
        @Schema(description = "가격", example = "25000") int price,
        @Schema(description = "판매 여부", example = "true") boolean onSale,
        @Schema(description = "재고 수량", example = "10") int stock,
        @Schema(description = "이미지 1 Public URL (없으면 null)",
                example = "https://pltejotxjzhsdqleqzmj.supabase.co/storage/v1/object/public/images/products/2026/11/12/71dd496a-7935-425b-aa32-507952eb1d22.png")
        String img1,
        @Schema(description = "이미지 2 Public URL (없으면 null)") String img2,
        @Schema(description = "이미지 3 Public URL (없으면 null)") String img3,
        @Schema(description = "버전 (수정 시 그대로 보냄)", example = "0") long version,
        @Schema(description = "등록일시") LocalDateTime createdAt,
        @Schema(description = "수정일시") LocalDateTime updatedAt) {

    /**
     * @param imageUrl 저장 경로 → 조회 URL 변환 (DB에는 경로만 저장되어 있음)
     */
    public static ProductResponse from(ProductEntity e, Function<String, String> imageUrl) {
        return new ProductResponse(e.getPno(), e.getPname(), e.getPdesc(), e.getPrice(), e.isOnSale(),
                e.getStock(), imageUrl.apply(e.getImg1()), imageUrl.apply(e.getImg2()), imageUrl.apply(e.getImg3()),
                e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
