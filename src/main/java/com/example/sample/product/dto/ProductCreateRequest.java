package com.example.sample.product.dto;

import com.example.sample.product.entity.ProductEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 상품 등록 요청 (multipart의 {@code product} 파트, JSON).
 * 이미지는 {@code images} 파트로 따로 받아 저장소에 올리고, 저장 경로를 img1~img3에 넣습니다.
 */
@Schema(description = "상품 등록 정보")
public record ProductCreateRequest(
        @Schema(description = "상품명 (필수, 200자 이하)", example = "무선 마우스", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        String pname,
        @Schema(description = "상품 설명 (2000자 이하)", example = "2.4GHz 무선, 저소음 클릭", maxLength = 2000)
        String pdesc,
        @Schema(description = "가격 (0 이상)", example = "25000", minimum = "0")
        int price,
        @Schema(description = "판매 여부", example = "true")
        boolean onSale,
        @Schema(description = "재고 수량 (0 이상)", example = "10", minimum = "0")
        int stock) {

    /** @param imagePaths 업로드된 이미지의 저장 경로 (0~3개, 순서대로 img1~img3) */
    public ProductEntity toEntity(List<String> imagePaths) {
        return ProductEntity.builder()
                .pname(pname).pdesc(pdesc).price(price).onSale(onSale).stock(stock)
                .img1(pathAt(imagePaths, 0)).img2(pathAt(imagePaths, 1)).img3(pathAt(imagePaths, 2))
                .build();
    }

    private static String pathAt(List<String> paths, int index) {
        return index < paths.size() ? paths.get(index) : null;
    }
}
