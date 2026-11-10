# 상품 (product)

상품 도메인의 기능 정리입니다. 테이블 정의와 인덱스는 [데이터베이스](database.md), 예외/동시성 원칙은 [아키텍처](architecture.md)를 참고하세요.

## 구성

```
product/
├── entity/       ProductEntity (tbl_product)
├── dto/          ProductCreateRequest, ProductUpdateRequest, ProductResponse, ProductSearchCondition
├── repository/   ProductRepository (+ search/ProductSearch, ProductSearchImpl: Querydsl)
├── service/      ProductService
└── exception/    ProductNotFoundException, InvalidStockException, InsufficientStockException, ProductConflictException
```

공통: `common.entity.BaseEntity`(작성일/수정일), `common.dto.PageResponse`, `common.exception`(BusinessException, ErrorCode, GlobalExceptionHandler, InvalidInputException)

## ProductService 기능

클래스 기본값은 `@Transactional(readOnly = true)`이고, 변경 메서드만 `@Transactional`을 따로 붙입니다.

| 메서드 | 기능 | 파라미터 → 반환 | 발생 예외 |
| --- | --- | --- | --- |
| `register` | 상품 등록 | `ProductCreateRequest` → `ProductResponse` | `InvalidInputException`(빈 상품명, 음수 가격), `InvalidStockException` |
| `get` | 단건 조회 | `Long pno` → `ProductResponse` | `ProductNotFoundException` |
| `list` | 검색 + 페이징 | `ProductSearchCondition`, `Pageable` → `PageResponse<ProductResponse>` | `InvalidInputException`(최소 가격 > 최대 가격) |
| `modify` | 상품명, 판매여부, 재고 수정 | `Long pno`, `ProductUpdateRequest` → `ProductResponse` | `ProductNotFoundException`, `ProductConflictException`, `InvalidStockException`, `InvalidInputException` |
| `changeStock` | 재고 증감 (양수 입고, 음수 차감) | `Long pno`, `int amount` → `ProductResponse` | `ProductNotFoundException`, `InsufficientStockException`, `ProductConflictException`(잠금 대기 3초 초과), `InvalidInputException`(amount=0) |
| `remove` | 삭제 | `Long pno` → 없음 | `ProductNotFoundException`, `ProductConflictException` |

## 검색 (`list`)

- 조건(모두 선택): `keyword`(상품명 또는 설명에 포함, 대소문자 무시), `minPrice`(이상), `maxPrice`(이하)
- 정렬 허용 필드: `pno`, `pname`, `price`, `createdAt`. 그 외는 무시, 기본 `pno desc`
- count 쿼리는 필요할 때만 실행 (`PageableExecutionUtils`)
- `REPEATABLE_READ`로 목록과 전체 건수를 같은 스냅샷에서 읽음
- 인덱스: `lower(pname)`, `lower(pdesc)` pg_trgm GIN, `price` B-tree

## 동시성

| 상황 | 처리 |
| --- | --- |
| 같은 상품 재고를 동시에 증감 | 비관적 잠금 `findByIdForUpdate` → `SELECT ... FOR NO KEY UPDATE`, `lock_timeout` 3초. 요청이 순서대로 반영되고, 결과가 음수면 `InsufficientStockException` |
| 조회한 뒤 수정하기 전에 다른 변경 발생 | 낙관적 잠금 `@Version`. `ProductUpdateRequest.version`이 현재 버전과 다르면 `ProductConflictException`. 확인 후 커밋 직전에 끼어든 변경도 `flush`에서 잡아 같은 예외로 변환 |

재고 수정은 두 가지입니다.

- `modify`: 재고를 지정한 값으로 설정 (관리자 수정). 낙관적 잠금
- `changeStock`: 현재 재고에서 증감 (주문, 입고, 취소). 비관적 잠금. 동시 요청에도 값이 덮어써지지 않음

## 예외 → 응답

`GlobalExceptionHandler`가 `{status, code, message}`로 변환합니다.

| 예외 | 상태 | code |
| --- | --- | --- |
| `InvalidInputException` | 400 | INVALID_INPUT |
| `InvalidStockException` | 400 | INVALID_STOCK |
| `ProductNotFoundException` | 404 | PRODUCT_NOT_FOUND |
| `InsufficientStockException` | 409 | INSUFFICIENT_STOCK |
| `ProductConflictException` | 409 | PRODUCT_CONFLICT |

## 테스트

모두 `@SpringBootTest`로 `local` 프로필의 실제 DB에 붙어서 실행합니다.

| 클래스 | 내용 | 데이터 |
| --- | --- | --- |
| `ProductRepositoryTest` | CRUD, 검색, 페이징, 인덱스 존재 (7건) | 롤백 |
| `ProductCrudTest` | insert/select/update/delete (4건) | 롤백 |
| `ProductNameSearchTest` | 상품명 검색 (1건) | 롤백 |
| `ProductServiceTest` | 서비스 기능, 예외, 동시 차감 8건 → 5건 성공/3건 재고 부족 (12건) | 직접 삭제 (잠금 검증 위해 롤백 안 씀) |
| `ProductDummyDataTest` | 상품 20건 저장 | 유지. `SEED_PRODUCTS=true`일 때만 실행 |

```bash
./gradlew test                                   # 전체 (시드 제외)
./gradlew test --tests ProductServiceTest        # 서비스만
SEED_PRODUCTS=true ./gradlew test --tests ProductDummyDataTest   # 시드 20건 추가
```
