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

        // 临时用于按顺序组装块
        long currentBlockHeader = -1;
        List<EntityBase> currentBlockEntities = null;

        for (ObjectRef ref : refs) {
            try {
                ObjectRecord rec = ObjRecParser.parseObjectRecord(objectsData, ref, ver.isR2010Plus());
                ObjHeader h = ObjRecParser.parseObjHeader(rec);
                
                String typeName = ObjTypeCode.entityTypeName(h.typeCode, null);
                
                if (ObjTypeCode.isEntityType(h.typeCode, null)) {
                    BitStream r = rec.bodyBitStream();
                    r.setBitPos(h.dataStartBit);

                    CommonEntityHead head = EntityHeadParser.parseEntityHead(r, rec.dataEndBit(), feats);
                    head.handle = ref.handle; 
                    
                    EntityBase ent = EntityParser.decodeEntityByTypeVer(r, head, h.typeCode, ver, 30);
                    ent.typeCode = h.typeCode; ent.typeName = typeName;

                    if (ent != null) {
                        doc.byHandle.put(ent.handle, ent);
                        
                        if (typeName.equals("BLOCK")) {
                            currentBlockEntities = new ArrayList<>();
                        } else if (typeName.equals("ENDBLK")) {
                            if (currentBlockEntities != null) {
                                doc.blocks.put(currentBlockHeader, currentBlockEntities);
                            }
                            currentBlockEntities = null;
                            currentBlockHeader = -1;
                        } else if (typeName.equals("SEQEND")) {
                            // 忽略
                        } else {
                            if (currentBlockEntities != null) {
                                currentBlockEntities.add(ent);
                            } else {
                                if (ent.mode == 2) doc.modelSpace.add(ent);
                                else if (ent.mode == 1) doc.pspaceSpace.add(ent);
                            }
                        }
                    }
                } else if (typeName.equals("BLOCK_HEADER")) {
                    // 块头对象，记录当前块的句柄
                    currentBlockHeader = ref.handle;
                }
            } catch (Exception e) {}
        }
        return doc;
    }
}
