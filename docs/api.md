# API 명세

- Base URL (local): `http://localhost:8080`
- curl 테스트 명령 모음: [curl로 상품 CRUD 테스트](curl-test.md)
- 요청과 응답 형식: `application/json` (상품 등록은 `multipart/form-data`)
- Swagger UI: `http://localhost:8080/swagger-ui.html` (OpenAPI JSON: `/v3/api-docs/product`). 현재 **상품 등록, 단건 조회, 목록 조회, 수정, 삭제**가 문서화되어 있습니다.

## 엔드포인트 목록

| Method | Path | 설명 | 성공 |
| --- | --- | --- | --- |
| POST | `/api/products` | 상품 등록 | 201 |
| GET | `/api/products/{pno}` | 상품 단건 조회 | 200 |
| GET | `/api/products?page=1&size=10&keyword=` | 상품 목록 + 검색 | 200 |
| PUT | `/api/products/{pno}` | 상품 수정 (상품명, 판매여부, 재고, 이미지 교체) | 200 |
| DELETE | `/api/products/{pno}` | 상품 삭제 (업로드 이미지 포함) | 204 |

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
  "img1": "앞면 사진(1).png", "img2": null, "img3": null,
  "upload1": "https://…/storage/v1/object/public/images/products/28d00fd0-….png", "upload2": null, "upload3": null,
  "version": 1,
  "createdAt": "2026-11-10T10:00:00", "updatedAt": "2026-11-10T11:00:00" }
```

에러: 404 `PRODUCT_NOT_FOUND`, 400 `INVALID_INPUT`(pno가 숫자가 아님)

### `GET /api/products`

| 파라미터 | 기본값 | 설명 |
| --- | --- | --- |
| page | 1 | **1부터 시작**. 0이나 음수는 **1로 조정** |
| size | 10 | **최대 20**. 20 초과는 20, 1 미만은 10으로 조정 |
| keyword | 없음 | 상품명 또는 설명에 포함 (대소문자 무시) |

정렬은 `pno desc`(최신 등록순) 고정. 실제 적용된 page/size는 응답의 `page`, `size`로 확인합니다.

목록 항목은 원본 이미지 대신 **첫 번째 이미지의 썸네일**(최대 300px JPEG)만 담습니다. 원본은 단건 조회의 `upload1~3`.

```json
{ "content": [ { "pno": 449, "pname": "무선 마우스", "pdesc": "...", "price": 12000, "onSale": true, "stock": 5,
                 "thumbnail": "https://…/storage/v1/object/public/images/products/thumbs/28d00fd0-….jpg",
                 "imageCount": 2, "createdAt": "2026-11-12T14:39:01" } ],
  "page": 1, "size": 10, "totalElements": 21, "totalPages": 3, "hasNext": true }
```

마지막 페이지를 넘기면 `content`는 빈 배열입니다.

에러: 400 `INVALID_INPUT`(page/size가 숫자가 아님)

### `PUT /api/products/{pno}`

`multipart/form-data`로 보냅니다. 수정 항목: 상품명, 판매여부, 재고, 이미지.

| 파트 | Content-Type | 필수 | 설명 |
| --- | --- | --- | --- |
| `product` | `application/json` | O | `{"pname","onSale","stock","version","keepImages"}` |
| `images` | `image/*` | X | 새 이미지 0~3개 |

- `version`: 조회했던 값. 다르면 409 `PRODUCT_CONFLICT`
- `keepImages`: 유지할 기존 이미지의 `upload` 값(URL 또는 `{uuid}.{확장자}`). **생략하면 모두 유지**, `[]`이면 모두 삭제
- 최종 이미지 = 유지 이미지(keepImages 순서) + 새 이미지, 최대 3개
- 처리 순서: 새 이미지 업로드 → DB 수정 → 커밋 후 유지하지 않은 기존 이미지 삭제. DB 수정이 실패하면 새 이미지만 삭제되고 기존 이미지는 그대로

```bash
curl -X PUT http://localhost:8080/api/products/449 \
  -F 'product={"pname":"무선 마우스 V2","onSale":true,"stock":7,"version":0,"keepImages":["28d00fd0-c6ee-453f-8e6c-0584d806f8e7.png"]};type=application/json' \
  -F 'images=@new.png'
```

응답 200: 수정된 상품 객체 (version +1).

에러: 400 `INVALID_INPUT`(빈 상품명, 이 상품의 이미지가 아닌 keepImages, 중복), 400 `INVALID_STOCK`, 400 `INVALID_FILE`(이미지 오류, 유지+새 이미지 3개 초과), 404 `PRODUCT_NOT_FOUND`, 409 `PRODUCT_CONFLICT`, 502 `STORAGE_ERROR`

### `DELETE /api/products/{pno}`

응답 204 (본문 없음). DB 삭제가 커밋된 뒤 upload1~3 이미지를 Storage에서 삭제합니다. DB 삭제가 실패하면 이미지는 남습니다.

에러: 404 `PRODUCT_NOT_FOUND`, 409 `PRODUCT_CONFLICT`, 400 `INVALID_INPUT`(pno가 숫자가 아님)

참고: Public URL은 CDN 캐시 때문에 삭제 직후 잠시 이미지가 보일 수 있습니다.

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
