# 아키텍처

## 패키지 구조

기본 패키지는 `com.example.sample`입니다. 도메인(기능) 단위로 패키지를 나누고, 그 안에서 계층별로 클래스를 둡니다.

```
com.example.sample
├── SampleApplication.java
├── common/                 # 공통 모듈
│   ├── config/             # 설정 클래스
│   ├── entity/             # BaseEntity (작성일, 수정일)
│   ├── exception/          # BusinessException, ErrorCode, 전역 예외 처리
│   └── dto/                # 공통 응답 형식 (PageResponse)
└── {domain}/               # 예: user, order
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    └── dto/
```

> 현재 구현된 도메인: `product` (entity, dto, repository, service, exception)

## 계층별 역할

| 계층 | 역할 |
| --- | --- |
| Controller | HTTP 요청과 응답 처리, 입력값 검증. 비즈니스 로직을 두지 않습니다. |
| Service | 비즈니스 로직과 트랜잭션 경계(`@Transactional`) |
| Repository | Spring Data JPA를 이용한 데이터 접근 |
| Entity | DB 테이블과 매핑되는 도메인 객체 |
| DTO | 계층 사이, 그리고 API 요청과 응답에 쓰는 데이터 객체 |

## 의존 방향

```
Controller → Service → Repository → Entity
```

- Controller는 Entity를 직접 반환하지 않고 DTO로 변환해서 반환합니다.
- 다른 도메인의 기능이 필요하면 그 도메인의 Repository가 아니라 Service를 통해 사용합니다.

## 예외 처리

- 비즈니스 계층의 문제는 `BusinessException`을 상속한 커스텀 예외로 던집니다. 각 예외는 `ErrorCode`(HTTP 상태, 기본 메시지)를 가집니다.
- `GlobalExceptionHandler`가 `{status, code, message}` 형식으로 응답합니다.

| ErrorCode | 상태 | 예외 | 상황 |
| --- | --- | --- | --- |
| INVALID_INPUT | 400 | `InvalidInputException` | 빈 상품명, 음수 가격, 잘못된 가격 범위 등 |
| PRODUCT_NOT_FOUND | 404 | `ProductNotFoundException` | 상품 없음 |
| INVALID_STOCK | 400 | `InvalidStockException` | 재고를 음수로 지정 |
| INSUFFICIENT_STOCK | 409 | `InsufficientStockException` | 증감 결과 재고가 음수 |
| PRODUCT_CONFLICT | 409 | `ProductConflictException` | 조회 이후 다른 변경 발생(버전 불일치), 잠금 대기 시간 초과 |

## 동시성 (상품)

| 상황 | 방식 |
| --- | --- |
| 재고 증감 동시 요청 | 비관적 잠금 (`findByIdForUpdate`, 대기 최대 3초). 순서대로 반영되고 음수가 되면 거부 |
| 조회 후 수정 사이에 다른 변경 | 낙관적 잠금 (`@Version`). 요청에 조회 시점 `version`을 담고, 다르면 409 |
| 목록 + 전체 건수 | `REPEATABLE_READ` 읽기 전용 트랜잭션으로 같은 스냅샷에서 조회 |
