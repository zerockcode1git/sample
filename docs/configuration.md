# 설정과 프로필

설정 파일은 `src/main/resources`에 YAML 형식으로 둡니다.

| 파일 | 용도 |
| --- | --- |
| `application.yaml` | 모든 환경에 공통으로 적용되는 설정. 기본 활성 프로필(`local`)을 지정합니다. |
| `application-local.yaml` | 로컬 개발용 설정 (Supabase DB 접속, JPA, SQL 로그). 비밀번호는 `DB_PASSWORD` 환경 변수로 받습니다. Git 제외 대상입니다. |
| `application-local.yaml.example` | `application-local.yaml`의 예시. Git에 올라갑니다. |

## 프로필 전환

`application.yaml`의 `spring.profiles.active` 기본값은 `local`입니다. 다른 환경에서는 아래 방법 중 하나로 덮어씁니다.

```bash
# 실행 인자
java -jar sample.jar --spring.profiles.active=prod

# 환경 변수
SPRING_PROFILES_ACTIVE=prod java -jar sample.jar
```

## local 프로필 주요 설정

| 키 | 값 | 설명 |
| --- | --- | --- |
| `spring.datasource.hikari.maximum-pool-size` | `2` | 커넥션 풀 최대 개수. Supabase 접속 수를 아끼기 위해 최소로 둡니다. |
| `spring.datasource.hikari.minimum-idle` | `1` | 유휴 상태로 유지할 최소 커넥션 수 |
| `spring.jpa.hibernate.ddl-auto` | `update` | 엔티티에 맞춰 스키마를 자동 변경합니다. 로컬 전용입니다. |
| `spring.jpa.show-sql` | `true` | 실행 SQL을 출력합니다. |
| `spring.jpa.properties.hibernate.format_sql` | `true` | SQL을 정렬해서 출력합니다. |
| `spring.jpa.open-in-view` | `false` | 뷰 렌더링 구간까지 영속성 컨텍스트를 유지하지 않습니다. |

## 새 프로필 추가 규칙

- 파일 이름은 `application-{profile}.yaml` 형식을 따릅니다.
- 운영 환경의 비밀번호, 키 같은 민감 정보는 파일에 적지 않고 환경 변수로 주입합니다.

  ```yaml
  spring:
    datasource:
      url: ${DB_URL}
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
  ```

- 운영 환경에서는 `ddl-auto`를 `validate` 또는 `none`으로 둡니다.
