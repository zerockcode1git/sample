-- 상품 검색용 인덱스. Hibernate가 테이블을 만든 뒤 실행됩니다 (defer-datasource-initialization).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 상품명/설명 부분 검색 (lower(col) LIKE '%kw%') 용 trigram GIN 인덱스
CREATE INDEX IF NOT EXISTS idx_product_pname_trgm ON tbl_product USING gin (lower(pname) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_product_pdesc_trgm ON tbl_product USING gin (lower(pdesc) gin_trgm_ops);
