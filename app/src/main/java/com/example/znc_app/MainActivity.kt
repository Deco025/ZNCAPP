package com.example.znc_app

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.znc_app.ui.ColorScreen
import com.example.znc_app.ui.SelectScreen
import com.example.znc_app.ui.theme.Znc_appTheme
import com.example.znc_app.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    @SuppressLint("MissingInflatedId", "DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置状态栏透明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        setContent {
            Znc_appTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colorScheme.background) {
                    // 在这里构建主UI
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> SelectScreen(viewModel = viewModel)
                1 -> ColorScreen(viewModel = viewModel)
            }
        }

        BottomNavBar(
            pagerState = pagerState,
            onTabSelected = { page ->
                scope.launch {
                    pagerState.animateScrollToPage(page)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomNavBar(
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val startColor = MaterialTheme.colorScheme.primary
    val endColor = MaterialTheme.colorScheme.secondary

    val pageOffset = pagerState.currentPageOffsetFraction

    // Calculate dynamic widths and colors
    val button1Width = screenWidth * (1 - abs(pageOffset))
    val button2Width = screenWidth * abs(pageOffset)

    val button1Color = lerp(startColor, endColor, abs(pageOffset))
    val button2Color = lerp(endColor, startColor, abs(pageOffset))

    Row(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { onTabSelected(0) },
            modifier = Modifier.width(button1Width),
            colors = ButtonDefaults.buttonColors(containerColor = button1Color)
        ) {
            Text("连接")
        }
        Button(
            onClick = { onTabSelected(1) },
            modifier = Modifier.width(button2Width),
            colors = ButtonDefaults.buttonColors(containerColor = button2Color)
        ) {
            Text("参数")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Znc_appTheme {
        // MainScreen(viewModel = MainViewModel(Application())) // Preview needs a ViewModel instance
    }
}

