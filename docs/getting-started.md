# 시작하기

## 사전 준비

- JDK 21
- Supabase DB 비밀번호

Gradle은 프로젝트에 포함된 Wrapper(`./gradlew`)를 사용하므로 따로 설치할 필요가 없습니다.

## DB 접속 정보 (local 프로필)

`application-local.yaml`은 `.gitignore`에 등록되어 GitHub에 올라가지 않습니다. 처음 받았다면 예시 파일을 복사해서 만듭니다.

```bash
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
```

`local` 프로필은 Supabase PostgreSQL(Session pooler)에 접속합니다. 값은 [application-local.yaml](../src/main/resources/application-local.yaml)에 있습니다.

| 항목 | 값 |
| --- | --- |
| Host | `aws-1-ap-southeast-2.pooler.supabase.com` |
| Port | `5432` |
| Database | `postgres` |
| User | `postgres.pltejotxjzhsdqleqzmj` |
| Password | 환경 변수 `DB_PASSWORD` |

비밀번호는 파일에 적지 않고 실행 전에 환경 변수로 넣습니다.

```bash
export DB_PASSWORD='비밀번호'
```

IntelliJ나 VS Code에서 실행할 때는 실행 구성의 환경 변수에 `DB_PASSWORD`를 추가합니다.

## 빌드와 실행

```bash
# 빌드 (테스트 포함)
./gradlew build

# 실행 (기본 프로필: local)
./gradlew bootRun

# 다른 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

기본 포트는 `8080`입니다.

## 테스트

```bash
./gradlew test
```

`SampleApplicationTests`는 애플리케이션 컨텍스트 전체를 띄우므로 테스트 실행 시에도 DB 접속이 필요합니다.
