package com.example.sample.product.dto;

import com.example.sample.product.entity.ProductEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * 목록 한 줄. 원본 이미지 대신 첫 번째 이미지의 썸네일(최대 300px JPEG) 주소만 담아 응답을 가볍게 합니다.
 * 원본 이미지가 필요하면 단건 조회를 사용합니다.
 */
@Schema(description = "상품 목록 항목")
public record ProductListResponse(
        @Schema(description = "상품 번호", example = "449") Long pno,
        @Schema(description = "상품명", example = "무선 마우스") String pname,
        @Schema(description = "상품 설명", example = "2.4GHz 무선, 저소음 클릭") String pdesc,
        @Schema(description = "가격", example = "25000") int price,
        @Schema(description = "판매 여부", example = "true") boolean onSale,
        @Schema(description = "재고 수량", example = "10") int stock,
        @Schema(description = "첫 번째 이미지의 썸네일 URL (이미지가 없으면 null)",
                example = "https://pltejotxjzhsdqleqzmj.supabase.co/storage/v1/object/public/images/products/thumbs/28d00fd0-c6ee-453f-8e6c-0584d806f8e7.jpg")
        String thumbnail,
        @Schema(description = "이미지 개수 (0~3)", example = "2") int imageCount,
        @Schema(description = "등록일시") LocalDateTime createdAt) {

    /** @param thumbnailUrl 원본 파일 ID → 썸네일 URL */
    public static ProductListResponse from(ProductEntity e, Function<String, String> thumbnailUrl) {
        var images = e.getImages();
        String thumbnail = images.isEmpty() ? null : thumbnailUrl.apply(images.getFirst().fileId());
        return new ProductListResponse(e.getPno(), e.getPname(), e.getPdesc(), e.getPrice(), e.isOnSale(), e.getStock(),
                thumbnail, images.size(), e.getCreatedAt());
    }
}
