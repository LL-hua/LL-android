package com.hdm;

import android.app.Application;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyApp extends Application {
    private static MyApp instance;
    
    public ProjectData data;
    public String currentProjectName;
    public File currentProjectFile;

    public List<File> projectFiles = new ArrayList<File>();
    public List<String> projectNames = new ArrayList<String>();

    public BluetoothRtkManager rtkManager;
    
    // RTK 数据状态
    public double currentRtkLat = 0;
    public double currentRtkLon = 0;
    public double currentRtkAlt = 0;
    public int currentRtkFix = 0; 
    public boolean hasRtkData = false;
    public boolean rtkConnected = false; // 新增：是否已连接蓝牙硬件

    // RTK 点校正偏移量
    public double rtkOffsetX = 0.0;
    public double rtkOffsetY = 0.0;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        rtkManager = new BluetoothRtkManager(this);

        SharedPreferences sp = getSharedPreferences("hdm_config", MODE_PRIVATE);
        rtkOffsetX = sp.getFloat("rtk_offset_x", 0f);
        rtkOffsetY = sp.getFloat("rtk_offset_y", 0f);
    }

    public static MyApp getInstance() {
        return instance;
    }
}