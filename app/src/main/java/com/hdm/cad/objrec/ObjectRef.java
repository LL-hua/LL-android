package com.hdm.cad.objrec;

public class ObjectRef {
    public long handle;
    public int offset;

    public ObjectRef(long handle, int offset) {
        this.handle = handle;
        this.offset = offset;
    }
}
