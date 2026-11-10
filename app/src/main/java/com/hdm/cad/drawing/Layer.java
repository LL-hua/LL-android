package com.hdm.cad.drawing;

public class Layer {
    public long handle;
    public String name;
    public int colorIndex;

    public Layer(long handle, String name, int colorIndex) {
        this.handle = handle;
        this.name = name;
        this.colorIndex = colorIndex;
    }
}