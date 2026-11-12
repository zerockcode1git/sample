# 작업 기록

## 2026-11-10

- `application.properties`를 `application.yaml`(공통)과 `application-local.yaml`(로컬 프로필)로 전환
- `docs/` 폴더와 문서 6개 작성 (getting-started, configuration, architecture, database, api, conventions)
- 루트 `README.md` 작성
- 작업 기록용 `docs/worklog.md` 추가
- `local` 프로필 DB를 Supabase PostgreSQL로 변경 (`sslmode=require`), 비밀번호는 `DB_PASSWORD` 환경 변수로 주입
- README, getting-started, configuration, database 문서에 접속 정보 반영
- `application-local.yaml`을 `.gitignore`에 추가하고, 공유용 `application-local.yaml.example` 생성
- DB 연결 확인: `./gradlew test`(contextLoads) 통과, Supabase 접속 성공
- `local` 프로필 HikariCP 커넥션 풀 최소화 (`maximum-pool-size: 2`, `minimum-idle: 1`)
- 비밀번호 입력 방식 문서 보완 (`application-local.yaml` 직접 입력 또는 `DB_PASSWORD` 환경 변수)

### 현재 상태 요약

- 설정: `application.yaml`(공통, 기본 프로필 `local`) + `application-local.yaml`(Git 제외) + `application-local.yaml.example`(공유용)
- DB: Supabase PostgreSQL 17, SSL 필수, 커넥션 풀 최대 2개
- 문서: 루트 `README.md`, `docs/` 아래 7개 문서
- 남은 작업: 도메인 코드, 엔티티, API 구현 시 `database.md`, `api.md` 갱신

## 2026-11-10 (상품 엔티티)

- `common.entity.BaseEntity` 추가 (작성일, 수정일 자동 기록), `common.config.JpaAuditingConfig`로 JPA Auditing 활성화
- `product.entity.ProductEntity` 추가 (`tbl_product`). 수정 메서드는 상품명, 판매여부, 재고수량만 제공
- `docs/database.md`에 테이블 정의 반영

## 2026-11-10 (ProductRepository)

- Querydsl 추가 (`io.github.openfeign.querydsl` 7.7, Hibernate 7 호환 포크), `QuerydslConfig`로 `JPAQueryFactory` 빈 등록
- `ProductRepository` (JpaRepository + `ProductSearch`): 기본 CRUD + `search(condition, pageable)`
  - 검색: 키워드(상품명 OR 설명, 대소문자 무시), 최소/최대 가격
  - 페이징: offset/limit, count 쿼리는 `PageableExecutionUtils`로 필요할 때만 실행, 정렬 필드 화이트리스트(pno, pname, price, createdAt), 기본 정렬 pno desc
- 인덱스: price B-tree(`@Index`), lower(pname)/lower(pdesc) pg_trgm GIN (`db/product-index.sql`, `application-local.yaml`의 `spring.sql.init`로 실행)
- 테스트 (모두 `@SpringBootTest` + 실제 DB, 롤백)
  - `ProductRepositoryTest`: CRUD + 검색 + 인덱스 존재 확인 7건, 통과
  - `ProductNameSearchTest`: 상품명 검색 1건, 통과 (SQL/바인딩 로그 확인)
  - `ProductCrudTest`: insert/select/update/delete 각 1건 (미실행)
  - `ProductDummyDataTest`: 상품 20건 실제 저장 (롤백 없음, `SEED_PRODUCTS=true`일 때만 실행)
- 실제 DB에 상품 20건 저장 (pno 32~51). '무선' 검색 시 3건 (상품명 2, 설명만 1)

### 현재 상태 요약

- 상품: 엔티티, Repository(CRUD + Querydsl 검색/페이징), 검색 인덱스 완료
- 남은 작업
  - `spring.sql.init` 설정이 Git 제외 파일(`application-local.yaml`)에만 있음 → 공유 위치로 옮길지 결정 필요
  - `application-local.yaml.example`이 작업 트리에서 삭제된 상태 (커밋에는 미포함)
  - 검색 시 판매여부 필터 추가 여부 결정
  - `ProductRepositoryTest`와 `ProductCrudTest`의 CRUD 테스트 중복 정리
  - Service, Controller, API 문서(`api.md`)

## 2026-11-10 (ProductService)

- `ProductService`: register, get, list, modify, changeStock, remove. 클래스 기본 `readOnly`, 변경 메서드만 `@Transactional`
- DTO: `ProductCreateRequest`, `ProductUpdateRequest`(version 포함), `ProductResponse`, 공통 `PageResponse`
- 예외: `BusinessException` + `ErrorCode`, 상품 예외 4종 + `InvalidInputException`, `GlobalExceptionHandler`
- 동시성: 재고 증감은 비관적 잠금(FOR NO KEY UPDATE, lock_timeout 3초), 수정은 `@Version` 낙관적 잠금, 목록은 REPEATABLE_READ
- `tbl_product.version` 컬럼 추가 (기존 20건 0으로 채워짐)
- `ProductServiceTest` 12건 통과 (동시 차감 8건 → 5건 성공, 3건 재고 부족). 전체 테스트 통과
- `architecture.md`(패키지명 `common`으로 정정, 예외/동시성 표), `database.md` 갱신

- 실제 DB 변경: 33번(기계식 키보드) 재고 6 → 4 (`changeStock`, amount=-2), version 0 → 1

- `ProductStockChangeRequest` 삭제, `changeStock(Long pno, int amount)`로 변경 (요청: 단일 값은 DTO로 만들지 않음)

### 현재 상태 요약

- 상품: Entity, Repository(CRUD + Querydsl 검색/페이징), Service(트랜잭션, DTO, 커스텀 예외, 동시성) 완료. 기능 정리는 `docs/product.md`
- 테스트: 전체 통과 (Repository 7, CRUD 4, 이름 검색 1, Service 12)
- 실제 DB: 상품 20건 (33번 재고 4, version 1)
- 남은 작업
  - Controller, API 문서(`api.md`)
  - `spring.sql.init` 설정이 Git 제외 파일(`application-local.yaml`)에만 있음
  - `application-local.yaml.example`이 작업 트리에서 삭제된 상태 (커밋 미포함)
  - 검색 시 판매여부 필터 여부, `@DynamicUpdate` 적용 여부

## 2026-11-12 (ProductController)

- `ProductController` (`/api/products`): 등록(POST 201), 조회(GET), 목록(GET), 수정(PUT), 삭제(DELETE 204)
  - 목록: `page`는 1부터(기본 1), `size` 1~100(기본 10), `keyword`로 상품명/설명 검색. 범위 밖이면 400
- `PageResponse.page`를 1 기반으로 변경 (`getNumber() + 1`)
- `ProductControllerAdvice`: ProductController 전용, `HIGHEST_PRECEDENCE`. 비즈니스 예외, 타입 불일치, JSON 오류, DB 제약 위반(400), 그 외(500)를 메시지 응답으로 변환
- `ProductControllerTest` 4건 (MockMvc + 실제 DB, 롤백). 전체 테스트 통과
- `api.md`, `product.md` 갱신

### 현재 상태 요약

- 상품: Entity, Repository, Service, Controller + Advice 완료
- 남은 작업
  - 재고 증감(`changeStock`) API 노출 여부, 목록 가격/정렬 파라미터 노출 여부
  - `spring.sql.init` 설정이 Git 제외 파일에만 있음, `application-local.yaml.example` 삭제 상태 (커밋 미포함)

## 2026-11-12 (Supabase Storage 설정)

- `application-local.yaml`에 `supabase.storage` 추가 (url, bucket `images`(Private), service-key `${SUPABASE_SERVICE_KEY:}`, signed-url-expires-in 3600)
- `common.storage.SupabaseStorageProperties`(@ConfigurationProperties), `SupabaseStorageConfig`(인증 헤더가 들어간 `RestClient` 빈)
- `SupabaseStorageConfigTest`: 설정 바인딩 1건 통과, 버킷 접속 확인 1건은 `SUPABASE_SERVICE_KEY` 없어 건너뜀
- `configuration.md` 갱신
- 남은 작업: 업로드/삭제/서명 URL 서비스, 상품 이미지 연동, 키 설정 후 접속 확인

## 2026-11-12 (Storage 업로드 확인)

- `SupabaseStorageUploadTest` 3건 통과 (실제 업로드 후 삭제)
  - 2.32MB PNG 업로드 200 → 서명 URL로 다운로드, 크기 일치
  - 3.15MB PNG 업로드 거부 (`EntityTooLarge`, 413)
  - 버킷 정보: `public=true`(요청은 Private였음), `file_size_limit=3145728`(3MB), `allowed_mime_types=[image/*]`
- 문제 해결: `{path}` URI 변수에 `/`가 들어가면 `%2F`로 인코딩되어 서명 URL이 `InvalidSignature` → 경로를 문자열로 연결
- 확인용 샘플 `images/test/sample-under-3mb.png`(2.13MB) 업로드, 삭제하지 않고 유지
- 남은 작업: 버킷 Public/Private 결정, 업로드 서비스 구현 시 경로 인코딩 주의

## 2026-11-12 (상품 등록 이미지 업로드)

- 버킷은 Public 유지로 결정 (상품 이미지는 공개 자료, 고정 URL·CDN 캐시). DB에는 경로만 저장하고 응답 시 Public URL로 변환
- `common.storage`: `FileStorageService`(인터페이스), `SupabaseFileStorageService`(upload/delete/getUrl), `ImageFileValidator`(image/*, jpg/jpeg/png/gif/webp, 3MB 미만, 최대 3개), `InvalidFileException`(400), `StorageException`(502)
- `ErrorCode`: `INVALID_FILE`, `STORAGE_ERROR` 추가. `GlobalExceptionHandler`에 multipart 크기 초과 처리
- `application.yaml`: multipart `max-file-size: 3MB`, `max-request-size: 10MB`
- 등록 API를 `multipart/form-data`로 변경 (`product` JSON 파트 + `images` 파트 0~3개). `ProductCreateRequest`에서 img1~3 제거
- `ProductService.register(request, images)`: 전부 검증 → 업로드 → DB 저장. 커밋 안 되면 업로드 파일 삭제 (`TransactionSynchronization.afterCompletion`)
- `ProductResponse.from(entity, urlResolver)`로 변경, 조회/목록/수정 응답도 이미지 URL로 나감
- `ProductControllerAdvice`: `product` 파트 누락, 파트 형식 오류 처리 추가
- 테스트: `ProductRegisterUploadTest` 4건 추가(실제 Storage, 롤백 시 삭제 확인), `ProductControllerTest` multipart로 수정. 전체 통과
- `api.md`, `product.md`, `configuration.md` 갱신

### 현재 상태 요약

- 상품 등록: multipart + Supabase Storage 이미지 업로드 완료
- 남은 작업
  - 상품 삭제 시 Storage 이미지 삭제 (현재 범위 밖)
  - 업로드 파일 내용 검사 (현재 Content-Type과 확장자만 확인)
  - 시드 데이터 img 값은 Storage에 실제 파일 없음

## 2026-11-12 (Swagger UI)

- `springdoc-openapi-starter-webmvc-ui:3.2.0` 추가 (Spring Boot 4.1.2 기준 빌드, swagger-ui 5.34.0)
- `OpenApiConfig`: API 정보 + `GroupedOpenApi`("상품 등록")로 `ProductController.register`만 노출
- 등록 API에 `@Operation`, 응답 예시(201/400/502), `product` 파트 `encoding: application/json` 지정 → Swagger UI의 Try it out에서 JSON 파트로 전송됨 (swagger-ui 번들 코드에서 확인)
- `ProductCreateRequest`, `ProductResponse`, `ErrorResponse`에 `@Schema` 설명/예시
- 확인: `bootRun` 후 `/swagger-ui.html` 302 → `/swagger-ui/index.html`, 문서 경로는 `POST /api/products` 하나. curl로 같은 형식 등록 201 → 이미지 URL 200 (확인용 상품 246과 이미지는 삭제). 전체 테스트 통과

## 2026-11-12 (저장 파일명에 원본 이름 추가)

- 저장 파일명: `{uuid}_{원본이름}.{소문자 확장자}` (예: `front.PNG` → `{uuid}_front.png`)
- Supabase 객체 키는 한글을 거부(`InvalidKey`, 직접 확인)하므로 원본 이름은 영문/숫자/`.`/`_`/`-`만 남기고 나머지는 `_`로 바꿈. 남는 게 없으면 `image`, 최대 100자, 경로(`C:\...`, `dir/`)는 제거
- `StoredFileNameTest` 9건 추가, `ProductRegisterUploadTest` 경로 검증 수정. 전체 테스트 통과

## 2026-11-12 (img / upload 컬럼 분리)

- `img1~3`: 원본 이미지 파일 이름 (한글 그대로), `upload1~3`: 실제 업로드된 파일의 저장 경로 (응답은 Public URL)
- `ProductEntity`에 `upload1~3` 추가 (`ddl-auto: update`로 컬럼 생성됨), `ProductImage(originalName, path)` DTO 추가
- `ProductResponse`에 `upload1~3` 추가, Swagger 설명 갱신
- `ProductRegisterUploadTest`: 한글 원본 이름(`앞면 사진(1).png`) 보존, 저장 키는 `{uuid}_1.png` 검증. 전체 테스트 통과
- `database.md`, `api.md`, `product.md` 갱신
- 미처리: 기존 1건(pno 287)은 `img1`에 업로드 경로가 들어 있음. DB 직접 수정이 권한 거부되어 이관 SQL만 안내 (원본 이름은 기록된 적 없음)

## 2026-11-12 (upload1~3은 UUID만)

- Storage 키를 `products/{uuid}`로 변경 (날짜 폴더, 원본 이름, 확장자 제거). 형식은 업로드 시 Content-Type으로 저장되어 Public URL 응답도 `image/png` 등으로 나옴
- DB `upload1~3`에는 UUID만 저장, 응답은 Public URL. 원본 이름은 `img1~3`
- `FileStorageService`: `upload(file, dir)` → UUID 반환, `delete(dir, id)`, `getUrl(dir, id)`로 변경
- `{uuid}_{원본이름}` 규칙과 `StoredFileNameTest` 삭제 (원본 이름은 DB에만 보관)
- 기존 데이터 이관 (직접 실행): pno 287 Storage `products/2026/11/12/3f83...png` → `products/3f83...` 이동, DB `upload1=3f83...`, `img1=3f83....png` (원본 이름은 기록된 적 없음). 남은 옛 경로 0건
- 전체 테스트 통과

## 2026-11-12 (upload1~3에 확장자 추가, 재테스트)

- 파일 ID와 Storage 키를 `{uuid}.{소문자 확장자}`로 변경 (`upload1~3` = `{uuid}.png`, 키 = `products/{uuid}.png`)
- pno 200 이상 상품은 사용자가 직접 삭제 (pno 287 포함) → 이관 대상 없음
- 전체 테스트 통과. `bootRun` 후 HTTP로 등록 확인: pno 449, img1=`앞면 사진(1).png`, upload1=`28d00fd0-....png`, img2=`Back.PNG`, upload2=`646de249-....png`(확장자 소문자), Public URL 200 `image/png`
- pno 449는 확인용으로 남겨 둠

## 2026-11-12 (상품 조회)

- 단건 조회 `GET /api/products/{pno}`, 목록 조회 `GET /api/products?page&size&keyword`는 기존 구현 유지 (응답에 img=원본 이름, upload=Public URL)
- Swagger: 그룹을 "상품 (등록/조회)"(`/v3/api-docs/product`)로 바꾸고 `register`, `get`, `list` 노출. 조회 API에 설명/파라미터/에러 예시, `PageResponse`에 `@Schema`
- `ProductReadTest` 5건 추가 (Repository로 직접 저장 → Storage 호출 없음, 롤백). 전체 테스트 통과
- 실행 확인: pno 449 조회 시 img1=`앞면 사진(1).png`, upload1=Public URL. 목록 page=1,size=2 → 전체 21건, 11페이지

## 2026-11-12 (상품 삭제 + 이미지 삭제)

- `ProductService.remove`: DB 삭제(flush)를 먼저 처리하고, 커밋 후(`afterCommit`)에만 upload1~3 이미지를 Storage에서 삭제. 롤백 시 이미지 유지, 이미지 삭제 실패는 로그만
- Swagger: 삭제 API 문서 추가 (그룹 "상품 (등록/조회/삭제)")
- `ProductDeleteTest` 4건 추가 (실제 DB + Storage). 전체 테스트 통과
- 발견: Public URL은 삭제 직후에도 CDN 캐시로 잠시 200을 줄 수 있음 → 테스트는 인증 API(`/object/authenticated/...`)로 존재 여부 확인
- 실행 중이던 앱 종료

## 2026-11-12 (상품 수정 + 이미지 교체)

- `PUT /api/products/{pno}`를 multipart로 변경 (`product` JSON + 새 `images` 0~3개)
- `ProductUpdateRequest.keepImages` 추가: 유지할 기존 이미지(파일 ID 또는 URL). 생략=모두 유지, []=모두 삭제. 최종 = 유지(순서대로) + 새 이미지, 최대 3개
- 처리: 검증(버전 포함, 업로드 전) → 새 이미지 업로드 → DB 수정 → 커밋 후 유지 안 한 기존 이미지 삭제. 롤백 시 새 이미지만 삭제
- `ProductEntity.changeImages`, `getImages` 추가. Swagger에 수정 API 추가
- `ProductModifyTest` 5건 추가, `ProductControllerTest` PUT을 multipart로 수정. 전체 테스트 통과
- 실제 서버 확인: 임시 상품 648 등록(이미지 2) → PUT(1개 유지 + 1개 추가) 200, 제거된 이미지 Storage에서 삭제 확인 → 임시 상품 삭제. Storage에는 pno 449 이미지 2개만 남음

## 2026-11-12 (curl 테스트 문서)

- `docs/curl-test.md` 추가: 준비(테스트 PNG 생성) → 등록(이미지 有/無) → 조회(단건, 목록, 검색) → 수정(정보만, 일부 유지+추가, 전부 삭제) → 삭제 → 에러 12종 → 정리. 변수(`PNO`, `VERSION`)를 이어 받아 순서대로 실행
- 문서의 bash 블록을 그대로 추출해 zsh로 실행 검증 (모든 응답이 문서 설명과 일치), 테스트 데이터 정리 완료
- `README.md`, `api.md`에 링크 추가

## 2026-11-12 (상품 목록: 20개 제한, 썸네일, 검색 인덱스 확인) — 진행 중 중지

- 목록 `size` 최대 20 (초과 → 20, 1 미만 → 10), `page` 0/음수 → 1로 조정 (오류 대신)
- 썸네일: Supabase 이미지 변환은 비활성(`FeatureNotEnabled`, 유료) → 업로드 시 서버에서 생성(`ThumbnailGenerator`, 최대 300px JPEG 0.85, 흰 배경), `products/thumbs/{uuid}.jpg`. 원본은 그대로 유지. TwelveMonkeys imageio-webp/jpeg 3.15.3 추가
- 목록 응답을 `ProductListResponse`(첫 이미지 `thumbnail`, `imageCount`)로 변경. 등록/수정/삭제/롤백에서 원본과 썸네일 함께 처리. 디코딩 실패 파일은 400 `INVALID_FILE`
- 테스트: `ThumbnailGeneratorTest` 6건, 목록 조정/썸네일 검증 추가, 실제 이미지 사용(`support.TestImages`). 전체 통과
- pno 449 이미지 2개 썸네일 수동 생성 (2.2MB → 67KB)
- 검색 인덱스 (20만 건 임시 테이블): 3글자 이상은 pname/pdesc trigram GIN BitmapOr 사용, **2글자는 trigram 불가로 Seq Scan**. 대안: PGroonga(설치 가능, 미설치)
- 중지 시점: 새 코드로 앱 실행 후 HTTP 확인 전, 문서(`curl-test.md`, `api.md`, `product.md`) 미갱신, 커밋 안 함
