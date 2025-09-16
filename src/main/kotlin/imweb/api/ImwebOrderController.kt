package justenter.imweb.api

import ai.fassto.fms.partner_api.domain.imweb.service.ImwebOrderService
import ai.fassto.fms.partner_api.global.common.enums.YesNoType
import ai.fassto.fms.partner_api.global.common.redisLock.RedisConst.IMWEB_BATCH_ORDER_LOCK_NAME
import ai.fassto.fms.partner_api.global.common.redisLock.RedisConst.IMWEB_MANUAL_ORDER_LOCK_NAME
import ai.fassto.fms.partner_api.global.common.redisLock.RedisLock
import ai.fassto.fms.partner_api.global.dto.request.OrderRequest
import ai.fassto.fms.partner_api.global.error.ApiException
import ai.fassto.fms.partner_api.global.error.ErrorCode
import com.fss.gateway.shop.entity.ShopOrderRegistResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.commons.lang3.StringUtils
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name="아임웹 주문 수집 API", description = "아임웹 주문 수집 API")
@RestController
@RequestMapping("imweb/api/v1/order/order")
class ImwebOrderController(
    private val redisLock: RedisLock,
    private val imwebOrderService: ImwebOrderService,
) {
    /*
    * 수동 주문 수집 요청
     */
    @Operation(summary = "수동 주문 수집")
    @PostMapping("regist")
    fun register(@RequestBody orderRequest: OrderRequest): List<ShopOrderRegistResponse>
        = redisLock.tryLockWith(lockName = IMWEB_MANUAL_ORDER_LOCK_NAME+orderRequest.cstCd) {
        if (StringUtils.isEmpty(orderRequest.cstCd)) throw ApiException(ErrorCode.CHECK_REQUEST_VALUES)
        imwebOrderService.register(orderRequest.cstCd, orderRequest.mallIdList, null)
    }

    /*
    * 자동 주문 수집 요청
     */
    @PostMapping("registMulti")
    @Operation(summary = "자동 주문 수집")
    fun orderBatch() : List<ShopOrderRegistResponse>
            = redisLock.tryLockWith(lockName = IMWEB_BATCH_ORDER_LOCK_NAME) { imwebOrderService.register(null, null, YesNoType.Y) }

}