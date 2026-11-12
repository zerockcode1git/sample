# 상품 (product)

상품 도메인의 기능 정리입니다. 테이블 정의와 인덱스는 [데이터베이스](database.md), 예외/동시성 원칙은 [아키텍처](architecture.md)를 참고하세요.

## 구성

```
product/
├── entity/       ProductEntity (tbl_product)
├── dto/          ProductCreateRequest, ProductUpdateRequest, ProductResponse, ProductSearchCondition
├── repository/   ProductRepository (+ search/ProductSearch, ProductSearchImpl: Querydsl)
├── service/      ProductService
├── controller/   ProductController (/api/products), ProductControllerAdvice
└── exception/    ProductNotFoundException, InvalidStockException, InsufficientStockException, ProductConflictException
```

공통: `common.entity.BaseEntity`(작성일/수정일), `common.dto.PageResponse`, `common.exception`(BusinessException, ErrorCode, GlobalExceptionHandler, InvalidInputException), `common.storage`(FileStorageService, SupabaseFileStorageService, ImageFileValidator, InvalidFileException, StorageException)

## ProductService 기능

클래스 기본값은 `@Transactional(readOnly = true)`이고, 변경 메서드만 `@Transactional`을 따로 붙입니다.

| 메서드 | 기능 | 파라미터 → 반환 | 발생 예외 |
| --- | --- | --- | --- |
| `register` | 상품 등록 + 이미지 업로드 | `ProductCreateRequest`, `List<MultipartFile>`(0~3개) → `ProductResponse` | `InvalidInputException`(빈 상품명, 음수 가격), `InvalidStockException`, `InvalidFileException`, `StorageException` |
| `get` | 단건 조회 | `Long pno` → `ProductResponse` | `ProductNotFoundException` |
| `list` | 검색 + 페이징 | `ProductSearchCondition`, `Pageable` → `PageResponse<ProductResponse>` | `InvalidInputException`(최소 가격 > 최대 가격) |
| `modify` | 상품명, 판매여부, 재고 수정 | `Long pno`, `ProductUpdateRequest` → `ProductResponse` | `ProductNotFoundException`, `ProductConflictException`, `InvalidStockException`, `InvalidInputException` |
| `changeStock` | 재고 증감 (양수 입고, 음수 차감) | `Long pno`, `int amount` → `ProductResponse` | `ProductNotFoundException`, `InsufficientStockException`, `ProductConflictException`(잠금 대기 3초 초과), `InvalidInputException`(amount=0) |
| `remove` | 삭제 | `Long pno` → 없음 | `ProductNotFoundException`, `ProductConflictException` |

## 등록 + 이미지 (`register`)

이미지 처리는 `common.storage`의 `FileStorageService`(인터페이스) / `SupabaseFileStorageService`(구현)에 맡깁니다.

1. 상품 정보와 모든 이미지를 먼저 검증 (`ImageFileValidator`: 이미지 형식, 확장자, 3MB 미만, 최대 3개). 하나라도 잘못되면 아무것도 올리지 않음
2. 이미지를 `products/yyyy/MM/dd/{uuid}_{원본이름}.{ext}`로 업로드 (원본 이름은 영문/숫자/`.`/`_`/`-`만 남기고 나머지는 `_`, 남는 게 없으면 `image`, 최대 100자, 확장자 소문자. Supabase가 한글 키를 거부하기 때문), 저장 경로를 img1~img3에 넣어 DB 저장
3. 트랜잭션이 커밋되지 않으면(DB 저장 실패, 업로드 도중 실패) `afterCompletion`에서 올린 이미지를 삭제

- DB에는 저장 경로만 저장하고, 응답할 때 `FileStorageService.getUrl`로 Public URL로 바꿉니다. 이미 `http(s)`로 시작하는 값은 그대로 둡니다.
- 시드 데이터(`product1_1.jpg` 등)는 Storage에 실제 파일이 없어 URL은 만들어지지만 조회하면 404입니다.

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

`ProductControllerAdvice`(ProductController 전용, 최우선 순위)가 `{status, code, message}`로 변환합니다. 다른 컨트롤러는 `GlobalExceptionHandler`가 처리합니다. API 상세는 [API 명세](api.md).

| 예외 | 상태 | code |
| --- | --- | --- |
| `InvalidInputException` | 400 | INVALID_INPUT |
| `InvalidStockException` | 400 | INVALID_STOCK |
| `ProductNotFoundException` | 404 | PRODUCT_NOT_FOUND |
| `InsufficientStockException` | 409 | INSUFFICIENT_STOCK |
| `ProductConflictException` | 409 | PRODUCT_CONFLICT |
| `InvalidFileException`, multipart 크기 초과 | 400 | INVALID_FILE |
| `StorageException` | 502 | STORAGE_ERROR |
| 타입 불일치(`/api/products/abc`), JSON 오류, DB 제약 위반 | 400 | INVALID_INPUT |
| 그 외 | 500 | INTERNAL_ERROR |

## 테스트

모두 `@SpringBootTest`로 `local` 프로필의 실제 DB에 붙어서 실행합니다.

| 클래스 | 내용 | 데이터 |
| --- | --- | --- |
| `ProductRepositoryTest` | CRUD, 검색, 페이징, 인덱스 존재 (7건) | 롤백 |
| `ProductCrudTest` | insert/select/update/delete (4건) | 롤백 |
| `ProductNameSearchTest` | 상품명 검색 (1건) | 롤백 |
| `ProductServiceTest` | 서비스 기능, 예외, 동시 차감 8건 → 5건 성공/3건 재고 부족 (12건) | 직접 삭제 (잠금 검증 위해 롤백 안 씀) |
| `ProductControllerTest` | MockMvc로 CRUD, 1 기반 페이징, 설명 검색, 에러 응답 (4건) | 롤백 |
| `ProductRegisterUploadTest` | 이미지 포함 등록, 이미지 없이 등록, 잘못된 파일 거부, DB 저장 실패 시 이미지 삭제 (4건). 실제 Storage 사용 | 직접 삭제 (DB + Storage) |
| `ProductDummyDataTest` | 상품 20건 저장 | 유지. `SEED_PRODUCTS=true`일 때만 실행 |

```bash
./gradlew test                                   # 전체 (시드 제외)
./gradlew test --tests ProductServiceTest        # 서비스만
SEED_PRODUCTS=true ./gradlew test --tests ProductDummyDataTest   # 시드 20건 추가
```
