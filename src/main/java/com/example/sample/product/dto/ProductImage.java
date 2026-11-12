package com.example.sample.product.dto;

/**
 * 업로드된 상품 이미지 한 개.
 *
 * @param originalName 원본 파일 이름 → img1~img3
 * @param fileId       저장소에 업로드된 파일 ID "{uuid}.{확장자}" → upload1~upload3
 */
public record ProductImage(String originalName, String fileId) {
}
