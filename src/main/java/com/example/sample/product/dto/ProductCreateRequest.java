package com.example.sample.product.dto;

import com.example.sample.product.entity.ProductEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 상품 등록 요청 (multipart의 {@code product} 파트, JSON).
 * 이미지는 {@code images} 파트로 따로 받아 저장소에 올리고, 원본 이름은 img1~img3, 파일 UUID는 upload1~upload3에 넣습니다.
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

    /** @param images 업로드된 이미지 (0~3개, 순서대로 1~3번) */
    public ProductEntity toEntity(List<ProductImage> images) {
        return ProductEntity.builder()
                .pname(pname).pdesc(pdesc).price(price).onSale(onSale).stock(stock)
                .img1(nameAt(images, 0)).img2(nameAt(images, 1)).img3(nameAt(images, 2))
                .upload1(fileIdAt(images, 0)).upload2(fileIdAt(images, 1)).upload3(fileIdAt(images, 2))
                .build();
    }

    private static String nameAt(List<ProductImage> images, int index) {
        return index < images.size() ? images.get(index).originalName() : null;
    }

    private static String fileIdAt(List<ProductImage> images, int index) {
        return index < images.size() ? images.get(index).fileId() : null;
    }
}
