package com.example.znc_app.ui.theme;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import com.example.znc_app.R;

public class mycolar extends Fragment {

    private static EditText TV_set;
    private static int[] ColarData =new int[4];
    private static int Set_Data;
    private static boolean init_flag;
    private static boolean[] BU_flag=new boolean[4];
    private static SparseArray<SeekBar> seekBars = new SparseArray<>();
    private static SparseArray<TextView> textViews = new SparseArray<>();
    private static SparseArray<Button> buttons= new SparseArray<>();
    private static int[] buttonIds = {R.id.colar_RED, R.id.colar_Yellow, R.id.colar_Bao,R.id.colar_Zi};
    private static Context context;
    private static int IMGMOD;

    private static int[][] seekdata = new int[4][2];
    private static int SetSeekMod =0;
    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        context =getActivity();
        View view = inflater.inflate(R.layout.ac_colar_adudio, container, false);
        TV_set = view.findViewById(R.id.Tv_Colar_Set);
        initializeSeekBars(view);
        setSeekBarListeners();
        setButtonsListeners();
        return view;
    }
    private void initializeSeekBars(View view) {
        // 示例：初始化多个 SeekBar 和对应的 TextView
        seekBars.put(1, view.findViewById(R.id.bar_red));
        textViews.put(1, view.findViewById(R.id.tx_red));
        buttons.put(1,view.findViewById(buttonIds[0]));

        seekBars.put(2, view.findViewById(R.id.bar_yellow));
        textViews.put(2, view.findViewById(R.id.tx_yellow));
        buttons.put(2,view.findViewById(buttonIds[1]));

        buttons.put(3,view.findViewById(buttonIds[2]));
        buttons.put(4,view.findViewById(buttonIds[3]));

        buttons.put(5,view.findViewById(R.id.Set_D));
        buttons.put(6,view.findViewById(R.id.Set_P));

        TV_set.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                try {
                    Set_Data = Integer.parseInt(s.toString().replace(" ",""));
                } catch (NumberFormatException e) {
                    showToast("输入数字");
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        for (int i =0; i< seekBars.size(); i++ ){
            int key = seekBars.keyAt(i);
            SeekBar seekBar = seekBars.get(key);
            TextView textView = textViews.get(key);
            textView.setText(String.format("%3d",(int)seekBar.getProgress()));
        }
    }

    private void setSeekBarListeners(){
        for (int i = 0; i < seekBars.size(); i++) {
            int key = seekBars.keyAt(i);
            SeekBar seekBar = seekBars.get(key);
            TextView textView = textViews.get(key);

            seekBar.setTag(i);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int id = (int) seekBar.getTag();
                    SetSeekMod =id;
                    int set_mod = -1;
                    textView.setText(String.format("%3d",progress));
                    ColarData[id] = progress;
                    for(int i=0;i<BU_flag.length;i++){
                        if(BU_flag[i])
                            set_mod =i;
                    }

                    if (IMGMOD>0){
                        seekdata[IMGMOD-1][id] = progress;
                    }
                    Set_Data = progress;
                    TV_setLoop();

                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // 可选：触摸滑块开始时的逻辑
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // 可选：触摸滑块结束时的逻辑
                }
            });
        }
    }

    private void setButtonsListeners(){
        for (int i =0;i<buttons.size();i++){
            int key = buttons.keyAt(i);
            Button button = buttons.get(key);
            button.setTag(i);
            if(i<4){
                button.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                int BU_flagIdx = (int)view.getTag();
                                for (int i=0;i<buttonIds.length;i++) {
                                    if (i != BU_flagIdx) {
                                        BU_flag[i] = false;
                                        int key = buttons.keyAt(i);
                                        Button button = buttons.get(key);
                                        SetbuttonBackgroundTintCloar(button,R.color.BU_define);
                                        Log.i("colar",String.format("%d",BU_flagIdx));
                                    }else{
                                        BU_flag[BU_flagIdx] = !BU_flag[BU_flagIdx];
                                        if(BU_flag[BU_flagIdx]){
                                            IMGMOD = BU_flagIdx+1;
                                            SetbuttonBackgroundTintCloar(button,R.color.BU_select);
                                            SeekBar seekBar = seekBars.get(seekBars.keyAt(0));
                                            seekBar.setProgress(seekdata[BU_flagIdx][0]);
                                            seekBar = seekBars.get(seekBars.keyAt(1));
                                            seekBar.setProgress(seekdata[BU_flagIdx][1]);
                                        }
                                        else{
                                            SetbuttonBackgroundTintCloar(button,R.color.BU_define);
                                            IMGMOD =0;
                                        }
                                    }
                                }

                            }
                        }
                );
            }else {
                button.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                int buid = (int)view.getTag();
                                int set_mod = -1;
                                ValueAnimator animatorx;
                                animatorx = ValueAnimator.ofArgb(ContextCompat.getColor(context,R.color.BU_select), ContextCompat.getColor(context,R.color.BU_define));
                                animatorx.setDuration(300);
                                animatorx.addUpdateListener(valueAnimator ->{
                                    int animatedValue = (int) valueAnimator.getAnimatedValue();
                                    ViewCompat.setBackgroundTintList(view, android.content.res.ColorStateList.valueOf(animatedValue));
                                });
                                animatorx.start();

                                SeekBar seekBar = seekBars.get(seekBars.keyAt(SetSeekMod));
                                if (buid == 5){
                                    if(Set_Data>0)
                                        Set_Data--;
                                    TV_setLoop();
                                }else{
                                    if(Set_Data<256)
                                        Set_Data++;
                                    TV_setLoop();
                                }
                                seekBar.setProgress(Set_Data);
                            }
                        }
                );
            }

        }
    }

    private static void SetbuttonBackgroundTintCloar(View view, int colarId){
        int color = ContextCompat.getColor(context, colarId);
        ColorStateList colorStateList = ColorStateList.valueOf(color);
        ViewCompat.setBackgroundTintList(view,colorStateList);
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    //RGB1 RGB2 RGB3 IMGMOD
    public static void InitData(int[] colardata){
        init_flag = true;
        ColarData = colardata.clone();
        IMGMOD = ColarData[ColarData.length -1];
        for (int i =0;i< BU_flag.length;i++){
            if (BU_flag[i]){
            BU_flag[i]=false;
            int key = buttons.keyAt(i);
            Button button = buttons.get(key);
            SetbuttonBackgroundTintCloar(button,R.color.BU_define);}
        }

        if(IMGMOD !=0){
            BU_flag[IMGMOD-1]=true;
            seekdata[IMGMOD-1][0]= ColarData[0];
            seekdata[IMGMOD-1][1]= ColarData[1];
            SetbuttonBackgroundTintCloar(buttons.get(buttons.keyAt(IMGMOD-1)),R.color.BU_select);
            SeekBar seekBar = seekBars.get(seekBars.keyAt(0));
            seekBar.setProgress(seekdata[IMGMOD-1][0]);
            seekBar = seekBars.get(seekBars.keyAt(1));
            seekBar.setProgress(seekdata[IMGMOD -1][1]);
        }

    }

    public void TV_setLoop(){
        if (SetSeekMod ==0)
            TV_set.setTextColor(ContextCompat.getColor(context, R.color.RED));
        else
            TV_set.setTextColor(ContextCompat.getColor(context, R.color.yeelow));
        TV_set.setText(String.format("%3d",Set_Data));
    }
    public static int[] getColarData(){
        int[] ints = new int[4];
        if (IMGMOD !=0){
            ints[0] = seekdata[IMGMOD-1][0];
            ints[1] = seekdata[IMGMOD-1][1];
        }

        ints[ints.length-1] = IMGMOD;
        return ints;
    }


}
