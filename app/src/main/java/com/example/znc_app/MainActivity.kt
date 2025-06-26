package com.example.znc_app

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.example.znc_app.ui.theme.myViewPagerAdapter
import kotlin.math.abs


class MainActivity : AppCompatActivity(){
    private val buttonStates = Array(4) {
        BooleanArray(
            4
        )
    }

    private lateinit var Bu_pa1: Button
    private lateinit var contex :Context
    @SuppressLint("MissingInflatedId", "DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置状态栏透明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
        }

        // 设置内容布局全屏（覆盖状态栏）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            window.attributes = attrs
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        setContentView(R.layout.ac_main)
//        var slider = findViewById<View>(R.id.slider)
        var viewPager = findViewById<ViewPager2>(R.id.view_pager)
        var adapter : myViewPagerAdapter = myViewPagerAdapter(this,
            object : myViewPagerAdapter.MsgListener{

                override fun onMessageReceived(msg: IntArray?) {
                }

                override fun onError(e: Exception?) {
                    if (e != null) {
                        runOnUiThread {
//                            showToast(e.message.toString())
                        }
                    };
                }
        })
        viewPager.setAdapter(adapter);
        val pagerWidth: Int = viewPager.getLayoutParams().width
        val slideWidth = 200// 假设ViewPager2有3个页面
//        Log.i("maintest",String.format("pagerw  %d SLw %d",pagerWidth,slideWidth))
//        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
//            override fun onPageScrolled(
//                position: Int,
//                positionOffset: Float,
//                positionOffsetPixels: Int,
//            ) {
//                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
//                // 计算滑块的左侧位置
//                // 更新滑块的位置
//                val layoutParams = slider.layoutParams as MarginLayoutParams
//                layoutParams.leftMargin = ((position * slideWidth + positionOffset * slideWidth - slider.width / 2).toInt())
//
//                Log.i("maintest",String.format("slideWidth %d , position %d, positionOffset %f",slideWidth,position,positionOffset))
////                Log.i("maintest",String.format("position %f * slideWidth %f + positionOffset %f * slidewidth %f - slider.width(%f)/2 = layoutParams.leftMargin %d",position,slideWidth,positionOffset,slideWidth,slider.width,layoutParams.leftMargin))
//                slider.layoutParams = layoutParams
//            }
//
//            override fun onPageSelected(position: Int) {
//                super.onPageSelected(position)
//                // 计算滑块在当前页面的左侧位置
//                // 重置滑块的位置
//                val leftMargin: Int = (position * slideWidth - slider.width / 2)
//                val layoutParams = slider.layoutParams as MarginLayoutParams
//                layoutParams.leftMargin = leftMargin
//                slider.layoutParams = layoutParams
//            }
//        })

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        Log.i("maintest",String.format("w %d  h %d",screenWidth,screenHeight))
        var button1 = findViewById<Button>(R.id.BU_pa1)
        var button2 = findViewById<Button>(R.id.BU_pa2)
        var bu_last1 =0;
        var bu_last2 =0;
        viewPager.setPageTransformer(ViewPager2.PageTransformer { page, position -> // 当前页面的透明度变换
            if (position < -1 || position > 1) {
                // 页面完全不可见
                page.alpha = 1f
            } else {
                // 页面在滑动过程中的透明度变化
                val alpha = (1 - abs(position.toDouble())*1.1).toFloat()
                page.alpha = alpha
            }


            // 添加其他页面变换效果，如缩放、旋转等
            // 例如：页面缩放效果
            val scaleFactor = (0.3f + (1 - abs(position.toDouble())) * 0.7f).toFloat()
            page.scaleX = scaleFactor
            page.scaleY = scaleFactor

            // 获取按钮的 LayoutParams
            val layoutParams = button1.layoutParams
            // 设置宽度为固定值（例如 200 像素）
            layoutParams.width = ((abs(position.toDouble()))*screenWidth).toInt()
            // 设置高度为 WRAP_CONTENT
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            // 应用新的 LayoutParams
            if (position !=0f)
            button1.layoutParams = layoutParams

            val layoutParams2 = button2.layoutParams
            // 设置宽度为固定值（例如 200 像素）
            layoutParams2.width = ((1-abs(position.toDouble()))*screenWidth).toInt()
            // 设置高度为 WRAP_CONTENT
            layoutParams2.height = ViewGroup.LayoutParams.WRAP_CONTENT
            // 应用新的 LayoutParams
            if (position!=0f)
            button2.layoutParams = layoutParams2
            Log.i("maintest",String.format("postion %f w %d ",position,layoutParams2.width))

        })

        val startColor = ContextCompat.getColor(this, R.color.purple_200)
        val endColor = ContextCompat.getColor(this, R.color.user)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                runOnUiThread {


                }
            }
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)

                // 使用 ArgbEvaluator 计算渐变颜色
                if (position == 0){
                    val currentColor = ArgbEvaluator().evaluate(positionOffset, startColor, endColor) as Int
                    // 更新按钮的背景色
                    button1.backgroundTintList = ColorStateList.valueOf(currentColor)

                    val currentColor2 = ArgbEvaluator().evaluate(positionOffset, endColor, startColor) as Int
                    // 更新按钮的背景色
                    button2.backgroundTintList = ColorStateList.valueOf(currentColor2)
                }else{
                    val currentColor1 = ArgbEvaluator().evaluate(positionOffset, startColor, endColor) as Int
                    val currentColor2 = ArgbEvaluator().evaluate(positionOffset, endColor, startColor) as Int
                    // 更新按钮的背景色
                    button1.backgroundTintList = ColorStateList.valueOf(currentColor2)
                    // 更新按钮的背景色
                    button2.backgroundTintList = ColorStateList.valueOf(currentColor1)
                }

            }


        })
        val data = intArrayOf(155, 100, 55)


        Bu_pa1= findViewById<Button>(R.id.BU_pa1);
        Bu_pa1.setOnClickListener {view ->
            Log.i("mymain",String.format("button %d",adapter.getBUColState(1)))
           //// 心跳包 AA 55 0 F1 F2 F3 F4 RGB1 RGB2 RGB3 IMGMOD
            val budata = intArrayOf(0xAA,0x55,0X00,3,4,2,1,100,200,155,2)
//            adapter.RecvDataAnlzy(budata);
//            adapter.SetInitDataInColar(data,1)
            adapter.startTimer()
        }
    }


    override fun onDestroy() {
        super.onDestroy()

    }
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

