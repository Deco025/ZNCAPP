package com.example.znc_app.ui.theme;

import static java.lang.Math.min;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

public class MYLed extends View {
    public MYLed(Context context) {
        super(context);
    }
    public MYLed(Context context, AttributeSet attrs){  //xml构建时调用
        super(context, attrs);
        init();
    }
    private int alpha;
    public int Width,Height,_CircleR;;
    private Paint paint;
    public  float raid;
    public float colar;
    private Boolean _InitFlag;
    private   ValueAnimator animatorx;
    private void init(){
        paint = new Paint();
        paint.setAntiAlias(true); // 设置抗锯齿
        paint.setColor(Color.RED); // 设置圆的颜色为红色
        paint.setStyle(Paint.Style.FILL);// 设置为实心圆
        _InitFlag=true;
    }
    @Override
    public void onDraw(Canvas canvas){
        super.onDraw(canvas);
        Width=getWidth();
        Height=getHeight();
        if(_InitFlag){
            _CircleR=(int)(min(Width, Height) / (2.5));
        }
        canvas.drawCircle(Width/2,Height/2,_CircleR,paint);
    }

    public void setColar(int Colar , int time){
        paint.setColor(Colar);
        if(time ==0){
            invalidate();
            return;
        }
        if(alpha == 0){
            alpha = 255;
            if (animatorx != null && animatorx.isRunning())
                animatorx.cancel();
        }
        animatorx = ValueAnimator.ofInt(255,100);
        animatorx.setDuration(time); // 动画持续2秒
        animatorx.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                alpha = (int)animatorx.getAnimatedValue();
                paint.setAlpha(alpha);
                invalidate(); // 请求重绘
            }
        });
        animatorx.start();
    }
}
