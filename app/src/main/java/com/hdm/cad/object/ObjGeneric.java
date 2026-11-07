package com.hdm.cad.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjGeneric {
    public String name;
    public int controlType;
    public long handle;
    public long owner;
    public long objSizeBit;
    public int numReactors;
    public boolean xdicMissing;
    public boolean hasDsData;
    public boolean unknown;
    public String origClass;
    public boolean r2010Plus;
    public boolean valueHandle91;
    public int hdlCount;
    public List<ObjField> fields = new ArrayList<>();
    public List<Long> handles = new ArrayList<>();

    public Object getField(String key) {
        for (ObjField f : fields) {
            if (f.key.equals(key)) return f.val;
        }
        return null;
    }

    public void addField(String key, Object val) {
        fields.add(new ObjField(key, val));
    }
}
