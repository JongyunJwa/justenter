package ai.fassto.fms.partner_api.global.error

import ai.fassto.fms.partner_api.global.webClient.imweb.response.ImwebError

class ImwebApiException (imwebError: ImwebError) : RuntimeException(imwebError.msg) {
    val code: Int = imwebError.code
    val httpCode: Int = imwebError.httpCode
    override val message: String = imwebError.msg
}