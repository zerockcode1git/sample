package com.example.sample.product.dto;

import com.example.sample.product.entity.ProductEntity;

public record ProductCreateRequest(String pname, String pdesc, int price, boolean onSale, int stock,
                                   String img1, String img2, String img3) {

    public ProductEntity toEntity() {
        return ProductEntity.builder()
                .pname(pname).pdesc(pdesc).price(price).onSale(onSale).stock(stock)
                .img1(img1).img2(img2).img3(img3)
                .build();
    }
}
