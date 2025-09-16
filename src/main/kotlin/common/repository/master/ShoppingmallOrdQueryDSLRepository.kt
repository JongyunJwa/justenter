package justenter.common.repository.master

import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ShoppingmallOrdQueryDSLRepository(
    private val queryFactory : JPAQueryFactory
) {

    // tb_shoppingmall_ord에 등록되어 있는 출고요청번호 기준으로 데이터를 조회한다. (삽링커 주문은 주문번호가 다른데 묶일 수 있음)
    fun findSlipNo(cstCd: String, whCd: String, slipNoList: List<String>): List<ShoppingmallOrd> {
        if (slipNoList.isEmpty()) return emptyList()

        return slipNoList.chunked(300).asSequence()
            .flatMap {
                queryFactory.select(shoppingmallOrd)
                    .from(shoppingmallOrd)
                    .where(shoppingmallOrd.whCd.eq(whCd))
                    .where(shoppingmallOrd.cstCd.eq(cstCd))
                    .where(shoppingmallOrd.outOrdSlipNo.`in`(it))
                    .fetch()
            }.toList()
    }

    // tb_shoppingmall_ord에 등록되어 있는 주문번호 기준으로 데이터를 조회한다.
    fun findOrdNo(cstCd: String, mallId: String, ordNoList: List<String>): List<ShoppingmallOrd> {
        if (ordNoList.isEmpty()) return emptyList()

        return ordNoList.chunked(300).asSequence()
            .flatMap {
                queryFactory.select(shoppingmallOrd)
                    .from(shoppingmallOrd)
                    .where(shoppingmallOrd.cstCd.eq(cstCd))
                    .where(shoppingmallOrd.serviceCode.eq(mallId))
                    .where(shoppingmallOrd.shopOrderNo.`in`(it))
                    .fetch()
            }.toList()
    }

    // 송장 등록해야될 주문조회
    fun findInvoiceOrdList(cstCd: String, mallId: String): List<ShoppingmallOrd> {
        val wrkStatList = listOf(
            ShopGatewayConstStat.PROCESS_STATUS_REG_ORDER.code, // 기존 샵링커 송장 전송을 위해 넣어둠 기존 샵링커는 주문 수집 시 상태값이 10이다.
            ShopGatewayConstStat.PROCESS_STATUS_PRODUCT_READY_1.code,
            ShopGatewayConstStat.PROCESS_STATUS_PRODUCT_READY_2.code,
        )
        return queryFactory.select(shoppingmallOrd)
            .from(shoppingmallOrd)
            .where(shoppingmallOrd.cstCd.eq(cstCd))
            .where(shoppingmallOrd.serviceCode.eq(mallId))
            .where(shoppingmallOrd.wrkStat.`in`(wrkStatList))
            .where(shoppingmallOrd.updTime.after(LocalDateTime.now().minusDays(7)))
            .fetch()
    }

    // tb_shoppingmall_ord에 등록되어 있는 주문번호가 있는지 확인한다. 수기 등록된 주문 주문 수집 안하도록 하기 위해서
    fun findShopOrderNo(cstCd: String, whCd: String, shopOrderNoList: List<String>): List<ShoppingmallOrd> {
        if (shopOrderNoList.isEmpty()) return emptyList()

        return shopOrderNoList.chunked(300).asSequence()
            .flatMap {
                queryFactory.select(shoppingmallOrd)
                    .from(shoppingmallOrd)
                    .where(shoppingmallOrd.cstCd.eq(cstCd))
                    .where(shoppingmallOrd.whCd.eq(whCd))
                    .where(shoppingmallOrd.shopOrderNo.`in`(it))
                    .fetch()
            }.toList()
    }
}