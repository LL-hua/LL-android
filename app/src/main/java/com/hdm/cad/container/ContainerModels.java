package com.hdm.cad.container;

import java.util.ArrayList;
import java.util.List;

public class ContainerModels {
    public static class R2000Locator {
        public int recordNo;
        public long offset;
        public long size;
    }

    public static class R2007Header {
        public long pagesMapOffset;
        public long pagesMapSizeCompressed;
        public long pagesMapSizeUncompressed;
        public long pagesMapCorrectionFactor;
        public long sectionsMapID;
        public long sectionsMapSizeCompressed;
        public long sectionsMapSizeUncompressed;
        public long sectionsMapCorrectionFactor;
        public long sectionsAmount;
    }

    public static class R2007PageSlot {
        public long id;
        public long size;
        public long address;
    }

    public static class R2007PageInfo {
        public long offset;
        public long id;
        public long sizeUncompressed;
        public long sizeCompressed;
    }

    public static class R2007SecEntry {
        public long size;
        public long encoded;
        public String name;
        public List<R2007PageInfo> pages = new ArrayList<>();
    }
}
