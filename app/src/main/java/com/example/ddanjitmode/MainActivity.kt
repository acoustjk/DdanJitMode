package com.example.ddanjitmode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {

    // 1. 필요한 권한 목록 정의 (기본 위치 권한 + Android 13 이상을 위한 알림 권한)
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val postNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    // 2. 권한 요청 런처 등록
    // 기본 위치 및 알림 권한 획득 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if ((fineLocationGranted || coarseLocationGranted) && notificationGranted) {
            // 위치 권한 획득 후 Android 10(Q) 이상인 경우 백그라운드 위치 권한 추가 요청
            checkAndRequestBackgroundLocation()
        } else {
            Toast.makeText(this, "위치 정보와 알림 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 다른 앱 위에 그리기(오버레이) 권한 런처
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "오버레이 권한 허용이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 백그라운드 위치 권한 런처 (Android 10 이상)
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "백그라운드 위치 권한이 승인되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "항상 위치 허용 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.example.ddanjitmode.ui.theme.DdanJitModeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen()
                }
            }
        }
    }

    @Composable
    fun DashboardScreen() {
        var isServiceRunning by remember { mutableStateOf(isServiceRunning(this)) }
        var hasLocationPermission by remember { mutableStateOf(hasLocationPermissions()) }
        var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
        var hasBgLocationPermission by remember { mutableStateOf(hasBackgroundLocationPermission()) }

        // 화면 복귀(onResume) 시 권한 및 서비스 상태 실시간 동기화
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasLocationPermission = hasLocationPermissions()
                    hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)
                    hasBgLocationPermission = hasBackgroundLocationPermission()
                    isServiceRunning = isServiceRunning(this@MainActivity)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "딴짓모드 MVP",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "정차 중 스마트폰 안심 사용 지원 서비스",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 1. 권한 현황 표시 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "권한 상태 설정", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    PermissionStatusRow(title = "위치 권한 (앞단)", isGranted = hasLocationPermission)
                    PermissionStatusRow(title = "위치 권한 (백그라운드)", isGranted = hasBgLocationPermission)
                    PermissionStatusRow(title = "다른 앱 위에 그리기", isGranted = hasOverlayPermission)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. 권한 획득 동작 버튼
            Button(
                onClick = {
                    if (!hasLocationPermissions()) {
                        requestPermissionLauncher.launch(locationPermissions + postNotificationPermission)
                    } else if (!hasBackgroundLocationPermission()) {
                        checkAndRequestBackgroundLocation()
                    } else if (!Settings.canDrawOverlays(this@MainActivity)) {
                        requestOverlayPermission()
                    } else {
                        Toast.makeText(this@MainActivity, "모든 필수 권한이 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "필수 권한 설정하기")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 서비스 시작 / 중지 버튼
            Button(
                onClick = {
                    if (!hasLocationPermissions() || !hasBgLocationPermission || !hasOverlayPermission) {
                        Toast.makeText(this@MainActivity, "권한 설정을 먼저 완료해 주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    val intent = Intent(this@MainActivity, SignalService::class.java)
                    if (isServiceRunning) {
                        stopService(intent)
                        isServiceRunning = false
                        Toast.makeText(this@MainActivity, "딴짓모드 작동을 중지합니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        ContextCompat.startForegroundService(this@MainActivity, intent)
                        isServiceRunning = true
                        Toast.makeText(this@MainActivity, "딴짓모드 서비스를 시작합니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (isServiceRunning) "딴짓모드 서비스 중지" else "딴짓모드 서비스 시작")
            }
        }
    }

    @Composable
    fun PermissionStatusRow(title: String, isGranted: Boolean) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 14.sp)
            Text(
                text = if (isGranted) "허용됨" else "허용 안 됨",
                color = if (isGranted) Color(0xFF2E7D32) else Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }

    // 권한 확인 보조 함수들
    private fun hasLocationPermissions(): Boolean {
        return locationPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android Q 미만에서는 위치 권한 획득 시 백그라운드 사용 가능
        }
    }

    // 다른 앱 위에 그리기 권한 요청
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    // 백그라운드 위치 권한 요청 (안내 메시지 팝업 후 진행)
    private fun checkAndRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasBackgroundLocationPermission()) {
                // 사용자가 위치 권한을 '앱 사용 중에만 허용'으로 설정한 상태에서
                // 백그라운드 위치 권한을 얻기 위해 항상 허용으로 선택하도록 대화상자를 안내해야 함.
                android.app.AlertDialog.Builder(this)
                    .setTitle("백그라운드 위치 권한 필요")
                    .setMessage("정차 중 교통 신호 감지를 위해 위치 권한을 '항상 허용'으로 설정해야 백그라운드에서 정상 작동합니다.")
                    .setPositiveButton("설정하러 가기") { _, _ ->
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    // 서비스 실행 여부 체크
    private fun isServiceRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in am.getRunningServices(Integer.MAX_VALUE)) {
            if (SignalService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
