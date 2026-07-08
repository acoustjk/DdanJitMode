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
    
    // 듀얼 신호 상태 데이터 관리 (직진 / 좌회전)
    private val straightTextState = mutableStateOf("🔴 10")
    private val isStraightGreenState = mutableStateOf(false)
    
    private val leftTextState = mutableStateOf("🔴 10")
    private val isLeftGreenState = mutableStateOf(false)

    // 윈도우 레이아웃 파라미터
    private lateinit var params: WindowManager.LayoutParams

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (composeView != null) return // 이미 표시 중이면 무시

        // 1. 윈도우 파라미터 설정 (가로 길이를 듀얼 신호 표시에 맞게 조절: 가로 180dp 수준)
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

    // 듀얼 신호 상태 업데이트 호출
    fun updateState(
        straightText: String, 
        isStraightGreen: Boolean, 
        leftText: String, 
        isLeftGreen: Boolean
    ) {
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

// 7. 가로 캡슐형 듀얼 신호 위젯 Compose UI
@Composable
fun FloatingWidgetContent(
    straightText: String, 
    isStraightGreen: Boolean, 
    leftText: String, 
    isLeftGreen: Boolean
) {
    // 듀얼 신호를 한눈에 감싸는 프리미엄 글래스모피즘 테두리/배경 디자인
    val widgetBorderGradient = Brush.horizontalGradient(
        listOf(Color(0xFF37474F), Color(0xFF455A64))
    )

    Row(
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xE61A237E)) // 짙은 네이비 반투명 배경
            .border(2.5.dp, widgetBorderGradient, RoundedCornerShape(32.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                .width(1.5.dp)
                .height(42.dp)
                .background(Color(0x33FFFFFF))
        )

        // [우측] 좌회전 신호부 (←)
        SignalIndicator(
            label = "좌회전 ←",
            text = leftText,
            isGreen = isLeftGreen
        )
    }
}

// 개별 신호 표시용 Composable
@Composable
fun SignalIndicator(label: String, text: String, isGreen: Boolean) {
    val indicatorBg = if (isGreen) Color(0x204CAF50) else Color(0x20F44336)
    val textThemeColor = if (isGreen) Color(0xFF81C784) else Color(0xFFE57373)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(indicatorBg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xB3FFFFFF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = text,
            color = textThemeColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
