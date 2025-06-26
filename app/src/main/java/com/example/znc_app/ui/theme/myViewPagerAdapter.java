package com.example.znc_app.ui.theme;

import static java.security.AccessController.getContext;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;

//AA 55 fun data
//fun 0 心跳包， 1 功能数据 ， 2 颜色通道 及 图像类型\
// 心跳包 AA 55 0 F1 F2 F3 F4 RGB1 RGB2 RGB3 IMGMOD
public class myViewPagerAdapter extends FragmentStateAdapter {
    private  int nowpager;
    private TCPCommunicator tcpCommunicator;
    private Handler handler;
    private MsgListener listener;
    private ButtonViewModel viewModel;
    private tcpviewModel tcpviewModel;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private boolean isTimerRunning = false;
    private static long timerInterval = 100;
    private final WeakReference<FragmentActivity> activityRef;
    private int[] lasttcpdata = new int[11];
    private int tcpdelytime = 0;
    private int tcpinitflag = 0;
    private boolean tcpconnect;
    private long tcpsendhearttime;
    private long tcprecvhearttime;
    public myViewPagerAdapter(@NonNull FragmentActivity fragmentActivity, MsgListener listener) {
        super(fragmentActivity);
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        this.activityRef = new WeakReference<>(fragmentActivity);
        viewModel = new ViewModelProvider(fragmentActivity).get(ButtonViewModel.class);
        tcpviewModel = new ViewModelProvider(fragmentActivity).get(tcpviewModel.class);
        initTimer();
        setupLifecycleObserver();
    }
    public interface MsgListener{
        void onMessageReceived(int[] msg);
        void onError(Exception e);
    }
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        myselect fragment = new myselect();
        Bundle args = new Bundle();
        args.putInt("POSITION", position);
        fragment.setArguments(args);
        fragment.setOnButtonClickListener((fragPosition, buttonId) -> {
            handleButtonClick(fragPosition, buttonId);
        });
        handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if(listener != null){
                            listener.onMessageReceived(new int[]{position});
                        }
                    }
                }
        );
        nowpager = position;
        switch (position) {
            case 0:
                tcpinitflag |= 1;
                return fragment;
            case 1:
                tcpinitflag |= 2;
                return new mycolar();
            default:
                tcpinitflag |= 1;
                return fragment;
        }
    }
    private void handleButtonClick(int position, int buttonId) {

        if (tcpCommunicator !=null && tcpCommunicator.isconnect()){
            tcpCommunicator.disconnect();
            stopTimer();
            tcpviewModel.setButtonData(new int[]{2,0,0});
        }
        // 处理按钮点击逻辑
        if (tcpCommunicator == null || tcpCommunicator.isClose()){
            String Ip = myselect.GetIPInput();
            Log.d("Adapter", "IP  "+Ip );
            ConnectTCP(Ip ,45678);
        }

    }
    public Integer getSelectid(){
        return myselect.getBuId();
    }
    @Override
    public int getItemCount() {
        return 2;
    }
    public int getNowpager(){
        return nowpager;
    }

    public Integer getBUColState(int col){
        return myselect.getColState(col);
    }

    public void SetInitDataInColar(int[] data){
        mycolar.InitData(data);
    }

    public void ConnectTCP(String IP,int port){
        tcpCommunicator = new TCPCommunicator(
                IP,
                port,
                new TCPCommunicator.TCPListener() {
                    @Override
                    public void onMessageReceived(int[] message) {
                        int[] resdata = new int[message.length+1];
                        System.arraycopy(message, 0, resdata, 1, message.length);
                        //AA 55 fun data
                        //fun 0 心跳包， 1 功能数据 ， 2 颜色通道 及 图像类型
                        if(message[0]==0x12 && message.length ==1){
                            tcpviewModel.setButtonData(new int[]{1,0,0});
                            tcpconnect =true;
                            startTimer();
                        }else if (message[0]==0x21 && message.length ==1){
                            tcpviewModel.setButtonData(new int[]{2,0,0});
                            stopTimer();
                            tcpconnect =false;
                        }

                        RecvDataAnlzy(message);
                        resdata[0] =0;
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.d("Adapter", e.toString());
                        listener.onError(e);
                    }
                }
        );
        if (tcpCommunicator != null)
            tcpCommunicator.connect();
    }

    public boolean RecvDataAnlzy(int[] data){
        String res ="";
        if(data.length!= 11 )
        {
            return false;
        }
        for (int i =0 ;i< data.length;i++)
            res += String.format("%d ",data[i]);
        res += String.format("len %d",data.length);
        Log.i("Adapter","recvdata:  "+ res);
        if(data[0] == 0xAA && data[1] ==0x55 && data[2] == 0 && data.length== 11){
            tcprecvhearttime = System.currentTimeMillis();


            // 心跳包 AA 55 0 F1 F2 F3 F4 RGB11 RGB12 RGB12 IMGMOD
                int[] buttonData = Arrays.copyOfRange(data, 3, 7);
                int[] lastbuttonData = myselect.getColAlldata();
                boolean initflagg =false;
                for (int i =0;i<buttonData.length;i++) {
                    if (buttonData[i] != lastbuttonData[i] ) {
                        initflagg = true;
                        break;
                    }
                }
                if (initflagg && ((tcpinitflag & 1) == 1))
                    viewModel.setButtonData(buttonData);
                int[] colardata = Arrays.copyOfRange(data, 7, data.length);
                int[] lastcolardata = mycolar.getColarData();
                initflagg =false;
                for (int i =0;i<colardata.length;i++)
                    if( ((tcpinitflag & 2) !=2) || colardata[i]!=lastcolardata[i] ){
                        initflagg = true;
                        break;
                    }
                if(initflagg && ( (tcpinitflag & 2) == 2))
                    mycolar.InitData(colardata);

            System.arraycopy(data,0,lasttcpdata,0,data.length);
            return true;
        }
        return false;
    }
    private static int sendmod=0;
    private void sendDataLoop(){
        // 心跳包 AA 55 0 F1 F2 F3 F4 RGB1 RGB2 RGB3 IMGMOD
        byte[] msg = new byte[11];
        msg[0]= (byte) 0xAA;
        msg[1]= (byte) 0x55;
        switch (tcpinitflag){
            case 0:
                msg[2]= (byte) 0x00;//心跳包，等待初始化
                break;
            case 1:
                msg[2]= (byte) 0x01;
                for (int i=0;i<4;i++){
                    msg[3+i] = (byte) myselect.getColState(i);
                }
                for (int i=7;i<11;i++){
                    msg[i] = (byte) lasttcpdata[i];
                }
                break;
            case 2:
                msg[2]= (byte) 0x01;
                for (int i=3;i<7;i++){
                    msg[i] = (byte) lasttcpdata[i];
                }
                int[] data1 = mycolar.getColarData();
                for (int i=0;i<data1.length;i++){
                    msg[7+i] = (byte) data1[i];
                }
                break;
            case 3:
                msg[2]= (byte) 0x01;
                for (int i=0;i<4;i++){
                    msg[3+i] = (byte) myselect.getColState(i);
                }
                int[] data2 = mycolar.getColarData();
                for (int i=0;i<4;i++){
                    msg[7+i] = (byte) data2[i];
                }
                break;
        }
        sendmod++;
        if (sendmod >= 3 && msg[2] == 0x01) {
            sendmod =0;
            msg[2]=(byte) 0x00;
        }
        if (msg[2] ==0x00)
            tcpsendhearttime = System.currentTimeMillis();
        if (tcprecvhearttime!=0 && System.currentTimeMillis() - tcprecvhearttime > 5000){
            if (!tcpCommunicator.isconnect()){
                tcpCommunicator.disconnect();
                stopTimer();
                viewModel.setButtonData(new int[]{0,999,0});
            }
        }

        tcpCommunicator.sendBytes(msg);
    }
    private void initTimer() {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTimerRunning && activityRef.get() != null) {
                    tcpdelytime = (int)(tcprecvhearttime - tcpsendhearttime);
                    if (tcpdelytime >999 | tcpdelytime <0){
                        tcpdelytime =999;
                        tcpviewModel.setButtonData(new int[]{0,tcpdelytime,0});
                    }
                    if (tcpCommunicator != null &&tcpCommunicator.isconnect()) {
                        if (tcpsendhearttime>0 && tcpdelytime <999){
                            tcpviewModel.setButtonData(new int[]{1,tcpdelytime,1});
                        }
                        sendDataLoop();
                    }else {
                        if(tcpCommunicator != null)
                            tcpCommunicator.disconnect();
                        stopTimer();
                        viewModel.setButtonData(new int[]{2,999,0});
                    }
                    // 递归调用保持定时
                    timerHandler.postDelayed(this, timerInterval);
                }
            }
        };
    }
    // 关联 Activity 生命周期
    private void setupLifecycleObserver() {
        FragmentActivity activity = activityRef.get();
        if (activity != null) {
            activity.getLifecycle().addObserver(new LifecycleEventObserver() {
                @Override
                public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        cleanup(); // Activity 销毁时自动停止
                    }
                }
            });
        }
    }

    // 开始定时器
    public void startTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true;
            timerHandler.postDelayed(timerRunnable, timerInterval);
            Log.i("AdapterTimer", "timer running");
        }
    }

    // 停止定时器
    public void stopTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;
            timerHandler.removeCallbacks(timerRunnable);
            Log.i("AdapterTimer", "timer stop");
        }
    }

    // 修改间隔时间（单位：毫秒）
    public void setTimerInterval(long interval) {

        if (interval > 0) {
            stopTimer();
            this.timerInterval = interval;
            // 如果定时器正在运行则立即应用新间隔
            if (isTimerRunning) {
                startTimer();
            }
        }
    }

    // 销毁时清理资源
    public void cleanup() {
        stopTimer();
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
        Log.i("AdapterTimer", "程序销毁");
    }
}
