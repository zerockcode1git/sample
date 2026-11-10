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
