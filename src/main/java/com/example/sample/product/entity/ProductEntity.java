package com.example.sample.product.entity;

import com.example.sample.common.entity.BaseEntity;
import com.example.sample.product.dto.ProductImage;
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

import java.util.ArrayList;
import java.util.List;

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

    // 원본 이미지 파일 이름 (사용자가 올린 이름 그대로, 예: "앞면 사진(1).jpg")
    @Column(length = 500)
    private String img1;

    @Column(length = 500)
    private String img2;

    @Column(length = 500)
    private String img3;

    // 업로드된 파일 ID "{uuid}.{확장자}". Storage 키는 "products/{uuid}.{확장자}", 응답에서는 Public URL로 변환
    @Column(length = 500)
    private String upload1;

    @Column(length = 500)
    private String upload2;

    @Column(length = 500)
    private String upload3;

    // 낙관적 잠금용 버전. 기존 행은 default 0으로 채워집니다.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Builder
    private ProductEntity(String pname, String pdesc, int price, boolean onSale, int stock,
                          String img1, String img2, String img3,
                          String upload1, String upload2, String upload3) {
        this.pname = pname;
        validateStock(stock);
        this.pdesc = pdesc;
        this.price = price;
        this.onSale = onSale;
        this.stock = stock;
        this.img1 = img1;
        this.img2 = img2;
        this.img3 = img3;
        this.upload1 = upload1;
        this.upload2 = upload2;
        this.upload3 = upload3;
    }

    // 수정 가능 항목: 상품명, 판매여부, 재고수량, 이미지
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

    /**
     * 이미지 교체. 순서대로 1~3번에 넣고 남는 칸은 비웁니다.
     *
     * @param images (원본 이름, 파일 ID) 0~3개
     */
    public void changeImages(List<ProductImage> images) {
        ProductImage[] slot = new ProductImage[3];
        for (int i = 0; i < Math.min(images.size(), 3); i++) {
            slot[i] = images.get(i);
        }
        this.img1 = slot[0] == null ? null : slot[0].originalName();
        this.upload1 = slot[0] == null ? null : slot[0].fileId();
        this.img2 = slot[1] == null ? null : slot[1].originalName();
        this.upload2 = slot[1] == null ? null : slot[1].fileId();
        this.img3 = slot[2] == null ? null : slot[2].originalName();
        this.upload3 = slot[2] == null ? null : slot[2].fileId();
    }

    /** 현재 이미지 (원본 이름, 파일 ID). 업로드 파일이 있는 칸만, 순서대로 */
    public List<ProductImage> getImages() {
        List<ProductImage> images = new ArrayList<>();
        if (upload1 != null) images.add(new ProductImage(img1, upload1));
        if (upload2 != null) images.add(new ProductImage(img2, upload2));
        if (upload3 != null) images.add(new ProductImage(img3, upload3));
        return images;
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
