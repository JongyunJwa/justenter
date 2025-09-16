package justenter.imweb.service

import ai.fassto.fms.partner_api.domain.entity.Shoppingmall
import ai.fassto.fms.partner_api.domain.entity.ShoppingmallErrLog
import ai.fassto.fms.partner_api.domain.imweb.enums.ImwebOrderStatus
import ai.fassto.fms.partner_api.domain.partner.dto.CustomInfo
import ai.fassto.fms.partner_api.domain.partner.service.orderApi.OrderApiService
import ai.fassto.fms.partner_api.global.common.enums.*
import ai.fassto.fms.partner_api.global.common.notice.slack.SlackMessageService
import ai.fassto.fms.partner_api.global.common.notice.slack.SlackService
import ai.fassto.fms.partner_api.global.common.redisLock.RedisConst
import ai.fassto.fms.partner_api.global.common.redisLock.RedisLock
import ai.fassto.fms.partner_api.global.common.repository.slave.queryDSL.OutOrdQueryDSLRepository
import ai.fassto.fms.partner_api.global.common.repository.slave.queryDSL.ShoppingmallCustomQueryDSLRepository
import ai.fassto.fms.partner_api.global.common.service.CstService
import ai.fassto.fms.partner_api.global.common.service.ShoppingmallErrLogService
import ai.fassto.fms.partner_api.global.common.service.ShoppingmallGodService
import ai.fassto.fms.partner_api.global.common.service.ShoppingmallOrdService
import ai.fassto.fms.partner_api.global.error.ApiException
import ai.fassto.fms.partner_api.global.error.ErrorCode
import ai.fassto.fms.partner_api.global.error.OrderApiOrderException
import ai.fassto.fms.partner_api.global.log
import ai.fassto.fms.partner_api.global.util.DateUtil
import ai.fassto.fms.partner_api.global.webClient.imweb.response.order.ImwebOrder
import ai.fassto.fms.partner_api.global.webClient.order.request.OrderApiRequest
import ai.fassto.fms.partner_api.global.webClient.order.response.OrderResult
import com.fss.gateway.shop.entity.ShopOrderRegistResponse
import com.fss.gateway.shop.exception.OpenapiException
import io.opentelemetry.api.trace.Span
import jodd.util.StringUtil
import kotlinx.coroutines.*
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

@Service
@ConfigurationProperties(prefix = "batch.imweb.order")
class ImwebOrderService(
    private val shoppingmallCustomQueryDSLRepository: ShoppingmallCustomQueryDSLRepository,
    private val outOrdQueryDSLRepository: OutOrdQueryDSLRepository,
    private val shoppingmallErrLogService: ShoppingmallErrLogService,
    private val shoppingmallOrdService: ShoppingmallOrdService,
    private val cstService: CstService,
    private val slackService: SlackService,
    private val slackMessageService: SlackMessageService,
    private val redisLock: RedisLock,
    private val orderApiService: OrderApiService,
    private val imwebApiService: ImwebApiService,
    private val imwebService: ImwebService,
    private val godService: ShoppingmallGodService,
) {
    val log = log()


    var threadCount : Int = 0
    var timeout : Long = 0

    fun register(cstCd : String?, mallIdList: List<String>?, autoOrderYn : YesNoType?) : List<ShopOrderRegistResponse> {
        log.info("아임웹 주문수집 요청 cstCd : $cstCd mallIdList : $mallIdList autoOrderYn : $autoOrderYn")

        val timeout: Long = timeout // ms
        val startTime = LocalDateTime.now()  // 코루틴 시작 시간 기록
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") // 날짜 포맷 지정

        // 고객사 정보 조회
        val shoppingmallList = shoppingmallCustomQueryDSLRepository.findShoppingmallCustomList(ShoppingmallType.IMWEB, cstCd, mallIdList, autoOrderYn)
        // 고객사 정보가 없으면 오류 처리
        if (shoppingmallList.isEmpty()) throw ApiException(ErrorCode.NOT_FOUND)

        val result : MutableList<ShopOrderRegistResponse> = mutableListOf()
        val doneOrderCstCdList : MutableList<String> = mutableListOf()
        val fixedThreadPool: ExecutorCoroutineDispatcher = Executors.newFixedThreadPool(threadCount).asCoroutineDispatcher()
        try {
            //리스트 (호출)
            runBlocking(Dispatchers.IO) {
                withContext(fixedThreadPool) {
                    val deferredList = shoppingmallList.map {
                        async {
                            result.add(redisLock.tryLockWithOrder("${RedisConst.IMWEB_ORDER}|${it.cstCd}|${it.mallId}") {
                                outBound(it)
                            })
                            doneOrderCstCdList.add(it.cstCd)
                        }
                    }
                    // 타임아웃 설정
                    val timeoutJob = withTimeoutOrNull(timeout){
                        deferredList.awaitAll()
                    }

                    // 타임아웃 체크
                    if (timeoutJob == null) {
                        val notDoneOrderCstCdList : Set<String> = shoppingmallList.asSequence().filter { !doneOrderCstCdList.contains(it.cstCd) }.map { it.cstCd }.toSet()
                        val elapsedTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - startTime.toEpochSecond(
                            ZoneOffset.UTC) // 경과 시간 계산
                        val formattedStartTime = startTime.format(formatter) // 시작 시간 포맷팅
                        val alertMessage = "주문수집 시간초과 (시작 시간: $formattedStartTime, 경과 시간: ${elapsedTime}s) 주문수집이 완료되지않은 고객사 리스트 $notDoneOrderCstCdList"
                        log.warn(alertMessage)
                        slackService.sendWarnAlarm(slackMessageService.makeErrorMessage(SlackNoticeErrorType.ORDER, cstCd?: "배치" , "주문수집 타임아웃", Span.current().spanContext.traceId, alertMessage, OmsType.IMWEB))
                    }
                }
            }
            fixedThreadPool.close()
        }catch (e: Exception) {
            fixedThreadPool.close()
            log.error("아임웹 주문수집 오류 ${e.message}", e)
            slackService.sendErrorAlarm(slackMessageService.makeErrorMessage(SlackNoticeErrorType.ORDER, cstCd?: "배치" , "주문수집 에러", Span.current().spanContext.traceId, e, OmsType.IMWEB))
            return emptyList()
        } finally {
            fixedThreadPool.close()
        }

        return result
    }

    // 고객사 별 주문 수집
    fun outBound(shoppingmall: Shoppingmall) : ShopOrderRegistResponse {
        var openApiException: OpenapiException? = null
        val orderApiResponse: MutableList<OrderResult> = mutableListOf()

        val response = ShopOrderRegistResponse(shoppingmall.cstCd, shoppingmall.shoppingmallName)
        var whCd = "" // 보상 트랜잭션에서 사용

        // 한번에 로그 저장을 위해 만들어둠
        val errLogList : MutableList<ShoppingmallErrLog> = mutableListOf()

        try {
            log.info("아임웹 주문수집 시작 cstCd : ${shoppingmall.cstCd} mallId : ${shoppingmall.mallId}")
            // 출고 정지 확인
//            if (cstService.isOutStop(shoppingmall.cstCd)) {
                // 출고 정지 고객사 로그 생성
//                errLogList.add(ShoppingmallErrLog.createStopCustomErrLog(shoppingmall))
//                throw ApiException(ErrorCode.STOPPED_CUSTOMER)
//            }

            // 센터 정보 조회
            val findWoreHouse = cstService.findWareHouse(shoppingmall.cstCd)
            whCd = findWoreHouse.whCd

            // 고객사명 입력
            response.cstNm = findWoreHouse.cstNm

            // 토큰 조회
            val token = imwebApiService.auth(shoppingmall.customerKey!!, shoppingmall.shoppingmallKey!!, shoppingmall, ShopGatewayConstStat.SHOP_API_WRK_STAT_ORDER.code)

            // 아임웹 주문 조회
            val orderList = imwebService.findOrderList(shoppingmall, token, ShopGatewayConstStat.SHOP_API_WRK_STAT_ORDER.code, shoppingmall.getOrderStatus())

            // 주문 필터링
            val filterOrderList = filterOrderList(shoppingmall, findWoreHouse, orderList)
            log.info("아임웹 최종 수집 대상 : $filterOrderList")

            // 수집할 주문이 없으면 넘어간다.
            if (filterOrderList.isEmpty()) {
                log.info("아임웹 주문수집 끝(주문X) cstCd : ${shoppingmall.cstCd} mallId : ${shoppingmall.mallId}")
                return response
            }

            // 상품 조회
            val fmsGodMap = godService.findImwebOrderGod(shoppingmall.cstCd, shoppingmall.mallId.toString(), filterOrderList)

            // OrderAPI Request 생성
            val orderApiRequestList: MutableList<OrderApiRequest> = mutableListOf()
            filterOrderList.forEach { imwebOrder ->
                try {
                    // OrderAPI 출고요청용 Request 생성
                    val orderApiRequest = OrderApiRequest.createImwebOrderApiRequest(shoppingmall, findWoreHouse, imwebOrder, fmsGodMap)

                    // 상품미연결이 없을 경우에만 넣어준다.
                    if (orderApiRequest.second.isEmpty())
                        orderApiRequestList.add(orderApiRequest.first)
                    else {
                        // 미연동 로그 생성
                        orderApiRequest.second.forEach { item ->
                            errLogList.add(ShoppingmallErrLog.createNoMapGodErrLog(shoppingmall, imwebOrder, item))
                            response.addFailShopOrderNo(
                                imwebOrder.orderNo,
                                ShopGatewayConstStat.SHOP_API_ERR_NO_GOD_MAP.code,
                                String.format(ErrorCode.NO_GOD_MAP.message, imwebOrder.orderNo, item.id, item.optionCd),
                                "",
                                "",
                                imwebOrder.delivery.address.name,
                                "",
                                item.productOrderNo,
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error("createOrderApiRequest Error", e)
                    // 알수 없는 오류 발생
                    errLogList.add(ShoppingmallErrLog.createNetworkErrLog(shoppingmall, ShopGatewayConstStat.SHOP_API_WRK_STAT_ORDER.code, ShopGatewayConstStat.SHOP_API_ERR_NETWORK.code))
                    response.addFailShopOrderNo(
                        imwebOrder.orderNo,
                        ShopGatewayConstStat.SHOP_API_ERR_NETWORK.code,
                        "",
                        "",
                        "",
                        imwebOrder.delivery.address.name,
                        "",
                        "",
                    )
                }
            }

            // 수집할 주문이 없으면 넘어간다.
            if (orderApiRequestList.isEmpty()) return response

            // OrderAPI 호출
            orderApiResponse.addAll(orderApiService.callOrderApi(shoppingmall.cstCd, shoppingmall.mallId.toString(), orderApiRequestList))

            // shoppingmallOrd 생성
            val shoppingmallOrderList = shoppingmallOrdService.createShoppingmallOrd(shoppingmall, whCd, filterOrderList, orderApiResponse, fmsGodMap)

            val successList = shoppingmallOrderList.first
            val failList = shoppingmallOrderList.second

            // 아임웹 상태값 변경 (wrk_stat가 10인 것만 처리한다.)
            val orderStatusFailList = imwebService.updateStatus(shoppingmall, token, ImwebOrderStatus.STANDBY, successList.filter { it.wrkStat == ShopGatewayConstStat.PROCESS_STATUS_REG_ORDER.code })

            // tb_shoppingmall_ord 데이터 입력
            shoppingmallOrdService.saveShoppingmallOrd(successList)

            // 상태값 변경 실패 결과 입력
            orderStatusFailList.forEach { fail ->
                // 원 주문 검색이 되면 넣어준다. (취소가 여기로 올 수 있음)
                val shoppingmallOrd = successList.find { it.getShoppingmallOrderNo() == fail.orderNo && it.shopProductOrderNo == fail.productOrderNo } ?: return@forEach

                response.addFailShopOrderNo(fail.orderNo, ShopGatewayConstStat.SHOP_API_ERR_NETWORK.code, fail.errorCode, fail.errorMessage, "")
                // 미연동내역
                errLogList.add(ShoppingmallErrLog.createNotStatusErrLog(
                    shoppingmall = shoppingmall,
                    shoppingmallOrd = shoppingmallOrd,
                    wrkStat = ShopGatewayConstStat.SHOP_API_WRK_STAT_ORDER.code,
                    shopErrCode = fail.errorCode,
                    shopErrMsg = fail.errorMessage
                ))
            }

            // 상태값 까지 정상적으로 성공한 결과 입력
            val groupSuccessList = successList
                .filter {shoppingmallOrd ->
                    orderStatusFailList.find { it.orderNo == shoppingmallOrd.getShoppingmallOrderNo() && it.productOrderNo == shoppingmallOrd.getShoppingmallProductOrderNo() } == null
                }.groupBy { it.getShoppingmallOrderNo() }

            groupSuccessList.forEach{
                val skuCount = it.value.size
                val ordCount = it.value.sumOf { it.shopOrdCnt }
                response.addSuccessShopOrder(it.key, skuCount, ordCount, it.value[0].outOrdSlipNo)
            }

            // 실패 결과 입력
            failList.forEach { (orderNo, orderResultList) ->
                val imwebOrder = filterOrderList.find { it.getFmsOrderNo() == orderNo } ?: return@forEach

                orderResultList.forEach { orderResult ->
                    response.addFailShopOrderNo(
                        imwebOrder.orderNo,
                        orderResult.getErrorCode(),
                        orderResult.resultMessage,
                        "",
                        "",
                        imwebOrder.delivery.address.name,
                        "",
                        orderResult.resultDetail?.productOrderId ?: "",
                    )
                }

                // 로그 생성
                errLogList.addAll(ShoppingmallErrLog.createImwebOrderErrLog(shoppingmall, imwebOrder, orderResultList))
            }

        } catch (e: OrderApiOrderException) {
            log.error("아임웹 주문수집 OrderAPI 오류 고객사코드 : ${shoppingmall.cstCd}", e)
            openApiException = OpenapiException(e)
            // OrderAPI 통신 중 중간에 오류가 난다면 결과값을 넣어준다.
            orderApiResponse.addAll(e.resultList)
        } catch (e: ApiException) {
            openApiException = OpenapiException(e)
        } catch (e: Exception) {
            log.error("아임웹 주문수집 오류 고객사코드 : ${shoppingmall.cstCd}", e)
            openApiException = OpenapiException(e)
            val traceId: String = Span.current().spanContext.traceId
            slackService.sendWarnAlarm(slackMessageService.makeWarningMessage(SlackNoticeErrorType.ORDER, shoppingmall.cstCd , shoppingmall.mallId.toString(), traceId, openApiException, OmsType.IMWEB))
        } finally {
            // 입력해야될 로그가 있으면 입력
            // 밖에서 하는 이유는 오류가 나도 로그는 넣어줘야 해서 밖에서 처리한다.
            shoppingmallErrLogService.saveErrorLog(errLogList)
        }

        if (openApiException != null) {
            // 보상 트랜잭션 Order API 통신 했을 경우 주문취소 호출

            val successResponseList = orderApiResponse.asSequence().filter { it.isSuccessCode() }.toList()
            if (successResponseList.isNotEmpty()) {
                orderApiService.callOrderCancelWhenError(shoppingmall.cstCd, shoppingmall.mallId.toString(), whCd, orderApiResponse, OmsType.IMWEB.loginUserId, OmsType.IMWEB.requestClient)
                log.error("아임웹 Order API 보상 트랜잭션 -> ${openApiException.message} ", openApiException)
            }

            val errorResponse = ShopOrderRegistResponse(response.cstCd, response.cstNm, response.mallName)
            errorResponse.errorCode = openApiException.errorCode
            errorResponse.errorMessage = openApiException.errorMessage
            return errorResponse
        }

        log.info("아임웹 주문수집 끝 cstCd : ${shoppingmall.cstCd} mallId : ${shoppingmall.mallId} response : $response")
        return response
    }

    private fun filterOrderList(shoppingmall: Shoppingmall, findWoreHouse: CustomInfo, imwebOrderList: List<ImwebOrder>) : List<ImwebOrder> {
        // tb_out_ord에 등록되어 있는 주문 및 tb_shoppingmall_ord에 들어가 있는 주문이 있을 경우 필터링 한다.
        val findOrdNoList : MutableList<String> = mutableListOf()
        // tb_out_ord imwebOrder에 수기 등록 시 orderNo로 고객사에서 넣기 때문에 해당값으로 조회한다.
        findOrdNoList.addAll(outOrdQueryDSLRepository.findOrdNo(shoppingmall.cstCd, findWoreHouse.whCd, imwebOrderList.asSequence().map { it.orderNo }.toList()).map { it.ordNo }.toList())
        // tb_shoppingmall_ord  imwebOrder에 fmsOrderNo로 들어가기 때문에 해당 값으로 조회한다.
        findOrdNoList.addAll(shoppingmallOrdService.findShopOrderNo(shoppingmall.cstCd, findWoreHouse.whCd, imwebOrderList.asSequence().map { it.getFmsOrderNo() }.toList()).map { it.getShoppingmallOrderNo() }.toList())

        // 위 2개의 테이블에 등록 안되어있는 주문번호만 필터링
        val filterOrderList = imwebOrderList.filter { imwebOrder -> findOrdNoList.find { it.equals(imwebOrder.orderNo) } == null }

        filterOrderList.forEach { imwebOrder ->
            // 실제 주문 수집하는 상태만 넣어주도록 수정
            imwebOrder.prodOrderList = imwebOrder.prodOrderList
                // 검색조건과 동일한 주문 상태
                .filter { it.status == shoppingmall.getOrderStatus() }
                // 클레임정보가 없는 경우에만 수집
                .filter { StringUtil.isEmpty(it.claimStatus) }
        }

        // 해당 고객사 주문 수집 타입 확인
        // 결제완료 상태의 주문은 취소허용시간 계산 (Y가 아닐때)
        val resultList = if (!YesNoType.Y.name.equals(shoppingmall.useOrderConfirmYn)) {
            // 해당 고객사가 설정한 주문 수집 시간
            val now = LocalDateTime.now().minusMinutes(shoppingmall.orderMinsAgo.toLong())
            log.info("cstCd : ${shoppingmall.cstCd} lastDateTime : $now")
            filterOrderList.filter { now.isAfter(DateUtil.getImwebDateTime(it.orderTime)) }
        } else {
            filterOrderList
        }

        // 주문에 상품주문이 없는 주문은 넘어간다.(주문즉시 클레임이 발생한 경우)
        return resultList.filter { it.prodOrderList.isNotEmpty() }
            // 먼저 들어온 주문을 먼저 내보내기 위해 주문일시로 정렬한다.
            .sortedBy { it.orderTime }
    }
}