package ai.fassto.fms.partner_api.global.error

open class ApiException(errorCode: ErrorCode) : RuntimeException(errorCode.message) {
    override val message: String = errorCode.message
    val code: Int = errorCode.code
}