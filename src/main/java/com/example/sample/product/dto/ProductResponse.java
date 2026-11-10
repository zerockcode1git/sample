package com.example.sample.product.dto;

import com.example.sample.product.entity.ProductEntity;

import java.time.LocalDateTime;

public record ProductResponse(Long pno, String pname, String pdesc, int price, boolean onSale, int stock,
                              String img1, String img2, String img3, long version,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static ProductResponse from(ProductEntity e) {
        return new ProductResponse(e.getPno(), e.getPname(), e.getPdesc(), e.getPrice(), e.isOnSale(),
                e.getStock(), e.getImg1(), e.getImg2(), e.getImg3(), e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
