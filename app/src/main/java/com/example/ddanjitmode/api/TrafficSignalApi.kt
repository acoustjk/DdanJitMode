package com.example.ddanjitmode.api

import com.example.ddanjitmode.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// =======================================================
// 1. 공공데이터포털(행안부/KLID) 실시간 C-ITS 규격 데이터 모델
// =======================================================

// 교차로 정보 응답 래퍼 (crsrd_map_info)
data class IntersectionListResponse(
    val response: IntersectionResponseContent
)

data class IntersectionResponseContent(
    val header: ResponseHeader,
    val body: IntersectionResponseBody?
)

data class IntersectionResponseBody(
    val items: IntersectionItems?,
    val totalCount: Int
)

data class IntersectionItems(
    val item: List<IntersectionItem>?
)

data class IntersectionItem(
    val crsrdId: String,       // 교차로 고유 식별 ID
    val crsrdNm: String,       // 교차로명 (한글)
    val mapCtptIntLat: String, // 위도 (Latitude, String으로 들어오므로 변환 필요)
    val mapCtptIntLot: String, // 경도 (Longitude, String으로 들어오므로 변환 필요)
    val lclgvNm: String?       // 관할 지자체명 (예: 서울특별시, 양주시)
)

// 실시간 신호 잔여 시간 응답 래퍼 (tl_drct_info)
data class SignalListResponse(
    val response: SignalResponseContent
)

data class SignalResponseContent(
    val header: ResponseHeader,
    val body: SignalResponseBody?
)

data class SignalResponseBody(
    val items: SignalItems?,
    val totalCount: Int
)

data class SignalItems(
    val item: List<SignalItem>?
)

// C-ITS 8개 방향 신호 상태/잔여시간 데이터 매핑 클래스
data class SignalItem(
    val crsrdId: String,
    
    // [북향 신호] nt (North)
    val ntStsgRmndCs: String?, // 직진 잔여시간 (센티초)
    val ntStsgSttsNm: String?, // 직진 상태 (stop-And-Remain / protected-Movement-Allowed)
    val ntLtsgRmndCs: String?, // 좌회전 잔여시간 (센티초)
    val ntLtsgSttsNm: String?, // 좌회전 상태
    
    // [동향 신호] et (East)
    val etStsgRmndCs: String?,
    val etStsgSttsNm: String?,
    val etLtsgRmndCs: String?,
    val etLtsgSttsNm: String?,
    
    // [남향 신호] st (South)
    val stStsgRmndCs: String?,
    val stStsgSttsNm: String?,
    val stLtsgRmndCs: String?,
    val stLtsgSttsNm: String?,
    
    // [서향 신호] wt (West)
    val wtStsgRmndCs: String?,
    val wtStsgSttsNm: String?,
    val wtLtsgRmndCs: String?,
    val wtLtsgSttsNm: String?,

    val totDt: String?        // 수집 기준 시각
) {
    // ----------------------------------------------------
    // 실시간 다중 신호 데이터를 로컬 디바이스 UI 규격으로 정제하는 헬퍼 함수
    // ----------------------------------------------------

    // 활성화된 방향의 직진 신호 남은 시간 (센티초 -> 초 단위 변환)
    fun getActiveStraightTime(): Int {
        val rawTime = listOf(ntStsgRmndCs, etStsgRmndCs, stStsgRmndCs, wtStsgRmndCs)
            .firstOrNull { !it.isNullOrEmpty() }
        val centiseconds = rawTime?.toIntOrNull() ?: 0
        return if (centiseconds > 0) (centiseconds / 10) else 0
    }

    // 활성화된 방향의 직진 신호 주행 가능 여부
    fun isStraightGreen(): Boolean {
        val status = listOf(ntStsgSttsNm, etStsgSttsNm, stStsgSttsNm, wtStsgSttsNm)
            .firstOrNull { !it.isNullOrEmpty() }
        return status == "protected-Movement-Allowed" || status == "permissive-Movement-Allowed"
    }

    // 활성화된 방향의 좌회전 신호 남은 시간 (센티초 -> 초 단위 변환)
    fun getActiveLeftTime(): Int {
        val rawTime = listOf(ntLtsgRmndCs, etLtsgRmndCs, stLtsgRmndCs, wtLtsgRmndCs)
            .firstOrNull { !it.isNullOrEmpty() }
        val centiseconds = rawTime?.toIntOrNull() ?: 0
        return if (centiseconds > 0) (centiseconds / 10) else 0
    }

    // 활성화된 방향의 좌회전 신호 주행 가능 여부
    fun isLeftGreen(): Boolean {
        val status = listOf(ntLtsgSttsNm, etLtsgSttsNm, stLtsgSttsNm, wtLtsgSttsNm)
            .firstOrNull { !it.isNullOrEmpty() }
        return status == "protected-Movement-Allowed" || status == "permissive-Movement-Allowed"
    }
}

data class ResponseHeader(
    val resultCode: String?,
    val resultMsg: String?
)

// =======================================================
// 2. Retrofit2 API 인터페이스 정의 (실제 Operation 매핑)
// =======================================================
interface TrafficSignalApi {

    /**
     * 교차로 지도 정보 조회 API (crsrd_map_info)
     */
    @GET("crsrd_map_info")
    suspend fun getItstList(
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 5000,
        @Query("type") responseType: String = "json"
    ): IntersectionListResponse

    /**
     * 신호제어기 신호잔여시간 정보 조회 API (tl_drct_info)
     */
    @GET("tl_drct_info")
    suspend fun getSgList(
        @Query("crsrdId") intersectionId: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 10,
        @Query("type") responseType: String = "json"
    ): SignalListResponse
}

// ==========================================
// 3. Retrofit 클라이언트 싱글톤 빌더
// ==========================================
object TrafficSignalApiClient {
    private const val BASE_URL = "http://apis.data.go.kr/B551982/rti/" // HTTP 호출 404 차단 목적 http 변경

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val originalUrl = originalRequest.url

                // URL 쿼리에 공공데이터 ServiceKey 자동 첨부
                val urlWithKey = originalUrl.newBuilder()
                    .addQueryParameter("serviceKey", BuildConfig.TRAFFIC_SIGNAL_API_KEY)
                    .build()

                val newRequest = originalRequest.newBuilder()
                    .url(urlWithKey)
                    .build()

                chain.proceed(newRequest)
            }
            .build()
    }

    val service: TrafficSignalApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrafficSignalApi::class.java)
    }
}
