# 아키텍처

## 패키지 구조

기본 패키지는 `com.example.sample`입니다. 도메인(기능) 단위로 패키지를 나누고, 그 안에서 계층별로 클래스를 둡니다.

```
com.example.sample
├── SampleApplication.java
├── global/                 # 공통 모듈
│   ├── config/             # 설정 클래스
│   ├── exception/          # 공통 예외, 전역 예외 처리
│   └── response/           # 공통 응답 형식
└── {domain}/               # 예: user, order
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    └── dto/
```

> 현재는 `SampleApplication`만 있으며, 위 구조는 기능을 추가할 때 따를 기준입니다.

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
