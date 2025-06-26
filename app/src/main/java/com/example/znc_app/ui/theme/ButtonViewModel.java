package com.example.znc_app.ui.theme;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ButtonViewModel extends ViewModel {
    private final MutableLiveData<int[]> buttonData = new MutableLiveData<>();

    public void setButtonData(int[] data) {
        buttonData.postValue(data);
    }

    public LiveData<int[]> getButtonData() {
        return buttonData;
    }
}

