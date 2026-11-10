# API 명세

- Base URL (local): `http://localhost:8080`
- 요청과 응답 형식: `application/json`

## 엔드포인트 목록

아직 구현된 API가 없습니다. API를 추가할 때 아래 표와 상세 형식에 맞춰 기록합니다.

| Method | Path | 설명 |
| --- | --- | --- |
| - | - | - |

## 상세 작성 형식

### `GET /api/{resource}/{id}`

{리소스} 한 건을 조회합니다.

**Path Parameter**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| id | Long | 리소스 ID |

**Response 200**

```json
{
  "id": 1
}
```

**에러**

| 상태 코드 | 상황 |
| --- | --- |
| 404 | 리소스가 없음 |

## 공통 에러 응답

```json
{
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다."
}
```

## URL 규칙

- 경로는 `/api` 아래에 두고, 리소스 이름은 복수형 kebab-case를 씁니다. 예: `/api/order-items`
- 행위는 HTTP 메서드로 표현합니다 (`GET` 조회, `POST` 생성, `PUT`/`PATCH` 수정, `DELETE` 삭제).
