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
