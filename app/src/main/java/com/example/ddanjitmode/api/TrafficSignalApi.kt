package com.example.ddanjitmode.api

import com.example.ddanjitmode.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ==========================================
// 1. 공공데이터포털(행안부/KLID) 규격 맞춤형 DTO 모델
// (공공데이터 JSON 특유의 response -> body -> items -> item 계층 래핑 반영)
// ==========================================

// 교차로 정보 응답 래퍼
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
    val itstId: String,   // 교차로 식별 ID
    val itstNm: String,   // 교차로명
    val la: Double,       // 위도 (Latitude)
    val lo: Double        // 경도 (Longitude)
)

// 신호 정보 응답 래퍼
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

data class SignalItem(
    val itstId: String,   // 교차로 식별 ID
    val sgId: String,     // 신호등 ID
    val color: String,    // 현재 점등 색상 ("RED", "GREEN", "YELLOW" 등)
    val tr: Int           // 신호 잔여 시간 (Time Remaining, 초 단위)
)

data class ResponseHeader(
    val resultCode: String,
    val resultMsg: String
)

// ==========================================
// 2. Retrofit2 API 인터페이스 정의 (실제 Operation 매핑)
// ==========================================
interface TrafficSignalApi {

    /**
     * 교차로 목록 조회 API (getItstList)
     * 전체 또는 특정 지자체의 교차로 위경도 및 식별 ID 목록을 가져옵니다.
     */
    @GET("getItstList")
    suspend fun getItstList(
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("type") responseType: String = "json"
    ): IntersectionListResponse

    /**
     * 신호등 실시간 잔여시간 정보 조회 API (getSgList)
     * 특정 교차로 ID를 기반으로 현재 점등된 색상과 남은 잔여 초 시간을 가져옵니다.
     */
    @GET("getSgList")
    suspend fun getSgList(
        @Query("itstId") intersectionId: String,
        @Query("type") responseType: String = "json"
    ): SignalListResponse
}

// ==========================================
// 3. Retrofit 클라이언트 싱글톤 빌더
// ==========================================
object TrafficSignalApiClient {
    private const val BASE_URL = "https://apis.data.go.kr/B551982/rti/" // 행정안전부 교통안전 신호등 실시간 정보 Endpoint

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
