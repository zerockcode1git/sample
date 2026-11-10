# sample

Spring Boot 기반 백엔드 서비스입니다.

## 기술 스택

- Java 21
- Spring Boot 4.1.2 (Web MVC, Data JPA)
- PostgreSQL
- Gradle, Lombok

## 빠른 시작

```bash
# 1. 로컬 프로필 파일 생성 후 password에 Supabase DB 비밀번호 입력
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml

# 2. 애플리케이션 실행 (기본 프로필: local)
./gradlew bootRun
```

서버는 `http://localhost:8080`에서 실행됩니다. 자세한 내용은 [시작하기](docs/getting-started.md)를 참고하세요.

## 주요 명령

| 명령 | 설명 |
| --- | --- |
| `./gradlew bootRun` | 애플리케이션 실행 |
| `./gradlew build` | 빌드 (테스트 포함) |
| `./gradlew test` | 테스트 실행 (DB 접속 필요) |

## 프로젝트 구조

```
.
├── docs/                         # 프로젝트 문서
├── src/main/java/com/example/sample/
├── src/main/resources/
│   ├── application.yaml          # 공통 설정
│   └── application-local.yaml    # 로컬 프로필 설정
└── src/test/java/com/example/sample/
```

## 문서

- [시작하기](docs/getting-started.md)
- [설정과 프로필](docs/configuration.md)
- [아키텍처](docs/architecture.md)
- [데이터베이스](docs/database.md)
- [API 명세](docs/api.md)
- [개발 컨벤션](docs/conventions.md)
- [작업 기록](docs/worklog.md)
