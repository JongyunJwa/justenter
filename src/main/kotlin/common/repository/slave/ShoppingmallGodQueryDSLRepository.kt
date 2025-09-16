package justenter.common.repository.slave

import ai.fassto.fms.partner_api.domain.entity.FmsGodCd
import ai.fassto.fms.partner_api.domain.entity.QFmsGodCd
import ai.fassto.fms.partner_api.domain.entity.QGod.god
import ai.fassto.fms.partner_api.domain.entity.QShoppingmallGod.shoppingmallGod
import ai.fassto.fms.partner_api.domain.entity.primaryKey.ShoppingmallGodId
import ai.fassto.fms.partner_api.global.common.enums.YesNoType
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class ShoppingmallGodQueryDSLRepository(
    private val queryFactory : JPAQueryFactory
) {
    // tb_shoppingmall_god에 해당 상품들이 있는지 조회한다.
    fun findShoppingmallGodList(shoppingmallGodIdList: List<ShoppingmallGodId>) : List<FmsGodCd> {
        if (shoppingmallGodIdList.isEmpty()) return emptyList()
        return queryFactory.select(QFmsGodCd(god.godCd, god.cstGodCd, god.godNm, shoppingmallGod.mallGoodsNo, shoppingmallGod.mallOptionCd))
            .from(shoppingmallGod)
            .join(shoppingmallGod.god, god)
            .where(god.useYn.eq(YesNoType.Y.name))
            .where(shoppingmallGodIdList(shoppingmallGodIdList))
            .fetch()
    }

    private fun shoppingmallGodIdList(shoppingmallGodIdList: List<ShoppingmallGodId>): BooleanBuilder {
        val booleanBuilder = BooleanBuilder()

        //pull 스캔 방지
        if (shoppingmallGodIdList.isEmpty()) {
            return booleanBuilder.and(
                shoppingmallGod.mallGoodsNo.eq("")
                    .and(shoppingmallGod.mallOptionCd.eq(""))
                    .and(shoppingmallGod.cstCd.eq(""))
                    .and(shoppingmallGod.mallId.eq(""))
            )
        }

        for(godId in shoppingmallGodIdList) {
            booleanBuilder.or(
                shoppingmallGod.mallGoodsNo.eq(godId.mallGoodsNo)
                    .and(shoppingmallGod.mallOptionCd.eq(godId.mallOptionCd))
                    .and(shoppingmallGod.cstCd.eq(godId.cstCd))
                    .and(shoppingmallGod.mallId.eq(godId.mallId))
            )
        }

        return booleanBuilder
    }
}