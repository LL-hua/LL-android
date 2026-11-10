package com.hdm.cad.drawing;

import com.hdm.cad.bitstream.BitStream;
import com.hdm.cad.container.ContainerParser;
import com.hdm.cad.container.DwgVersion;
import com.hdm.cad.entity.*;
import com.hdm.cad.objrec.*;
import static com.hdm.cad.entity.EntityBase.*;
import java.util.ArrayList;
import java.util.List;

public class DwgParser {
    public static Document parse(byte[] data) throws Exception {
        DwgVersion ver = DwgVersion.detectVersion(data);
        Document doc = new Document();
        doc.version = ver.getMagic();

        byte[] objectsData = ContainerParser.loadNamedSectionData(data, "AcDb:AcDbObjects");
        byte[] handlesData = ContainerParser.loadNamedSectionData(data, "AcDb:Handles");
        List<ObjectRef> refs = ObjRecParser.parseObjectMapHandles(handlesData);

        int feats = 0;
        if (ver == DwgVersion.VerR2000) feats = 0x123;
        else if (ver == DwgVersion.VerR2004) feats = 0x023;
        else if (ver == DwgVersion.VerR2007) feats = 0x0AF;
        else if (ver == DwgVersion.VerR2010) feats = 0x0BC;
        else if (ver == DwgVersion.VerR2013 || ver == DwgVersion.VerR2018) feats = 0x0FC;

        long currentBlockHeader = -1;
        List<EntityBase> currentBlockEntities = null;

        for (ObjectRef ref : refs) {
            try {
                ObjectRecord rec = ObjRecParser.parseObjectRecord(objectsData, ref, ver.isR2010Plus());
                ObjHeader h = ObjRecParser.parseObjHeader(rec);
                String typeName = ObjTypeCode.entityTypeName(h.typeCode, null);
                
                // --- 提取 LAYER (0x33) 对象 ---
                if (h.typeCode == 0x33) {
                    BitStream r = rec.bodyBitStream();
                    r.setBitPos(h.dataStartBit);
                    
                    long objSizeBit = 0;
                    if ((feats & 0x001) != 0) objSizeBit = r.readRL();
                    
                    r.readH(); 
                    while (true) { int sz = r.readBS(); if (sz == 0) break; r.readH(); r.readRCS(sz); } 
                    r.readBL(); 
                    if (ver.ordinal() >= DwgVersion.VerR2004.ordinal()) r.readB(); 
                    if (ver.ordinal() >= DwgVersion.VerR2013.ordinal()) r.readB(); 
                    
                    String name = "";
                    if (ver.ordinal() >= DwgVersion.VerR2007.ordinal()) {
                        long bitsizeAbs = rec.dataEndBit(); 
                        if ((feats & 0x001) != 0) bitsizeAbs = rec.bodyBitOffset + objSizeBit;
                        
                        if (bitsizeAbs > 34) {
                            BitStream r2 = new BitStream(r.src);
                            r2.setBitPos(bitsizeAbs - 1);
                            if (r2.readB() == 1) {
                                r2.setBitPos(bitsizeAbs - 17);
                                int ds = r2.readRS();
                                if ((ds & 0x8000) != 0) {
                                    r2.setBitPos(bitsizeAbs - 33);
                                    int hi = r2.readRS();
                                    ds = (ds & 0x7fff) | (hi << 15);
                                }
                                long areaStart = bitsizeAbs - 17 - ds;
                                if (areaStart >= 0) {
                                    r2.setBitPos(areaStart);
                                    name = r2.readTU();
                                }
                            }
                        }
                    } else {
                        name = r.readTV(30);
                    }
                    
                    if (ver.ordinal() >= DwgVersion.VerR2007.ordinal()) r.readBS();
                    else { r.readB(); r.readBS(); r.readB(); }
                    
                    r.readBS(); // flag 70
                    
                    int colorIdx = 7;
                    if (ver.ordinal() >= DwgVersion.VerR2004.ordinal()) {
                        colorIdx = r.readBS(); r.readBL(); r.readRC();
                    } else {
                        colorIdx = r.readBS();
                    }
                    if (colorIdx <= 0 || colorIdx > 255) colorIdx = 7;
                    doc.layers.put(ref.handle, new Layer(ref.handle, name, colorIdx));
                }
                
                // --- 提取实体图元 ---
                if (ObjTypeCode.isEntityType(h.typeCode, null)) {
                    BitStream r = rec.bodyBitStream();
                    r.setBitPos(h.dataStartBit);

                    CommonEntityHead head = EntityHeadParser.parseEntityHead(r, rec.dataEndBit(), feats);
                    head.handle = ref.handle; 
                    
                    EntityBase ent = EntityParser.decodeEntityByTypeVer(r, head, h.typeCode, ver, 30);
                    ent.typeCode = h.typeCode; ent.typeName = typeName;

                    if (ent != null) {
                        // ==========================================
                        // ✅ 完美还原 AutoCAD 颜色优先级逻辑（删除猜色）
                        // ==========================================
                        EntColor finalColor = new EntColor();
                        boolean needLayerColor = false;

                        if (ent.color != null) {
                            if (ent.color.hasTrue) {
                                // 1. 最优先：真彩色（包括红色）
                                finalColor.hasTrue = true;
                                finalColor.trueColor = ent.color.trueColor;
                                if (ent.color.hasAlpha) {
                                    finalColor.hasAlpha = true;
                                    finalColor.alpha = ent.color.alpha;
                                    finalColor.alphaType = ent.color.alphaType;
                                }
                            } else if (ent.color.hasIndex) {
                                // 2. 索引色 (判断是否随层/随块 256/0)
                                int idx = ent.color.index;
                                if (idx == 256 || idx == 0) {
                                    needLayerColor = true; 
                                } else {
                                    finalColor.hasIndex = true;
                                    finalColor.index = idx;
                                }
                            } else {
                                needLayerColor = true;
                            }
                        } else {
                            needLayerColor = true;
                        }

                        // 3. 随层/随块逻辑：去图层字典找真实的 ACI 索引
                        if (needLayerColor) {
                            Layer layer = doc.layers.get(ent.layer);
                            if (layer != null) {
                                finalColor.hasIndex = true;
                                finalColor.index = layer.colorIndex;
                            } else {
                                // 确实找不到图层，用默认 7 号色（白底会自动反转为黑色）
                                finalColor.hasIndex = true;
                                finalColor.index = 7; 
                            }
                        }
                        ent.color = finalColor;

                        doc.byHandle.put(ent.handle, ent);
                        if (typeName.equals("BLOCK")) {
                            currentBlockEntities = new ArrayList<>();
                        } else if (typeName.equals("ENDBLK")) {
                            if (currentBlockEntities != null) doc.blocks.put(currentBlockHeader, currentBlockEntities);
                            currentBlockEntities = null; currentBlockHeader = -1;
                        } else if (!typeName.equals("SEQEND")) {
                            if (currentBlockEntities != null) currentBlockEntities.add(ent);
                            else {
                                if (ent.mode == 2) doc.modelSpace.add(ent);
                                else if (ent.mode == 1) doc.pspaceSpace.add(ent);
                            }
                        }
                    }
                } else if (typeName.equals("BLOCK_HEADER")) {
                    currentBlockHeader = ref.handle;
                }
            } catch (Exception e) {
                // 建议在调试时打印一下 e.printStackTrace(); 有助于排查解析失败的图元
            }
        }
        return doc;
    }
}