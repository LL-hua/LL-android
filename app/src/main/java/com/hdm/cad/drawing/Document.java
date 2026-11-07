package com.hdm.cad.drawing;

import com.hdm.cad.entity.EntityBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Document {
    public String version;
    public List<EntityBase> modelSpace = new ArrayList<>();
    public List<EntityBase> pspaceSpace = new ArrayList<>();
    public Map<Long, EntityBase> byHandle = new HashMap<>();
    public Map<Long, List<EntityBase>> blocks = new HashMap<>(); // 块句柄 -> 内部实体

    public int entityCount() { return modelSpace.size() + pspaceSpace.size(); }
}
