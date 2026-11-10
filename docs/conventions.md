# 개발 컨벤션

## 네이밍

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 클래스 | PascalCase | `UserService` |
| 메서드, 변수 | camelCase | `findById` |
| 상수 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| 패키지 | 소문자 | `com.example.sample.user` |
| DTO | `{기능}{Request,Response}` | `UserCreateRequest` |

## 코드 작성

- 의존성은 생성자 주입을 씁니다 (`@RequiredArgsConstructor` + `private final` 필드).
- 조회 전용 서비스 메서드에는 `@Transactional(readOnly = true)`를 붙입니다.
- 예외는 공통 예외 클래스를 상속해서 만들고, 전역 예외 처리기(`@RestControllerAdvice`)에서 응답으로 변환합니다.
- 설정 파일은 YAML(`application*.yaml`)로 작성합니다.

## 테스트

- 테스트 클래스 이름은 `{대상클래스}Test`로 짓습니다.
- 서비스 로직은 단위 테스트로, Repository는 `@DataJpaTest`로 검증합니다.

## 커밋 메시지

```
<type>: <요약>

<본문 (선택)>
```

| type | 용도 |
| --- | --- |
| feat | 새 기능 |
| fix | 버그 수정 |
| refactor | 동작 변화 없는 구조 개선 |
| docs | 문서 |
| test | 테스트 |
| chore | 빌드, 설정 등 기타 |

## 브랜치

- `main`: 배포 가능한 상태 유지
- `feature/{기능}`, `fix/{이슈}`: 작업 브랜치. PR을 거쳐 `main`에 병합합니다.
