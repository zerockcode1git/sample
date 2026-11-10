# 데이터베이스

- DBMS: PostgreSQL (Supabase, Session pooler)
- ORM: Spring Data JPA (Hibernate)

## 스키마 관리

| 환경 | 방식 |
| --- | --- |
| local | `ddl-auto: update`로 자동 반영 |
| 운영 | `ddl-auto: validate`. 스키마 변경은 마이그레이션 도구(Flyway 등) 도입 후 스크립트로 관리할 예정 |

## 테이블 목록

### `tbl_product` (`product.entity.ProductEntity`)

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| pno | BIGINT | PK, IDENTITY | 상품 번호 |
| pname | VARCHAR(200) | NOT NULL | 상품명 (수정 가능) |
| pdesc | VARCHAR(2000) | | 상품 설명 |
| price | INT | NOT NULL | 상품 가격 |
| on_sale | BOOLEAN | NOT NULL | 판매 여부 (수정 가능) |
| stock | INT | NOT NULL | 재고 수량 (수정 가능) |
| img1, img2, img3 | VARCHAR(500) | | 상품 이미지 |
| version | BIGINT | NOT NULL, DEFAULT 0 | 낙관적 잠금 버전 (`@Version`) |
| created_at | TIMESTAMP | NOT NULL | 작성일 (자동) |
| updated_at | TIMESTAMP | NOT NULL | 수정일 (자동) |

인덱스

| 이름 | 대상 | 방식 | 용도 |
| --- | --- | --- | --- |
| idx_product_price | price | B-tree (`@Index`) | 가격대 검색 |
| idx_product_pname_trgm | lower(pname) | GIN `gin_trgm_ops` | 상품명 부분 검색 |
| idx_product_pdesc_trgm | lower(pdesc) | GIN `gin_trgm_ops` | 설명 부분 검색 |

trigram 인덱스는 JPA로 선언할 수 없어 `src/main/resources/db/product-index.sql`(pg_trgm 확장 + 인덱스)을 Hibernate 테이블 생성 후 실행합니다 (`spring.sql.init`, `defer-datasource-initialization`). 운영에서는 마이그레이션 스크립트로 옮겨야 합니다.

작성일, 수정일은 `common.entity.BaseEntity`와 JPA Auditing(`common.config.JpaAuditingConfig`)이 자동으로 채웁니다.

## 엔티티 작성 규칙

- 테이블과 컬럼 이름은 `snake_case`를 씁니다. Spring Boot 기본 네이밍 전략이 camelCase 필드를 자동으로 변환합니다.
- 기본키는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 씁니다.
- 연관관계는 기본으로 `FetchType.LAZY`를 씁니다.
- Lombok `@Setter`, `@Data`는 쓰지 않고 `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 씁니다.
