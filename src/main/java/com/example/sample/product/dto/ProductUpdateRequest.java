package com.example.sample.product.dto;

/**
 * 상품 수정 요청. 수정 가능 항목은 상품명, 판매여부, 재고수량뿐입니다.
 *
 * @param version 클라이언트가 조회했던 시점의 버전. 그 사이 다른 변경이 있었다면 수정이 거부됩니다.
 */
public record ProductUpdateRequest(String pname, boolean onSale, int stock, long version) {
}
