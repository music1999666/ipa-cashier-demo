package com.ipaloma.jxbpaydemo;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;


public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("debug","MyApplication  onCreate");
    }

}
