# 데이터베이스

- DBMS: PostgreSQL (Supabase, Session pooler)
- ORM: Spring Data JPA (Hibernate)

## 스키마 관리

| 환경 | 방식 |
| --- | --- |
| local | `ddl-auto: update`로 자동 반영 |
| 운영 | `ddl-auto: validate`. 스키마 변경은 마이그레이션 도구(Flyway 등) 도입 후 스크립트로 관리할 예정 |

## 테이블 목록

아직 정의된 엔티티가 없습니다. 엔티티를 추가할 때 아래 형식으로 기록합니다.

### `{table_name}`

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 식별자 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

## 엔티티 작성 규칙

- 테이블과 컬럼 이름은 `snake_case`를 씁니다. Spring Boot 기본 네이밍 전략이 camelCase 필드를 자동으로 변환합니다.
- 기본키는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 씁니다.
- 연관관계는 기본으로 `FetchType.LAZY`를 씁니다.
- Lombok `@Setter`, `@Data`는 쓰지 않고 `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 씁니다.
