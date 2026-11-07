package com.hdm.cad.object;

import com.hdm.cad.bitstream.BitStream;
import com.hdm.cad.container.DwgVersion;

public class InternalObjectSpec {
    public interface DecodeFunc { void decode(BitStream r, DwgVersion ver, GfRead fr, ObjGeneric g) throws Exception; }
    public interface HdlFunc { void hdl(BitStream r, DwgVersion ver, GfRead fr, ObjGeneric g) throws Exception; }

    public DecodeFunc decode;
    public HdlFunc hdl;
    public int extraHandles = 0;
    public String handleVectorKey = "";

    public InternalObjectSpec(DecodeFunc decode) { this.decode = decode; }
    public InternalObjectSpec(DecodeFunc decode, HdlFunc hdl) { this.decode = decode; this.hdl = hdl; }
}
