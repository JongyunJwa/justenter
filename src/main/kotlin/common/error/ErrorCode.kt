package justenter.common.error

enum class ErrorCode(val code: Int, val message: String) {
    UNKNOWN_ERROR(9999, "확인되지 않은 오류입니다."),
    INVALID_AUTHORIZE_AUTH(401, "잘못된 인증 방식입니다."),
    NON_REQUEST_VALUES(400, "필수 요청 변수가 없습니다."),
    CHECK_REQUEST_VALUES(400, "요청 변수의 형식이 잘못됬습니다."),
    MISSING_REQUEST_BODY(400, "REQUEST BODY 값이 없습니다."),
    MISSING_REQUEST_METHOD(405, "REQUEST METHOD가 다릅니다."),
    NOT_FOUND(404, "클라이언트가 요청한 자원이 존재하지 않습니다."),
    //500 INTERNAL SERVER ERROR
    INTERNAL_SERVER_ERROR(500, "서버 에러가 발생했습니다."),
    NAVER_NO_ORDER(400, " 스마트스토어 / 윈도 에서 주문수집 할 정보가 없습니다."),
    NO_ORDER(400, " 주문수집 할 정보가 없습니다."),
    NOT_FOUND_ACCESSTOKEN(401, "사용할 엑세스토큰이 없습니다. 다시 인증하여 주시기 바랍니다."),
    REDIS_LOCK (429,"현재 작업 진행중입니다. 잠시후 재시도 부탁드립니다."),
    NONE_REDIS_LOCK (429,"Redis Lock 이 없습니다."),
    ORDER_BATCH_TIME_OUT(408,"주문수집 배치 타임아웃"),
    ORDER_BATCH_ERROR(500,"주문수집 배치 에러"),
    STOPPED_CUSTOMER(400, "출고정지 고객사입니다."),
    NO_GOD_MAP(400, "상품미연결 주문번호=[%s] 상품코드=[%s] 품목코드=[%s]"),
    CONNECT_TEST_ERROR(500, "쇼핑몰 연동 테스트에 실패하였습니다."),
}