package com.hdm.cad.bitstream;

public class HandleRef {
    public int code;
    public int counter;
    public long value;

    public HandleRef(int code, int counter, long value) {
        this.code = code;
        this.counter = counter;
        this.value = value;
    }
}
