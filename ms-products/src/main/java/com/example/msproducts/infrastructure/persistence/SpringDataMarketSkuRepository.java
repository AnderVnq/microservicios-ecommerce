package com.example.msproducts.infrastructure.persistence;

import com.example.msproducts.application.port.out.ResolvedMarketSku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface SpringDataMarketSkuRepository extends JpaRepository<MarketSkuJpaEntity, Long> {

    List<MarketSkuJpaEntity> findByVariantIdInOrderByIdAsc(Collection<Long> variantIds);

    @Query("""
            select new com.example.msproducts.application.port.out.ResolvedMarketSku(m.marketId, m.marketSku, v.sku)
            from MarketSkuJpaEntity m join VariantJpaEntity v on v.id = m.variantId
            where m.marketId in :marketIds and m.marketSku in :marketSkus""")
    List<ResolvedMarketSku> findResolved(Collection<Long> marketIds, Collection<String> marketSkus);
}
