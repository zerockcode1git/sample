package com.example.sample.product.entity;

import com.example.sample.common.entity.BaseEntity;
import com.example.sample.product.exception.InsufficientStockException;
import com.example.sample.product.exception.InvalidStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tbl_product", indexes = {
        @Index(name = "idx_product_price", columnList = "price")
})
public class ProductEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pno;

    @Column(nullable = false, length = 200)
    private String pname;

    @Column(length = 2000)
    private String pdesc;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private boolean onSale;

    @Column(nullable = false)
    private int stock;

    @Column(length = 500)
    private String img1;

    @Column(length = 500)
    private String img2;

    @Column(length = 500)
    private String img3;

    // 낙관적 잠금용 버전. 기존 행은 default 0으로 채워집니다.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Builder
    private ProductEntity(String pname, String pdesc, int price, boolean onSale, int stock,
                          String img1, String img2, String img3) {
        this.pname = pname;
        validateStock(stock);
        this.pdesc = pdesc;
        this.price = price;
        this.onSale = onSale;
        this.stock = stock;
        this.img1 = img1;
        this.img2 = img2;
        this.img3 = img3;
    }

    // 수정 가능 항목: 상품명, 판매여부, 재고수량
    public void changePname(String pname) {
        this.pname = pname;
    }

    public void changeOnSale(boolean onSale) {
        this.onSale = onSale;
    }

    public void changeStock(int stock) {
        validateStock(stock);
        this.stock = stock;
    }

    /** 재고 증감. 결과가 음수면 예외를 던지고 값은 바뀌지 않습니다. */
    public void adjustStock(int amount) {
        int result = this.stock + amount;
        if (result < 0) {
            throw new InsufficientStockException(this.pno, this.stock, amount);
        }
        this.stock = result;
    }

    private static void validateStock(int stock) {
        if (stock < 0) {
            throw new InvalidStockException(stock);
        }
    }
}
