package com.example.sample.product.repository.search;

import com.example.sample.product.dto.ProductSearchCondition;
import com.example.sample.product.entity.ProductEntity;
import com.example.sample.product.entity.QProductEntity;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Data가 이름 규칙(ProductRepository + Impl 또는 ProductSearch + Impl)으로 찾아 연결합니다.
 */
@RequiredArgsConstructor
public class ProductSearchImpl implements ProductSearch {

    private static final QProductEntity product = QProductEntity.productEntity;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ProductEntity> search(ProductSearchCondition condition, Pageable pageable) {
        BooleanExpression[] where = {
                keywordContains(condition.keyword()),
                priceGoe(condition.minPrice()),
                priceLoe(condition.maxPrice())
        };

        List<ProductEntity> content = queryFactory
                .selectFrom(product)
                .where(where)
                .orderBy(orderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // count 쿼리는 필요할 때만 실행 (첫 페이지에서 결과가 페이지 크기보다 적으면 생략)
        return PageableExecutionUtils.getPage(content, pageable, () -> {
            Long total = queryFactory
                    .select(product.count())
                    .from(product)
                    .where(where)
                    .fetchOne();
            return total == null ? 0L : total;
        });
    }

    // lower(pname) like '%kw%' -> pg_trgm GIN 인덱스(idx_product_pname_trgm) 사용
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String kw = keyword.trim();
        return product.pname.containsIgnoreCase(kw).or(product.pdesc.containsIgnoreCase(kw));
    }

    private BooleanExpression priceGoe(Integer minPrice) {
        return minPrice == null ? null : product.price.goe(minPrice);
    }

    private BooleanExpression priceLoe(Integer maxPrice) {
        return maxPrice == null ? null : product.price.loe(maxPrice);
    }

    // 허용된 필드만 정렬에 사용 (임의 문자열로 정렬 컬럼을 지정하지 못하게 화이트리스트 처리)
    private OrderSpecifier<?>[] orderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order o : sort) {
            Order direction = o.isAscending() ? Order.ASC : Order.DESC;
            switch (o.getProperty()) {
                case "pno" -> orders.add(new OrderSpecifier<>(direction, product.pno));
                case "pname" -> orders.add(new OrderSpecifier<>(direction, product.pname));
                case "price" -> orders.add(new OrderSpecifier<>(direction, product.price));
                case "createdAt" -> orders.add(new OrderSpecifier<>(direction, product.createdAt));
                default -> { /* 허용되지 않은 정렬 필드는 무시 */ }
            }
        }
        if (orders.isEmpty()) {
            orders.add(product.pno.desc()); // 기본: 최신 등록순
        }
        return orders.toArray(OrderSpecifier[]::new);
    }
}
