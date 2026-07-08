package com.example.ddanjitmode

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class FloatingWidgetManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    
    // 듀얼 신호 및 현재 교차로 타이틀 상태 데이터 관리
    private val titleState = mutableStateOf("📍 검색 중...")
    
    private val straightTextState = mutableStateOf("🔴 10")
    private val isStraightGreenState = mutableStateOf(false)
    
    private val leftTextState = mutableStateOf("🔴 10")
    private val isLeftGreenState = mutableStateOf(false)

    // 윈도우 레이아웃 파라미터
    private lateinit var params: WindowManager.LayoutParams

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (composeView != null) return // 이미 표시 중이면 무시

        // 1. 윈도우 파라미터 설정 (타이틀 추가로 세로 길이가 약간 증가하므로 WRAP_CONTENT 자동 대응)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 150
            y = 250
        }

        // 2. ComposeView 생성 및 설정
        val view = ComposeView(context).apply {
            setContent {
                FloatingWidgetContent(
                    title = titleState.value,
                    straightText = straightTextState.value,
                    isStraightGreen = isStraightGreenState.value,
                    leftText = leftTextState.value,
                    isLeftGreen = isLeftGreenState.value
                )
            }
        }

        // 3. Service 등 Non-Activity 컨텍스트에서 ComposeView를 그리기 위해 필수적인 Lifecycle Owner 바인딩
        setupLifecycleOwnersForView(view)

        // 4. 드래그 제스처 및 터치 리스너 세팅
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = initialX + dx
                    params.y = initialY + dy
                    
                    // 드래그 중인 위치로 윈도우 실시간 갱신
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: IllegalArgumentException) {
                        // 뷰가 사라졌을 때 예외 처리
                    }
                    true
                }
                else -> false
            }
        }

        // 5. WindowManager에 추가
        composeView = view
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 듀얼 신호 및 교차로 타이틀 상태 업데이트 호출
    fun updateState(
        title: String,
        straightText: String, 
        isStraightGreen: Boolean, 
        leftText: String, 
        isLeftGreen: Boolean
    ) {
        titleState.value = title
        straightTextState.value = straightText
        isStraightGreenState.value = isStraightGreen
        
        leftTextState.value = leftText
        isLeftGreenState.value = isLeftGreen
    }

    // 플로팅 위젯 제거
    fun dismiss() {
        composeView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                // 이미 제거된 경우의 예외 처리
            } finally {
                composeView = null
            }
        }
    }

    private fun setupLifecycleOwnersForView(view: View) {
        val lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.onCreate()
        
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        view.setViewTreeViewModelStoreOwner(lifecycleOwner)
    }

    private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val viewModelStore = ViewModelStore()

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        fun onCreate() {
            savedStateRegistryController.performRestore(Bundle())
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun onDestroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            viewModelStore.clear()
        }
    }
}

// 7. 가로 캡슐형 듀얼 신호 위젯 Compose UI (상단 교차로명 헤더 추가)
@Composable
fun FloatingWidgetContent(
    title: String,
    straightText: String, 
    isStraightGreen: Boolean, 
    leftText: String, 
    isLeftGreen: Boolean
) {
    val widgetBorderGradient = Brush.horizontalGradient(
        listOf(Color(0xFF37474F), Color(0xFF455A64))
    )

    Column(
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE61A237E)) // 짙은 네이비 반투명 배경
            .border(2.dp, widgetBorderGradient, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // [상단 중앙] 교차로명 또는 데모 상태 헤더
        Text(
            text = title,
            color = Color(0xE6FFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(6.dp))

        // [하단] 직진 / 좌회전 듀얼 신호 영역
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // [좌측] 직진 신호부 (↑)
            SignalIndicator(
                label = "직진 ↑",
                text = straightText,
                isGreen = isStraightGreen
            )

            // [중앙] 세로 구분선
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color(0x22FFFFFF))
            )

            // [우측] 좌회전 신호부 (←)
            SignalIndicator(
                label = "좌회전 ←",
                text = leftText,
                isGreen = isLeftGreen
            )
        }
    }
}

// 개별 신호 표시용 Composable
@Composable
fun SignalIndicator(label: String, text: String, isGreen: Boolean) {
    val indicatorBg = if (isGreen) Color(0x1A4CAF50) else Color(0x1AF44336)
    val textThemeColor = if (isGreen) Color(0xFF81C784) else Color(0xFFE57373)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(indicatorBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color(0x99FFFFFF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = text,
            color = textThemeColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
