package com.example.ddanjitmode

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.example.ddanjitmode.api.*
import kotlinx.coroutines.*

class SignalService : Service() {

    companion object {
        private const val TAG = "SignalService"
        private const val CHANNEL_ID = "DdanJitServiceChannel"
        private const val NOTIFICATION_ID = 1004
        private const val SPEED_THRESHOLD_KMH = 3.0 // 정차 상태를 판단할 임계 속도 (3km/h)
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var widgetManager: FloatingWidgetManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    
    private var isStoppedState = false // 차량이 정차 중인지 여부

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        widgetManager = FloatingWidgetManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        // 포그라운드 서비스 활성화를 위해 알림 생성 및 시작
        val notification = createNotification("차량 속도 감지 중입니다.")
        startForeground(NOTIFICATION_ID, notification)

        // GPS 추적 시작
        startLocationUpdates()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // 1. Android 8.0 이상용 알림 채널 설정
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "딴짓모드 서비스 채널",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    // 2. 알림 빌더
    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("딴짓모드 작동 중")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // 기본 시스템 아이콘 사용
            .setOngoing(true)
            .build()
    }

    // 알림 메시지 갱신용 Helper
    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    // 3. FusedLocationProvider Callback 설정
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processLocation(location)
                }
            }
        }
    }

    // 4. GPS 추적 요청 및 설정
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 
            2000L // 2초 간격 업데이트
        ).apply {
            setMinUpdateIntervalMillis(1000L) // 최소 1초 간격
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "위치 권한이 누락되었습니다: ${e.message}")
        }
    }

    // 5. GPS 속도에 따른 비즈니스 로직 처리
    private fun processLocation(location: Location) {
        // speed는 m/s 단위이므로 km/h 로 변환
        val speedKmh = location.speed * 3.6
        Log.d(TAG, "현재 속도: ${"%.2f".format(speedKmh)} km/h")

        if (speedKmh <= SPEED_THRESHOLD_KMH) {
            // 속도가 3km/h 이하 -> 정차 상태
            if (!isStoppedState) {
                isStoppedState = true
                updateNotification("차량이 정차했습니다. 신호 대기 모드를 실행합니다.")
                startSignalSync(location.latitude, location.longitude)
            }
        } else {
            // 속도가 3km/h 초과 -> 차량 출발 상태
            if (isStoppedState) {
                isStoppedState = false
                updateNotification("차량이 주행 중입니다.")
                stopSignalTimer()
            }
        }
    }

    // 6. 실시간 교통 신호 연동 및 카운트다운 타이머 실행 (듀얼 신호 매핑 및 시뮬레이터 적용)
    private fun startSignalSync(latitude: Double, longitude: Double) {
        // 기존 타이머가 작동 중이면 정지
        timerJob?.cancel()
        
        // 플로팅 위젯 띄우기
        widgetManager.show()

        timerJob = serviceScope.launch {
            widgetManager.updateState(
                title = "📍 검색 중...",
                straightText = "🔄 검색", isStraightGreen = false,
                leftText = "🔄 검색", isLeftGreen = false
            )

            var intersectionId: String? = null
            var intersectionName: String = ""
            
            // 1. 근처 교차로 매칭 API 호출 (전체 리스트 중 100m 이내 최인접 교차로 탐색)
            try {
                val response = withContext(Dispatchers.IO) {
                    TrafficSignalApiClient.service.getItstList(pageNo = 1, numOfRows = 5000)
                }
                val items = response.response.body?.items?.item
                if (!items.isNullOrEmpty()) {
                    var minDistance = Double.MAX_VALUE
                    var matchedItst: IntersectionItem? = null
                    
                    for (item in items) {
                        val results = FloatArray(1)
                        Location.distanceBetween(latitude, longitude, item.la, item.lo, results)
                        val distance = results[0].toDouble()
                        
                        if (distance < 100.0 && distance < minDistance) {
                            minDistance = distance
                            matchedItst = item
                        }
                    }
                    
                    if (matchedItst != null) {
                        intersectionId = matchedItst!!.itstId
                        intersectionName = matchedItst!!.itstNm
                        Log.d(TAG, "최인접 교차로 매칭 성공: $intersectionName ($intersectionId)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "교차로 목록 조회 API 실패: ${e.message}")
            }

            // 실내 테스트 또는 비-교차로 구역인 경우 듀얼 데모 시뮬레이션 모드로 매끄럽게 전환
            if (intersectionId == null) {
                Log.w(TAG, "인근에 매칭된 신호등 교차로가 없습니다. 실내 테스트용 데모 시뮬레이션 모드를 실행합니다.")
                runDemoSimulation()
                return@launch
            }

            // 2. 실시간 신호 카운트다운 및 주기적 동기화 폴링 루프 (실제 API 매핑)
            var straightTime = 0
            var straightPhase = "RED"
            var leftTime = 0
            var leftPhase = "RED"

            while (isActive && isStoppedState) {
                try {
                    // 5초 간격으로 서버에서 신호 정보 리스트 조회
                    val statusResponse = withContext(Dispatchers.IO) {
                        TrafficSignalApiClient.service.getSgList(intersectionId)
                    }
                    val items = statusResponse.response.body?.items?.item
                    if (!items.isNullOrEmpty()) {
                        // 직진 신호와 좌회전 신호를 신호등 ID(sgId) 또는 인덱스로 분리 매핑
                        val straightSignal = items.find { it.sgId.contains("S") || it.sgId.contains("straight", true) } ?: items.getOrNull(0)
                        val leftSignal = items.find { it.sgId.contains("L") || it.sgId.contains("left", true) } ?: items.getOrNull(1)

                        if (straightSignal != null) {
                            straightTime = straightSignal.tr
                            straightPhase = straightSignal.color
                        }
                        if (leftSignal != null) {
                            leftTime = leftSignal.tr
                            leftPhase = leftSignal.color
                        }
                        
                        Log.d(TAG, "실서버 듀얼 신호 동기화: $intersectionName -> [직진] ${straightPhase}(${straightTime}s) [좌회전] ${leftPhase}(${leftTime}s)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "신호 데이터 갱신 실패: ${e.message}. 로컬 연산을 계속합니다.")
                }

                // 5초 동안 1초 간격으로 로컬 카운트다운 및 화면 갱신
                val syncIntervalSeconds = 5
                for (i in 0 until syncIntervalSeconds) {
                    if (!isActive || !isStoppedState) break

                    val sText = if (straightPhase.uppercase() == "GREEN") "🟢 주행" else "🔴 $straightTime"
                    val sGreen = straightPhase.uppercase() == "GREEN"

                    val lText = if (leftPhase.uppercase() == "GREEN") "🟢 주행" else "🔴 $leftTime"
                    val lGreen = leftPhase.uppercase() == "GREEN"

                    // 플로팅 위젯 상태 갱신 (헤더에 실제 교차로명 표출)
                    widgetManager.updateState(
                        title = "📍 $intersectionName",
                        straightText = sText, isStraightGreen = sGreen,
                        leftText = lText, isLeftGreen = lGreen
                    )

                    // [추가] 단말기 알림바 실시간 로그 업데이트 (PC 연결 없이 데이터 디버깅 가능)
                    updateNotification("연동: $intersectionName | 직진: $sText | 좌회전: $lText")

                    delay(1000L)
                    if (straightTime > 0) straightTime--
                    if (leftTime > 0) leftTime--
                }
            }
        }
    }

    // 데모 시뮬레이션 모드: 직진 신호와 좌회전 신호가 각각 차이를 두고 변환하는 실제 도로 시나리오 시뮬레이션
    private suspend fun runDemoSimulation() {
        widgetManager.updateState(
            title = "📍 데모 (로딩)",
            straightText = "🔄 로딩", isStraightGreen = false,
            leftText = "🔄 로딩", isLeftGreen = false
        )
        delay(1500L)

        // 가상 시나리오: 
        // 1. 초기 상태: 둘 다 적색 (직진 10초 대기, 좌회전 15초 대기)
        // 2. 직진이 먼저 녹색등으로 전환 (직진: 주행 / 좌회전: 적색 5초 대기)
        // 3. 이후 좌회전도 녹색등으로 전환 (직진: 주행 / 좌회전: 주행)
        var sTimeLeft = 10
        var sPhase = "RED"
        
        var lTimeLeft = 15
        var lPhase = "RED"

        while (isStoppedState) {
            Log.d(TAG, "데모 API 동기화 - [직진] $sPhase(${sTimeLeft}s) [좌회전] $lPhase(${lTimeLeft}s)")

            val syncInterval = 5
            for (i in 0 until syncInterval) {
                if (!isStoppedState) break

                // 직진 신호 상태 제어
                if (sPhase == "RED") {
                    if (sTimeLeft <= 0) {
                        sPhase = "GREEN"
                        sTimeLeft = 20 // 직진 신호 20초간 지속
                    }
                } else if (sPhase == "GREEN") {
                    if (sTimeLeft <= 0) {
                        sPhase = "RED"
                        sTimeLeft = 15 // 적색 신호 재진입
                    }
                }

                // 좌회전 신호 상태 제어
                if (lPhase == "RED") {
                    if (lTimeLeft <= 0) {
                        lPhase = "GREEN"
                        lTimeLeft = 15 // 좌회전 신호 15초간 지속
                    }
                } else if (lPhase == "GREEN") {
                    if (lTimeLeft <= 0) {
                        lPhase = "RED"
                        lTimeLeft = 20 // 적색 신호 재진입
                    }
                }

                // UI에 보낼 문구 매핑
                val sText = if (sPhase == "GREEN") {
                    "🟢 주행"
                } else {
                    if (sTimeLeft in 1..3) "🟢 ${sTimeLeft}초 후!" else "🔴 $sTimeLeft"
                }
                
                val lText = if (lPhase == "GREEN") {
                    "🟢 주행"
                } else {
                    if (lTimeLeft in 1..3) "🟢 ${lTimeLeft}초 후!" else "🔴 $lTimeLeft"
                }

                widgetManager.updateState(
                    title = "📍 강남역 (데모)",
                    straightText = sText, isStraightGreen = (sPhase == "GREEN"),
                    leftText = lText, isLeftGreen = (lPhase == "GREEN")
                )

                // [추가] 단말기 알림바 실시간 로그 업데이트 (데모)
                updateNotification("데모: 강남역 | 직진: $sText | 좌회전: $lText")

                delay(1000L)
                if (sTimeLeft > 0) sTimeLeft--
                if (lTimeLeft > 0) lTimeLeft--
            }
        }
    }

    // 타이머 및 위젯 초기화
    private fun stopSignalTimer() {
        timerJob?.cancel()
        timerJob = null
        widgetManager.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        // 리소스 해제
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopSignalTimer()
        serviceScope.cancel()
    }
}
