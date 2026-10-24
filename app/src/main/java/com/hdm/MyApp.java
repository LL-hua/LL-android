package com.hdm;

import android.app.Application;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyApp extends Application {
    // 当前项目
    public ProjectData data;
    public String currentProjectName;
    public File currentProjectFile;

    // 项目列表（主页扫描后存）
    public List<File> projectFiles = new ArrayList<File>();
    public List<String> projectNames = new ArrayList<String>();
}
