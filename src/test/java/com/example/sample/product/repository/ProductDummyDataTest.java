package com.example.sample.product.repository;

import com.example.sample.product.entity.ProductEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 상품 20건을 실제 DB에 저장합니다 (롤백 없음).
 * 실행할 때마다 20건씩 추가되므로 환경 변수를 줄 때만 실행됩니다.
 * <pre>SEED_PRODUCTS=true ./gradlew test --tests ProductDummyDataTest</pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SEED_PRODUCTS", matches = "true")
class ProductDummyDataTest {

    @Autowired
    ProductRepository productRepository;

    @Test
    void insertDummyProducts() {
        String[][] items = {
                {"무선 마우스", "저소음 버튼과 2.4GHz 무선 연결을 지원하는 마우스"},
                {"기계식 키보드", "갈축 스위치를 사용한 텐키리스 기계식 키보드"},
                {"27인치 모니터", "QHD 해상도와 IPS 패널의 27인치 모니터"},
                {"노트북 거치대", "알루미늄 소재의 각도 조절형 노트북 거치대"},
                {"USB-C 허브", "HDMI, USB 3.0, SD 카드 슬롯을 갖춘 7in1 허브"},
                {"블루투스 이어폰", "노이즈 캔슬링을 지원하는 무선 이어폰"},
                {"웹캠", "1080p 해상도와 자동 초점을 지원하는 웹캠"},
                {"게이밍 헤드셋", "7.1 가상 서라운드를 지원하는 유선 헤드셋"},
                {"외장 SSD 1TB", "USB-C 연결, 최대 1050MB/s 전송 속도"},
                {"무선 충전기", "최대 15W 고속 무선 충전 패드"},
                {"스마트 워치", "심박수 측정과 GPS를 지원하는 스마트 워치"},
                {"태블릿 케이스", "자석 부착식 태블릿 보호 케이스"},
                {"보조배터리 20000mAh", "PD 고속 충전을 지원하는 대용량 보조배터리"},
                {"마우스 패드", "책상 전체를 덮는 대형 마우스 패드"},
                {"모니터 암", "가스 스프링 방식의 싱글 모니터 암"},
                {"LED 스탠드", "밝기와 색온도 조절이 가능한 책상용 LED 스탠드"},
                {"HDMI 케이블 2m", "4K 60Hz를 지원하는 HDMI 2.0 케이블"},
                {"노트북 파우치", "15인치 노트북용 방수 파우치"},
                {"블루투스 스피커", "방수 기능을 갖춘 휴대용 블루투스 스피커"},
                {"키보드 손목 받침대", "메모리폼 소재의 키보드 손목 받침대"},
        };

        List<ProductEntity> products = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            int no = i + 1;
            products.add(ProductEntity.builder()
                    .pname(items[i][0])
                    .pdesc(items[i][1])
                    .price(10_000 * no)          // 10,000 ~ 200,000
                    .onSale(no % 5 != 0)         // 5, 10, 15, 20번은 판매 중지
                    .stock(no * 3)
                    .img1("product" + no + "_1.jpg")
                    .build());
        }
        productRepository.saveAll(products);
    }
}
