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
| `modify` | 상품명, 판매여부, 재고, 이미지 수정 | `Long pno`, `ProductUpdateRequest`, `List<MultipartFile>` → `ProductResponse` | `ProductNotFoundException`, `ProductConflictException`, `InvalidStockException`, `InvalidInputException` |
| `changeStock` | 재고 증감 (양수 입고, 음수 차감) | `Long pno`, `int amount` → `ProductResponse` | `ProductNotFoundException`, `InsufficientStockException`, `ProductConflictException`(잠금 대기 3초 초과), `InvalidInputException`(amount=0) |
| `remove` | 삭제 + 이미지 삭제 | `Long pno` → 없음 | `ProductNotFoundException`, `ProductConflictException` |

## 등록 + 이미지 (`register`)

이미지 처리는 `common.storage`의 `FileStorageService`(인터페이스) / `SupabaseFileStorageService`(구현)에 맡깁니다.

1. 상품 정보와 모든 이미지를 먼저 검증 (`ImageFileValidator`: 이미지 형식, 확장자, 3MB 미만, 최대 3개). 하나라도 잘못되면 아무것도 올리지 않음
2. 이미지를 `products/{uuid}`로 업로드 (확장자 없음, 형식은 업로드 시 Content-Type으로 저장). 원본 파일 이름은 img1~img3, UUID는 upload1~upload3에 넣어 DB 저장
3. 트랜잭션이 커밋되지 않으면(DB 저장 실패, 업로드 도중 실패) `afterCompletion`에서 올린 이미지를 삭제

- `img1~3`: 원본 파일 이름 그대로 (한글 가능). `upload1~3`: UUID만 저장하고, 응답할 때 `FileStorageService.getUrl("products", uuid)`로 Public URL로 바꿉니다. 이미 `http(s)`로 시작하는 값은 그대로 둡니다.
- 시드 데이터는 `img1`에 `product1_1.jpg` 같은 이름만 있고 `upload1~3`은 비어 있습니다.

## 수정 + 이미지 교체 (`modify`)

1. 검증: 상품명, 재고, 버전, `keepImages`(이 상품의 이미지인지, 중복 여부), 유지+새 이미지 3개 이하, 새 이미지 파일 → 실패하면 아무것도 올리지 않음
2. 새 이미지 업로드 (롤백되면 삭제)
3. DB 수정: 최종 이미지 = 유지 이미지(요청 순서) + 새 이미지 → `changeImages`
4. 커밋 후: 유지하지 않은 기존 이미지 삭제 / 롤백 시: 새 이미지만 삭제, 기존 이미지는 그대로

- `keepImages` 생략(null) = 모두 유지, `[]` = 모두 삭제. 값은 파일 ID 또는 URL(마지막 경로만 사용)

## 삭제 (`remove`)

1. 상품 조회 후 upload1~3(파일 ID)를 모아 둠
2. DB 삭제 + `flush` (버전 충돌 시 `ProductConflictException`)
3. 트랜잭션 **커밋 후**(`TransactionSynchronization.afterCommit`) Storage에서 이미지 삭제. 롤백되면 이미지는 남음
4. 이미지 삭제 실패는 로그만 남기고 상품 삭제는 유지 (Storage에 파일이 남을 수 있음)

## 검색 (`list`)

- 조건(모두 선택): `keyword`(상품명 또는 설명에 포함, 대소문자 무시), `minPrice`(이상), `maxPrice`(이하)
- 정렬 허용 필드: `pno`, `pname`, `price`, `createdAt`. 그 외는 무시, 기본 `pno desc`
- count 쿼리는 필요할 때만 실행 (`PageableExecutionUtils`)
- `REPEATABLE_READ`로 목록과 전체 건수를 같은 스냅샷에서 읽음
- 인덱스: `lower(pname)`, `lower(pdesc)` pg_trgm GIN, `price` B-tree
- 컨트롤러: page 0/음수 → 1, size 최대 20 (초과 → 20, 1 미만 → 10)
- 응답: `ProductListResponse` (첫 이미지 `thumbnail`, `imageCount`). 원본 URL은 넣지 않음

### 검색 인덱스 사용 확인 (2026-11-12, 같은 인덱스의 임시 테이블 20만 건)

| 검색어 | 실행 계획 |
| --- | --- |
| 3글자 이상 (`마우스`) | `BitmapOr` → pname/pdesc trigram GIN 인덱스 둘 다 사용 |
| 2글자 (`무선`) | **Seq Scan** (trigram은 3글자 단위라 2글자 패턴에 인덱스를 못 씀) |
| 흔한 단어 + LIMIT (`가방`, 2.5만 건) | pkey 역순 스캔 후 10건에서 멈춤 (빠름) |

- 데이터가 적으면(현재 21건) 3글자 이상이어도 Seq Scan이 선택됨 (정상)
- 2글자 검색 대안: PGroonga 확장 (Supabase에서 설치 가능, 미적용). pg_bigm은 제공 안 됨

## 썸네일

- Supabase 이미지 변환(render)은 프로젝트에서 비활성(`FeatureNotEnabled`, 유료 플랜) → 서버에서 생성
- `common.storage.ThumbnailGenerator`: 비율 유지, 최대 300px(확대 안 함), 흰 배경, JPEG 품질 0.85. TwelveMonkeys로 webp 읽기 지원
- 저장: `products/thumbs/{uuid}.jpg` (원본 파일 ID에서 계산, DB 컬럼 없음). 원본 `products/{uuid}.{ext}`는 그대로
- 등록/수정: 모든 파일을 먼저 디코딩해 썸네일 생성 (이미지가 아니면 업로드 전에 400) → 원본 + 썸네일 업로드
- 수정/삭제/롤백: 원본과 썸네일을 함께 삭제

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
| `ProductReadTest` | 단건 조회(이미지 이름/URL), 404·잘못된 pno, 목록 페이징·검색·빈 결과·썸네일, page/size 조정, 숫자 아님 오류 (6건) | 롤백 |
| `ThumbnailGeneratorTest` | 비율 유지 축소, 확대 안 함, JPEG 출력, 이미지 아닌 내용 거부 (6건) | - |
| `ProductModifyTest` | 일부 유지+새 이미지, 생략 시 전부 유지/순서 변경, 전부 삭제, 업로드 전 거부(다른 이미지, 4개, 버전), 롤백 시 새 이미지만 삭제 (5건). 실제 Storage 사용 | 직접 삭제 |
| `ProductDeleteTest` | 이미지 포함 삭제(DB+파일), 이미지 없이 삭제, DB 롤백 시 이미지 유지, 404 (4건). 실제 Storage 사용 | 직접 삭제 |
| `ProductRegisterUploadTest` | 이미지 포함 등록, 이미지 없이 등록, 잘못된 파일 거부, DB 저장 실패 시 이미지 삭제 (4건). 실제 Storage 사용 | 직접 삭제 (DB + Storage) |
| `ProductDummyDataTest` | 상품 20건 저장 | 유지. `SEED_PRODUCTS=true`일 때만 실행 |

```bash
./gradlew test                                   # 전체 (시드 제외)
./gradlew test --tests ProductServiceTest        # 서비스만
SEED_PRODUCTS=true ./gradlew test --tests ProductDummyDataTest   # 시드 20건 추가
```
