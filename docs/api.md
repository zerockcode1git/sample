# API 명세

- Base URL (local): `http://localhost:8080`
- 요청과 응답 형식: `application/json` (상품 등록은 `multipart/form-data`)
- Swagger UI: `http://localhost:8080/swagger-ui.html` (OpenAPI JSON: `/v3/api-docs/product-register`). 현재 **상품 등록만** 문서화되어 있습니다.

## 엔드포인트 목록

| Method | Path | 설명 | 성공 |
| --- | --- | --- | --- |
| POST | `/api/products` | 상품 등록 | 201 |
| GET | `/api/products/{pno}` | 상품 단건 조회 | 200 |
| GET | `/api/products?page=1&size=10&keyword=` | 상품 목록 + 검색 | 200 |
| PUT | `/api/products/{pno}` | 상품 수정 (상품명, 판매여부, 재고) | 200 |
| DELETE | `/api/products/{pno}` | 상품 삭제 | 204 |

## 상품 API

컨트롤러: `product.controller.ProductController`, 예외 응답: `ProductControllerAdvice`

### `POST /api/products`

`multipart/form-data`로 보냅니다.

| 파트 | Content-Type | 필수 | 설명 |
| --- | --- | --- | --- |
| `product` | `application/json` | O | `{"pname","pdesc","price","onSale","stock"}` |
| `images` | `image/*` | X | 0~3개, 각 3MB 미만, 확장자 jpg/jpeg/png/gif/webp. 순서대로 img1~img3 |

```bash
curl -X POST http://localhost:8080/api/products \
  -F 'product={"pname":"무선 마우스","pdesc":"2.4GHz","price":25000,"onSale":true,"stock":10};type=application/json' \
  -F 'images=@front.png' -F 'images=@back.png'
```

응답 201: 상품 객체. `img1~img3`은 원본 파일 이름, `upload1~upload3`은 업로드된 파일 주소(Public URL, `{url}/storage/v1/object/public/images/products/{uuid}`). 없으면 null.

에러

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 빈 상품명, 음수 가격, JSON 오류, 상품명 200자 초과, `product` 파트 없음 또는 JSON이 아님 |
| 400 | `INVALID_STOCK` | 음수 재고 |
| 400 | `INVALID_FILE` | 이미지가 아님, 3MB 이상, 4개 이상, 빈 파일, 요청 전체 10MB 초과 |
| 502 | `STORAGE_ERROR` | Supabase Storage 업로드 실패 |

### `GET /api/products/{pno}`

```json
{ "pno": 33, "pname": "기계식 키보드", "pdesc": "...", "price": 89000, "onSale": true, "stock": 4,
  "img1": null, "img2": null, "img3": null,
  "upload1": null, "upload2": null, "upload3": null, "version": 1,
  "createdAt": "2026-11-10T10:00:00", "updatedAt": "2026-11-10T11:00:00" }
```

에러: 404 `PRODUCT_NOT_FOUND`, 400 `INVALID_INPUT`(pno가 숫자가 아님)

### `GET /api/products`

| 파라미터 | 기본값 | 설명 |
| --- | --- | --- |
| page | 1 | **1부터 시작**. 내부에서 0 기반으로 변환 |
| size | 10 | 1~100 |
| keyword | 없음 | 상품명 또는 설명에 포함 (대소문자 무시) |

정렬은 `pno desc`(최신 등록순) 고정.

```json
{ "content": [ { "pno": 51, "...": "..." } ], "page": 1, "size": 10,
  "totalElements": 20, "totalPages": 2, "hasNext": true }
```

에러: 400 `INVALID_INPUT`(page < 1, size 범위 밖)

### `PUT /api/products/{pno}`

```json
{ "pname": "기계식 키보드 V2", "onSale": true, "stock": 10, "version": 1 }
```

`version`은 조회했던 값. 응답 200: 수정된 상품 객체 (version +1).
에러: 404 `PRODUCT_NOT_FOUND`, 409 `PRODUCT_CONFLICT`(버전 불일치), 400 `INVALID_INPUT`, 400 `INVALID_STOCK`

### `DELETE /api/products/{pno}`

응답 204 (본문 없음). 에러: 404 `PRODUCT_NOT_FOUND`, 409 `PRODUCT_CONFLICT`

## 공통 에러 응답

```json
{
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다."
}
```

예상하지 못한 오류는 500 `INTERNAL_ERROR`("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")

## URL 규칙

- 경로는 `/api` 아래에 두고, 리소스 이름은 복수형 kebab-case를 씁니다. 예: `/api/order-items`
- 행위는 HTTP 메서드로 표현합니다 (`GET` 조회, `POST` 생성, `PUT`/`PATCH` 수정, `DELETE` 삭제).
