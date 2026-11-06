package com.hdm;

import android.app.Application;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyApp extends Application {
    private static MyApp instance;
    
    // 当前项目
    public ProjectData data;
    public String currentProjectName;
    public File currentProjectFile;

    // 项目列表
    public List<File> projectFiles = new ArrayList<File>();
    public List<String> projectNames = new ArrayList<String>();

    // ===== 新增：全局 RTK 管理器 =====
    public BluetoothRtkManager rtkManager;
    
    // 记录最新收到的 RTK 坐标 (全局共享)
    public double currentRtkLat = 0;
    public double currentRtkLon = 0;
    public double currentRtkAlt = 0;
    public int currentRtkFix = 0; // 0未定位 1单点 4固定解 5浮点解
    public boolean hasRtkData = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        rtkManager = new BluetoothRtkManager(this);
    }

    public static MyApp getInstance() {
        return instance;
    }
}