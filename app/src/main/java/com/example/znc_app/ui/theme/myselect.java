package com.example.znc_app.ui.theme;

import static android.content.Context.MODE_PRIVATE;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.znc_app.R;

public class myselect extends Fragment {
    public static boolean[][] buttonStates = new boolean[4][4];
    private static int[] lastbustates = new int[4];//id
    private static int[] lastbustatesrow = new int[4];
    private static int[] nowbustatesrow = new int[4];
    private static Context context;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_BUTTON_STATE = "button_state";
    private static final String LAST_IP = "last_ip";
    private static View view;
    private static Button bu_connect;
    private ButtonViewModel viewModel;
    private tcpviewModel tcpviewModel;
    private static EditText inputip;
    private static String IPDZ;
    private boolean lopflag = false;
    private int[] delaytime = new int[10];
    // 定义按钮点击监听接口
    public interface OnButtonClickListener {
        void onButtonClick(int fragmentPosition, int buttonId);
    }

    private OnButtonClickListener listener;

    // 设置监听器的方法
    public void setOnButtonClickListener(OnButtonClickListener listener) {
        this.listener = listener;
    }

    public static String GetIPInput(){

        return IPDZ.replace(" ","");
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 定义一个二维数组来保存按钮的状态
        view = inflater.inflate(R.layout.ac_select, container, false);
        context = getActivity();

        inputip = view.findViewById(R.id.IPinput);

        viewModel = new ViewModelProvider(requireActivity()).get(ButtonViewModel.class);
        tcpviewModel = new ViewModelProvider(requireActivity()).get(tcpviewModel.class);
        // 观察数据变化
        viewModel.getButtonData().observe(getViewLifecycleOwner(), data -> {
            if (data != null ) {
                setButtonStates(data);
            }
        });

        tcpviewModel.getButtonData().observe(getViewLifecycleOwner(),data->{
            if (data != null ) {
                setButtonStates(data);
            }
        });
        readButtonState();
        bu_connect = view.findViewById(R.id.BU_Connect);

        bu_connect.setOnClickListener(v -> {
            if (listener != null) {
                IPDZ = inputip.getText().toString();
                // 传递 Fragment 的位置和按钮 ID
                int position = getArguments().getInt("POSITION");
                listener.onButtonClick(position, v.getId());
            }
        });
// 在布局中找到所有按钮并设置点击事件
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int id = getResources().getIdentifier("button_" + i + "_" + j, "id", getActivity().getPackageName());
                Log.i("mytast", String.valueOf(id));
                if (id==0)break;
                Button button = view.findViewById(id);
                if (button==null){
                    Log.i("mytast", "select_null");
                }
                // 设置按钮的标签为位置信息
                button.setTag(new int[]{i, j});
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        // 获取按钮的标签
                        int[] position = (int[]) v.getTag();
                        int row = position[0];
                        int col = position[1];
                        if (row>3||col>3)
                            return;
                        if(!lopflag)
                            buttonclickloop(row+1,col+1);
                    }
                });

                button.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        // 获取按钮的标签
                        int[] position = (int[]) view.getTag();
                        return buttonclickChangeSize(view,motionEvent,position);
                    }
                });
            }
        }
        return view;
    }

    private boolean buttonclickChangeSize(View view , MotionEvent motionEvent,int[] position){
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                int row = position[0];
                int col = position[1];
                if (row>3||col>3)
                    return false;
                if(!lopflag)
                    buttonclickloop(row+1,col+1);
                // 大小改变动画
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.5f,1.2f,0.8f,1.0f);
                scaleX.setDuration(500);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.5f,1.2f,0.8f,1.0f);
                scaleY.setDuration(500);

                // 创建动画集合
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(scaleX, scaleY);
                animatorSet.start();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.setScaleX(1.0f);
                view.setScaleY(1.0f);
                return true;
        }
        return false;
    }

    private void buttonclickloop(int row,int col){

        // 根据状态更新按钮外观
        ValueAnimator animatorx,animatorend;
        animatorx = ValueAnimator.ofArgb(ContextCompat.getColor(context,R.color.BU_define), ContextCompat.getColor(context,R.color.BU_select));
        animatorend =ValueAnimator.ofArgb(ContextCompat.getColor(context,R.color.BU_select), ContextCompat.getColor(context,R.color.BU_define));

        animatorx.setDuration(300); // 动画持续时间（毫秒）
        animatorx.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorend.setDuration(300);
        row-=1;col-=1;

        if (row <0 && col >=0 && lastbustatesrow[col] != 0){
            int buttonid = getResources().getIdentifier(String.format("button_%d_%d",lastbustatesrow[col] -1,col), "id", getActivity().getPackageName());
            View vvv = view.findViewById(buttonid);
            ViewCompat.setBackgroundTintList(vvv,android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context,R.color.BU_define)));
            lastbustatesrow[col] =0;
        }

        View v = view.findViewById(getResources().getIdentifier(String.format("button_%d_%d",row,col), "id", getActivity().getPackageName()));
        if(v==null || col <0)
            return;

        if (lastbustatesrow[col] > 0){
            int buttonid = getResources().getIdentifier(String.format("button_%d_%d",lastbustatesrow[col] -1,col), "id", getActivity().getPackageName());
            animatorend.addUpdateListener(valueAnimator -> {
                int animatedValue = (int) valueAnimator.getAnimatedValue();
                ViewCompat.setBackgroundTintList(view.findViewById(buttonid),android.content.res.ColorStateList.valueOf(animatedValue));
            });
            animatorend.start();
        }

        nowbustatesrow[col] =row+1;
        if(nowbustatesrow[col] == lastbustatesrow[col])
            nowbustatesrow[col] = 0;
        if(nowbustatesrow[col] >0){
            animatorx.addUpdateListener(valueAnimator -> {
                int animatedValue = (int) valueAnimator.getAnimatedValue();
                ViewCompat.setBackgroundTintList(v, android.content.res.ColorStateList.valueOf(animatedValue));
            });
            animatorx.start();
            lastbustatesrow[col] = nowbustatesrow[col];
        }else {
            lastbustatesrow[col] =0;
        }

    }
    public static View getConnectBU(){
        return bu_connect;
    }
    public static int getBuId(){
        int res =0;
        int idx =0;
        for(int i=0;i<4;i++){
            for(int j=0;j<4;j++){
                if(buttonStates[i][j])
                    res=res|(1<<idx);
                idx++;
            }
        }
        return res;
    }

    public static int getColState(int col){
        return nowbustatesrow[col];
    }

    public static int[] getColAlldata(){
        return nowbustatesrow.clone();
    }

    private void saveButtonState(String buttonid,int state) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Log.i("select","save "+buttonid+String.format(" row %d",state));
        editor.putInt(buttonid, state);
        editor.apply();
        editor.commit();
    }

    private void readButtonState(){

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        for (int i = 0; i < 4; i++) {
            String ids = String.format("button_%d",i);
            int row = prefs.getInt(ids,0);//无该参数则返回 第二个参数的值
            buttonclickloop(row,i+1);
            lastbustatesrow[i] =row;
        }
        if (inputip != null)
            inputip.setText(prefs.getString(LAST_IP,"192.168.1.1"));
        boolean buttonState = prefs.getBoolean(KEY_BUTTON_STATE, false);

    }
    int delaytimeidx =0;
    private int getavgtime(){
        int res =0;
        for (int i=0;i<delaytime.length;i++)
            res+= delaytime[i];
        return res/delaytime.length;
    }
    public void setButtonStates(int[] data){
        if (data.length == 3 ){
            if (data[0] == 1){
                int color = ContextCompat.getColor(context, R.color.BU_select);
                ViewCompat.setBackgroundTintList(bu_connect,ColorStateList.valueOf(color));
            }else if (data[0] == 2){
                int color = ContextCompat.getColor(context, R.color.BU_define);
                ViewCompat.setBackgroundTintList(bu_connect,ColorStateList.valueOf(color));
            }
            if(data[1] > 0) {
                delaytime[delaytimeidx] = data[1];
                delaytimeidx++;
                if (delaytimeidx >= 10)
                    delaytimeidx = 0;
                TextView tv = view.findViewById(R.id.TX_dlayTime);
                tv.setText(String.format("%03dms", getavgtime()));
            }
            if (data[2] == 1){
                MYLed led = view.findViewById(R.id.LED_c);
                int color = ContextCompat.getColor(context, R.color.BU_select);
                led.setColar(color,100);
            }
        }

        if(data.length ==4){
            lopflag = true;
            for (int i = 0; i < 4; i++) {
                if (data[i]!=lastbustatesrow[i])
                     buttonclickloop(data[i],i+1);
            }
            lopflag =false;
        }

    }

    private void savedata(){
        for (int i = 0; i < 4; i++) {
            String ids = String.format("button_%d",i);
            saveButtonState(ids,nowbustatesrow[i]);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(LAST_IP, inputip.getText().toString());
        editor.apply();
        editor.commit();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        savedata();
    }

    @Override
    public void onStop(){
        super.onStop();
        savedata();
    }
}
