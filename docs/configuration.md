# 설정과 프로필

설정 파일은 `src/main/resources`에 YAML 형식으로 둡니다.

| 파일 | 용도 |
| --- | --- |
| `application.yaml` | 모든 환경에 공통으로 적용되는 설정. 기본 활성 프로필(`local`)을 지정합니다. |
| `application-local.yaml` | 로컬 개발용 설정 (Supabase DB 접속, JPA, SQL 로그). 비밀번호는 `DB_PASSWORD` 환경 변수로 받습니다. Git 제외 대상입니다. |
| `application-local.yaml.example` | `application-local.yaml`의 예시. Git에 올라갑니다. |

## 공통 설정 (`application.yaml`)

| 키 | 값 | 설명 |
| --- | --- | --- |
| `spring.servlet.multipart.max-file-size` | `3MB` | 파일 1개 최대 크기 (버킷 제한과 동일). 3MB 미만 검사는 `ImageFileValidator`가 다시 함 |
| `spring.servlet.multipart.max-request-size` | `10MB` | 요청 전체 크기 (이미지 3개 + 상품 정보) |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI 주소. 문서에 넣을 API는 `common.config.OpenApiConfig`의 `GroupedOpenApi`에서 고름 |
| `springdoc.api-docs.path` | `/v3/api-docs` | OpenAPI JSON 주소 |

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

## Supabase Storage 설정 (local)

`application-local.yaml`의 `supabase.storage.*` → `common.storage.SupabaseStorageProperties`로 바인딩됩니다.

| 키 | 값 | 설명 |
| --- | --- | --- |
| `supabase.storage.url` | `https://pltejotxjzhsdqleqzmj.supabase.co` | 프로젝트 URL. API 경로는 `{url}/storage/v1` |
| `supabase.storage.bucket` | `images` | Public 버킷(상품 이미지용으로 Public 유지 결정), 크기 제한 3MB(`file_size_limit=3145728`), `image/*`만 허용 |
| `supabase.storage.service-key` | `${SUPABASE_SERVICE_KEY:}` | service_role 키. 직접 입력하거나 환경 변수로 주입. 서버 전용, Git/클라이언트에 노출 금지 |
| `supabase.storage.signed-url-expires-in` | `3600` | Private 객체 조회용 서명 URL 유효 시간(초) |

`SupabaseStorageConfig`가 기본 URL과 인증 헤더(`apikey`, `Authorization: Bearer`)를 넣은 `RestClient` 빈(`supabaseStorageRestClient`)을 등록합니다.

```bash
export SUPABASE_SERVICE_KEY='service_role 키'
./gradlew test --tests SupabaseStorageConfigTest   # 키가 있으면 버킷 접속까지 확인
./gradlew test --tests SupabaseStorageUploadTest   # 실제 업로드(3MB 미만 성공, 초과 거부) 후 삭제
```

주의: 객체 경로를 `uri("/object/{bucket}/{path}", ...)`처럼 템플릿 변수로 넘기면 `/`가 `%2F`로 인코딩되어 서명 URL 검증이 실패(`InvalidSignature`)합니다. 경로는 문자열로 이어 붙입니다.

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
