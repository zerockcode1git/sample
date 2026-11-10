package com.example.sample.product.dto;

/**
 * 상품 목록 검색 조건. 모든 값은 선택이며 null이면 해당 조건을 무시합니다.
 *
 * @param keyword  상품명 또는 상품 설명에 포함된 문자열 (대소문자 무시)
 * @param minPrice 최소 가격 (이상)
 * @param maxPrice 최대 가격 (이하)
 */
public record ProductSearchCondition(String keyword, Integer minPrice, Integer maxPrice) {
}
