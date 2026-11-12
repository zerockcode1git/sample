# curl로 상품 CRUD 테스트

터미널 하나에서 위에서부터 순서대로 실행하면 등록 → 조회 → 수정 → 삭제를 모두 확인할 수 있습니다. 앞 단계에서 저장한 변수(`PNO`, `VERSION` 등)를 다음 단계가 사용합니다.

API 상세는 [API 명세](api.md), 브라우저에서 확인하려면 Swagger UI(`http://localhost:8080/swagger-ui.html`)를 사용합니다.

## 0. 준비

```bash
./gradlew bootRun          # 다른 터미널에서 서버 실행
```

```bash
BASE=http://localhost:8080/api/products

# 테스트할 이미지 3개를 현재 폴더에 준비합니다 (3MB 미만, jpg/jpeg/png/gif/webp).
# 이미지가 없으면 아래 명령으로 작은 PNG를 만들 수 있습니다 (macOS 기본 python3 사용).
python3 - <<'EOF'
import zlib, struct
def png(name, rgb):
    raw = b''.join(b'\x00' + bytes(rgb) * 50 for _ in range(50))
    chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(name, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 50, 50, 8, 2, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))
png('front.png', (220, 60, 60)); png('back.png', (60, 120, 220)); png('new.png', (60, 180, 90))
EOF
```

응답을 보기 좋게 보려고 `jq`를 사용합니다 (macOS 기본 포함, 없으면 `brew install jq`).

## 1. 등록 (Create)

`multipart/form-data`로 상품 정보(`product`, JSON)와 이미지(`images`, 0~3개)를 보냅니다. `product` 파트에는 반드시 `;type=application/json`을 붙입니다.

```bash
# 이미지 2개와 함께 등록 → 201
RES=$(curl -s -X POST $BASE \
  -F 'product={"pname":"curl 테스트 마우스","pdesc":"무선 2.4GHz","price":25000,"onSale":true,"stock":10};type=application/json' \
  -F 'images=@front.png' \
  -F 'images=@back.png')
echo "$RES" | jq

PNO=$(echo "$RES" | jq -r '.pno')
VERSION=$(echo "$RES" | jq -r '.version')
echo "PNO=$PNO VERSION=$VERSION"
```

응답에서 `img1~3`은 원본 파일 이름, `upload1~3`은 이미지 주소(Public URL)입니다.

```bash
# 이미지 없이 등록
curl -s -X POST $BASE \
  -F 'product={"pname":"이미지 없는 상품","pdesc":"설명","price":1000,"onSale":true,"stock":1};type=application/json' | jq
```

## 2. 조회 (Read)

```bash
# 단건 조회 → 200
curl -s $BASE/$PNO | jq

# 이미지 주소를 브라우저로 열어 보기 (macOS)
open "$(curl -s $BASE/$PNO | jq -r '.upload1')"
```

```bash
# 목록: page는 1부터, size 기본 10(최대 20), 최신 등록순. 이미지는 첫 번째 이미지의 썸네일만
curl -s "$BASE?page=1&size=5" | jq '{page, size, totalElements, totalPages, hasNext, pnos: [.content[].pno]}'
curl -s "$BASE?size=3" | jq '.content[] | {pno, pname, thumbnail, imageCount}'

# 범위 밖 값은 조정: page 0/음수 → 1, size 20 초과 → 20, 1 미만 → 10
curl -s "$BASE?page=-1&size=100" | jq '{page, size, count: (.content | length)}'

# 검색: 상품명 또는 설명에 포함 (한글은 URL 인코딩 필요 → --data-urlencode 사용)
curl -s -G $BASE --data-urlencode "keyword=마우스" | jq '.content[] | {pno, pname, pdesc}'
curl -s -G $BASE --data-urlencode "keyword=2.4GHz" | jq '.totalElements'
```

## 3. 수정 (Update)

`PUT`, `multipart/form-data`. `version`은 조회한 값을 그대로 보냅니다. 이미지는 `keepImages`로 유지할 기존 이미지를 고르고, 새 이미지는 `images`로 추가합니다.

| keepImages | 의미 |
| --- | --- |
| 생략 | 기존 이미지 모두 유지 |
| `["<upload 값>", ...]` | 적은 이미지만 이 순서로 유지, 나머지는 삭제 |
| `[]` | 기존 이미지 모두 삭제 |

최종 이미지 = 유지 이미지 + 새 이미지, 최대 3개.

```bash
# 3-1. 상품 정보만 수정 (keepImages 생략 → 이미지 그대로) → 200, version +1
RES=$(curl -s -X PUT $BASE/$PNO \
  -F "product={\"pname\":\"curl 테스트 마우스 V2\",\"onSale\":true,\"stock\":7,\"version\":$VERSION};type=application/json")
echo "$RES" | jq '{pname, stock, version, img1, img2}'
VERSION=$(echo "$RES" | jq -r '.version')
```

```bash
# 3-2. 두 번째 이미지(back.png)만 유지 + 새 이미지 추가 → [back.png, new.png], front.png는 Storage에서 삭제
KEEP=$(curl -s $BASE/$PNO | jq -r '.upload2')       # URL 또는 {uuid}.png 둘 다 가능
RES=$(curl -s -X PUT $BASE/$PNO \
  -F "product={\"pname\":\"curl 테스트 마우스 V2\",\"onSale\":true,\"stock\":7,\"version\":$VERSION,\"keepImages\":[\"$KEEP\"]};type=application/json" \
  -F 'images=@new.png')
echo "$RES" | jq '{version, img1, img2, img3, upload1, upload2}'
VERSION=$(echo "$RES" | jq -r '.version')
```

```bash
# 3-3. 이미지 모두 삭제
RES=$(curl -s -X PUT $BASE/$PNO \
  -F "product={\"pname\":\"curl 테스트 마우스 V2\",\"onSale\":false,\"stock\":0,\"version\":$VERSION,\"keepImages\":[]};type=application/json")
echo "$RES" | jq '{version, onSale, img1, upload1}'
VERSION=$(echo "$RES" | jq -r '.version')
```

## 4. 삭제 (Delete)

DB 삭제가 성공하면 업로드된 이미지도 Storage에서 삭제됩니다.

```bash
# → 204 (본문 없음)
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE $BASE/$PNO

# 삭제 확인 → 404
curl -s $BASE/$PNO | jq
```

## 5. 에러 확인

모든 에러는 `{status, code, message}` 형식입니다.

```bash
# 404 없는 상품
curl -s $BASE/999999999 | jq

# 400 pno가 숫자가 아님
curl -s $BASE/abc | jq

# 400 page/size가 숫자가 아님 (범위 밖 숫자는 오류 대신 조정됨)
curl -s "$BASE?page=abc" | jq

# 400 빈 상품명
curl -s -X POST $BASE -F 'product={"pname":"","price":1000,"onSale":true,"stock":1};type=application/json' | jq

# 400 음수 재고 (INVALID_STOCK)
curl -s -X POST $BASE -F 'product={"pname":"재고오류","price":1000,"onSale":true,"stock":-1};type=application/json' | jq

# 400 이미지가 아닌 파일 (INVALID_FILE)
echo "hello" > memo.txt
curl -s -X POST $BASE \
  -F 'product={"pname":"파일오류","price":1000,"onSale":true,"stock":1};type=application/json' \
  -F 'images=@memo.txt;type=text/plain' | jq

# 400 이미지 4개 (INVALID_FILE)
curl -s -X POST $BASE \
  -F 'product={"pname":"개수오류","price":1000,"onSale":true,"stock":1};type=application/json' \
  -F 'images=@front.png' -F 'images=@back.png' -F 'images=@new.png' -F 'images=@front.png' | jq

# 400 product 파트 누락
curl -s -X POST $BASE -F 'images=@front.png' | jq

# 400 product 파트에 type=application/json 누락
curl -s -X POST $BASE -F 'product={"pname":"형식오류","price":1000,"onSale":true,"stock":1}' | jq
```

```bash
# 409 버전 불일치: 같은 version으로 두 번 수정하면 두 번째가 거부됨
RES=$(curl -s -X POST $BASE -F 'product={"pname":"버전 테스트","price":1000,"onSale":true,"stock":1};type=application/json')
P=$(echo "$RES" | jq -r '.pno')
curl -s -o /dev/null -w "첫 번째 수정 %{http_code}\n" -X PUT $BASE/$P \
  -F 'product={"pname":"먼저 수정","onSale":true,"stock":1,"version":0};type=application/json'
curl -s -X PUT $BASE/$P \
  -F 'product={"pname":"나중 수정","onSale":true,"stock":1,"version":0};type=application/json' | jq

# 400 다른 상품의 이미지를 keepImages로 지정
curl -s -X PUT $BASE/$P \
  -F 'product={"pname":"x","onSale":true,"stock":1,"version":1,"keepImages":["not-mine.png"]};type=application/json' | jq

curl -s -o /dev/null -w "정리 %{http_code}\n" -X DELETE $BASE/$P
```

## 6. 정리

```bash
# '이미지 없는 상품' 등 테스트로 만든 상품 삭제
for p in $(curl -s -G $BASE --data-urlencode "keyword=이미지 없는 상품" | jq -r '.content[].pno'); do
  curl -s -o /dev/null -w "DELETE $p %{http_code}\n" -X DELETE $BASE/$p
done
rm -f front.png back.png new.png memo.txt
```

Swagger UI나 curl로 만든 데이터는 실제 DB와 Supabase Storage에 저장됩니다.
