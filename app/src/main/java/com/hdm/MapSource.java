package com.hdm;

public class MapSource {
    public String name;
    public String urlTemplate;
    public String dbName; // 用于本地缓存瓦片的数据库名

    public MapSource(String name, String urlTemplate) {
        this.name = name;
        this.urlTemplate = urlTemplate;
        
        // ================= 核心兼容逻辑 =================
        // 只要名字是这4个老的，就强行使用以前的缓存文件
        if ("谷歌路网".equals(name)) {
            this.dbName = "hdm_road.mbtiles";
        } else if ("谷歌卫星".equals(name)) {
            this.dbName = "hdm_sat.mbtiles";
        } else if ("地形图".equals(name)) {
            this.dbName = "hdm_topo.mbtiles";
        } else if ("OSM".equals(name)) {
            this.dbName = "hdm_osm.mbtiles";
        } else if (urlTemplate.startsWith("blank://")) {
            // 空白图层不需要数据库
            this.dbName = "blank"; 
        } else {
            // 用户自己加的新图源，直接用 "hdm_名称.mbtiles" 作为缓存文件
            this.dbName = "hdm_" + name + ".mbtiles";
        }
    }
}