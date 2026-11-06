package com.hdm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

public class BluetoothRtkManager {

    // 标准串口蓝牙 UUID
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    public interface RtkCallback {
        void onRtkData(double lat, double lon, double alt, int fixQuality);
        void onRtkStatus(String status);
    }

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private boolean isReading = false;
    private RtkCallback callback;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public BluetoothRtkManager(Context context) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setCallback(RtkCallback callback) {
        this.callback = callback;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        disconnect(); // 先断开之前的
        new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 使用原生方法检查连接权限
                    if (MyApp.getInstance().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        sendStatus("缺少蓝牙连接权限");
                        return;
                    }
                }
                
                if (bluetoothAdapter == null) {
                    sendStatus("蓝牙不可用");
                    return;
                }

                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();
                sendStatus("已连接: " + device.getName());
                isReading = true;
                readLoop();
            } catch (IOException e) {
                e.printStackTrace();
                sendStatus("连接失败");
            }
        }).start();
    }

    private void readLoop() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            String line;
            while (isReading && (line = reader.readLine()) != null) {
                if (line.startsWith("$GNGGA") || line.startsWith("$GPGGA")) {
                    parseGGA(line);
                }
            }
        } catch (IOException e) {
            if (isReading) {
                sendStatus("连接断开");
            }
        }
    }

    private void parseGGA(String gga) {
        try {
            String[] parts = gga.split(",");
            if (parts.length > 9) {
                String latStr = parts[2];
                String latDir = parts[3];
                String lonStr = parts[4];
                String lonDir = parts[5];
                int fixQuality = Integer.parseInt(parts[6].isEmpty() ? "0" : parts[6]);
                double alt = Double.parseDouble(parts[9].isEmpty() ? "0" : parts[9]);

                if (latStr.isEmpty() || lonStr.isEmpty()) return;

                // 度分格式转十进制度 (例如 2236.12345 -> 22 + 36.12345/60)
                double lat = Double.parseDouble(latStr.substring(0, 2)) + Double.parseDouble(latStr.substring(2)) / 60.0;
                if (latDir.equals("S")) lat = -lat;

                double lon = Double.parseDouble(lonStr.substring(0, 3)) + Double.parseDouble(lonStr.substring(3)) / 60.0;
                if (lonDir.equals("W")) lon = -lon;

                // ================= 核心修复 =================
                // 复制给 final 变量，满足 Lambda 语法要求
                final double finalLat = lat;
                final double finalLon = lon;
                final double finalAlt = alt;
                final int finalFixQuality = fixQuality;

                if (callback != null) {
                    mainHandler.post(() -> callback.onRtkData(finalLat, finalLon, finalAlt, finalFixQuality));
                }
                // ============================================
            }
        } catch (Exception ignored) {
        }
    }

    private void sendStatus(String msg) {
        if (callback != null) {
            mainHandler.post(() -> callback.onRtkStatus(msg));
        }
    }

    public void disconnect() {
        isReading = false;
        try {
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        inputStream = null;
        bluetoothSocket = null;
    }
}