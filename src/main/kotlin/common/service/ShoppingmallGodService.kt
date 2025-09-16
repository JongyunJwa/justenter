package justenter.common.service

import ai.fassto.fms.partner_api.domain.cafe24.repository.slave.queryDSL.Cafe24GodQueryDSLRepository
import ai.fassto.fms.partner_api.domain.entity.FmsGodCd
import ai.fassto.fms.partner_api.domain.entity.primaryKey.Cafe24GodId
import ai.fassto.fms.partner_api.domain.entity.primaryKey.ShoppingmallGodId
import ai.fassto.fms.partner_api.global.common.repository.slave.queryDSL.ShoppingmallGodQueryDSLRepository
import ai.fassto.fms.partner_api.global.webClient.cafe24.response.order.Cafe24Order
import ai.fassto.fms.partner_api.global.webClient.imweb.response.order.ImwebOrder
import ai.fassto.fms.partner_api.global.webClient.shoplinker.response.order.ShopLinkerOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShoppingmallGodService (
        private val shoppingmallGodQueryDSLRepository: ShoppingmallGodQueryDSLRepository
)  {

    @Transactional(readOnly = true)
    fun findShopLinkerOrderGod(cstCd: String, mallId: String, shopLinkerOrderList: List<ShopLinkerOrder>): Map<String, FmsGodCd> {
        val shoppingmallGodIdList : MutableList<ShoppingmallGodId> = mutableListOf()

        shopLinkerOrderList.forEach{
            shoppingmallGodIdList.add(
                ShoppingmallGodId(
                    cstCd = cstCd,
                    mallId = mallId,
                    mallGoodsNo = it.getMallGoodsNo(),
                    mallOptionCd = it.getMallOptionCd()
                ))
        }

        return shoppingmallGodIdList.asSequence()
            .distinct()
            .chunked(300)
            .flatMap { shoppingmallGodQueryDSLRepository.findShoppingmallGodList(it) }
            .toList()
            .associateBy { it.makeMappingKey() }
    }

    @Transactional(readOnly = true)
    fun findImwebOrderGod(cstCd: String, mallId: String, imwebOrderList: List<ImwebOrder>): Map<String, FmsGodCd> {
        val shoppingmallGodIdList : MutableList<ShoppingmallGodId> = mutableListOf()

        imwebOrderList.forEach{
            it.getProductList().forEach {
                shoppingmallGodIdList.add(
                    ShoppingmallGodId(
                        cstCd = cstCd,
                        mallId = mallId,
                        mallGoodsNo = it.id,
                        mallOptionCd = it.optionCd
                    ))
            }
        }

        return shoppingmallGodIdList.asSequence()
            .distinct()
            .chunked(300)
            .flatMap { shoppingmallGodQueryDSLRepository.findShoppingmallGodList(it) }
            .toList()
            .associateBy { it.makeMappingKey() }
    }
}