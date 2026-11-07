package com.hdm.cad.container;

import com.hdm.cad.bitstream.Utils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerParser {

    // ---- 统一入口 ----
    public static byte[] loadNamedSectionData(byte[] data, String name) throws Exception {
        if (data.length >= 6 && new String(data, 0, 6).equals("AC1021")) {
            return loadNamedSectionDataR2007(data, name);
        }
        R2004Header header = ContainerBasic.decryptR2004Header(data);
        List<PageSlot> pages = parsePageMap(data, header);
        List<SecEntry> sections = parseSectionMap(data, header, pages);

        Map<Integer, PageSlot> lookup = new HashMap<>();
        for (PageSlot p : pages) if (p.id > 0) lookup.put(p.id, p);

        for (SecEntry sec : sections) {
            if (sec.name.equals(name)) {
                return assembleSection(data, sec, lookup);
            }
        }
        throw new Exception("段 " + name + " 不存在");
    }

    // ---- R2004+ 系统段解析 ----
    private static byte[] inflateSystemSection(byte[] data, long address, int expectSignature) throws Exception {
        int offset = (int) address;
        if (offset < 0 || offset + ContainerBasic.systemSectionHdrSize > data.length) {
            throw new Exception("system section 头越界");
        }
        int signature = ContainerBasic.readUInt32LE(data, offset);
        int plainSize = ContainerBasic.readUInt32LE(data, offset + 4);
        int packedSize = ContainerBasic.readUInt32LE(data, offset + 8);
        int codec = ContainerBasic.readUInt32LE(data, offset + 12);

        if (signature != expectSignature) {
            throw new Exception("system section 签名不匹配");
        }
        if (packedSize == 0) return new byte[0];

        int start = offset + ContainerBasic.systemSectionHdrSize;
        if (codec == 1) { // store
            if (plainSize > packedSize) throw new Exception("store 长度不足");
            byte[] out = new byte[plainSize];
            System.arraycopy(data, start, out, 0, plainSize);
            return out;
        } else if (codec == 2) { // LZ77
            byte[] packed = new byte[packedSize];
            System.arraycopy(data, start, packed, 0, packedSize);
            return Lz77Decompressor.decompress(packed, plainSize);
        }
        throw new Exception("不支持的 system section 压缩类型");
    }

    // ---- R2004+ 页表/段表 ----
    private static List<PageSlot> parsePageMap(byte[] data, R2004Header header) throws Exception {
        byte[] blob = inflateSystemSection(data, header.sectionPageMapAddress + 0x100, ContainerBasic.SectionPageMapMagic);
        List<PageSlot> slots = new ArrayList<>();
        long pageAddress = 0x100;
        int off = 0;
        while (off + 8 <= blob.length) {
            int id = ContainerBasic.readUInt32LE(blob, off);
            int pageSize = ContainerBasic.readUInt32LE(blob, off + 4);
            off += 8;
            slots.add(new PageSlot(id, pageAddress));
            pageAddress += pageSize;
            if (id < 0) off += 16; // gap
        }
        return slots;
    }

    private static List<SecEntry> parseSectionMap(byte[] data, R2004Header header, List<PageSlot> pages) throws Exception {
        PageSlot mapPage = null;
        for (PageSlot p : pages) if (p.id == header.sectionMapID) mapPage = p;
        if (mapPage == null) throw new Exception("找不到段表页");

        byte[] blob = inflateSystemSection(data, mapPage.address, ContainerBasic.SectionMapMagic);
        int entryCount = ContainerBasic.readUInt32LE(blob, 0);
        List<SecEntry> sections = new ArrayList<>();
        int off = 20;
        for (int i = 0; i < entryCount; i++) {
            SecEntry entry = new SecEntry();
            entry.size = ContainerBasic.readUInt64LE(blob, off);
            int pageCount = ContainerBasic.readUInt32LE(blob, off + 8);
            entry.maxDecompressedSz = ContainerBasic.readUInt32LE(blob, off + 12);
            entry.compressed = ContainerBasic.readUInt32LE(blob, off + 20);
            entry.encrypted = ContainerBasic.readUInt32LE(blob, off + 28);
            entry.name = ContainerBasic.readCString(blob, off + 32, 64);
            off += 96;
            for (int p = 0; p < pageCount; p++) {
                entry.pageIDs.add(ContainerBasic.readUInt32LE(blob, off));
                off += 16;
            }
            sections.add(entry);
        }
        return sections;
    }

    // ---- 段装配 ----
    private static byte[] assembleSection(byte[] data, SecEntry section, Map<Integer, PageSlot> lookup) throws Exception {
        if (section.encrypted == 1) throw new Exception("暂不支持加密段");
        if (section.pageIDs.isEmpty()) return new byte[0];

        int capacity = section.maxDecompressedSz * section.pageIDs.size();
        byte[] out = new byte[capacity];
        int outLen = 0;

        for (int pageID : section.pageIDs) {
            PageSlot slot = lookup.get(pageID);
            if (slot == null) throw new Exception("找不到页 id=" + pageID);

            int pageOffset = (int) slot.address;
            if (pageOffset + 32 > data.length) throw new Exception("页头越界");
            byte[] header = ContainerBasic.unmaskPageHeader(data, slot.address, pageOffset);
            
            if (ContainerBasic.readUInt32LE(header, 0) != ContainerBasic.DataSectionMagic) {
                throw new Exception("数据页签名不匹配");
            }
            int packedSize = ContainerBasic.readUInt32LE(header, 8);
            int dataStart = pageOffset + 32;
            
            if (section.compressed == 2) {
                byte[] packed = new byte[packedSize];
                System.arraycopy(data, dataStart, packed, 0, packedSize);
                byte[] pageData = Lz77Decompressor.decompress(packed, section.maxDecompressedSz);
                System.arraycopy(pageData, 0, out, outLen, pageData.length);
                outLen += pageData.length;
            } else {
                System.arraycopy(data, dataStart, out, outLen, packedSize);
                outLen += packedSize;
            }
        }

        int finalLen = (int) Math.min(outLen, section.size);
        byte[] result = new byte[finalLen];
        System.arraycopy(out, 0, result, 0, finalLen);
        return result;
    }

    // ---- R2007 独立容器解析 (去交织 + R21) ----
    private static byte[] r2007Deinterleave(byte[] src, int k, int blockCount, int method) throws Exception {
        int outSize = k * blockCount;
        if (outSize == 0) return new byte[0];
        if (src.length < outSize) throw new Exception("R2007 RS 输入不足");
        byte[] out = new byte[outSize];
        if (method == 4) {
            for (int p = 0; p < outSize; p++) out[p] = src[(p % k) * blockCount + (p / k)];
            return out;
        } else if (method == 1) {
            System.arraycopy(src, 0, out, 0, outSize);
            return out;
        }
        throw new Exception("不支持的 R2007 RS 方法");
    }

    private static ContainerModels.R2007Header decodeR2007Header(byte[] data) throws Exception {
        if (data.length < 0x80 + 0x3D8) throw new Exception("缺少 R2007 第二头部");
        byte[] encoded = new byte[0x3D8];
        System.arraycopy(data, 0x80, encoded, 0, 0x3D8);
        byte[] decoded = r2007Deinterleave(encoded, 239, 3, 4);

        int compSize = ContainerBasic.readUInt32LE(decoded, 24);
        byte[] body;
        if (compSize < 0) { // 这理论上会有符号问题，但原Go也是这样处理
            int size = -compSize;
            body = new byte[size];
            System.arraycopy(decoded, 0x20, body, 0, size);
        } else if (compSize > 0) {
            byte[] packed = new byte[compSize];
            System.arraycopy(decoded, 0x20, packed, 0, compSize);
            body = R21Decompressor.decompress(packed, 0x110);
        } else {
            throw new Exception("R2007 第二头部 compressedSize 为 0");
        }

        ContainerModels.R2007Header hdr = new ContainerModels.R2007Header();
        hdr.pagesMapOffset = ContainerBasic.readUInt64LE(body, 7 * 8);
        hdr.pagesMapSizeCompressed = ContainerBasic.readUInt64LE(body, 10 * 8);
        hdr.pagesMapSizeUncompressed = ContainerBasic.readUInt64LE(body, 11 * 8);
        hdr.pagesMapCorrectionFactor = ContainerBasic.readUInt64LE(body, 3 * 8);
        hdr.sectionsMapID = ContainerBasic.readUInt64LE(body, 24 * 8);
        hdr.sectionsMapSizeCompressed = ContainerBasic.readUInt64LE(body, 22 * 8);
        hdr.sectionsMapSizeUncompressed = ContainerBasic.readUInt64LE(body, 25 * 8);
        hdr.sectionsMapCorrectionFactor = ContainerBasic.readUInt64LE(body, 27 * 8);
        hdr.sectionsAmount = ContainerBasic.readUInt64LE(body, 20 * 8);
        return hdr;
    }

    private static List<ContainerModels.R2007PageSlot> parseR2007PageMap(byte[] data, ContainerModels.R2007Header hdr) throws Exception {
        long address = 0x480 + hdr.pagesMapOffset;
        byte[] sysData = readR2007SystemPage(data, address, hdr.pagesMapSizeCompressed, hdr.pagesMapSizeUncompressed, hdr.pagesMapCorrectionFactor);
        
        List<ContainerModels.R2007PageSlot> slots = new ArrayList<>();
        long current = 0x480;
        int off = 0;
        while (off + 16 <= sysData.length) {
            long size = ContainerBasic.readUInt64LE(sysData, off);
            long id = ContainerBasic.readUInt64LE(sysData, off + 8);
            off += 16;
            if (size == 0 && id == 0) break;
            
            ContainerModels.R2007PageSlot slot = new ContainerModels.R2007PageSlot();
            slot.id = id; slot.size = size; slot.address = current;
            slots.add(slot);
            current += size;
        }
        return slots;
    }

    private static byte[] readR2007SystemPage(byte[] data, long address, long sizeCompressed, long sizeUncompressed, long correctionFactor) throws Exception {
        long compressedPadded = (sizeCompressed + 7) / 8 * 8;
        long rsPreEncoded = compressedPadded * correctionFactor;
        int blockCount = (int) ((rsPreEncoded + 238) / 239);
        long pageSize = ((blockCount * 255L) + 0x1F) / 0x20 * 0x20;
        
        int start = (int) address;
        int end = (int) (address + pageSize);
        if (start > data.length || end > data.length) throw new Exception("R2007 系统页越界");
        
        byte[] enc = new byte[end - start];
        System.arraycopy(data, start, enc, 0, enc.length);
        byte[] decoded = r2007Deinterleave(enc, 239, blockCount, 4);
        
        if (sizeCompressed < sizeUncompressed) {
            byte[] packed = new byte[(int)sizeCompressed];
            System.arraycopy(decoded, 0, packed, 0, packed.length);
            return R21Decompressor.decompress(packed, (int)sizeUncompressed);
        }
        byte[] out = new byte[(int)sizeUncompressed];
        System.arraycopy(decoded, 0, out, 0, out.length);
        return out;
    }

    private static byte[] loadNamedSectionDataR2007(byte[] data, String name) throws Exception {
        ContainerModels.R2007Header hdr = decodeR2007Header(data);
        List<ContainerModels.R2007PageSlot> pages = parseR2007PageMap(data, hdr);
        
        ContainerModels.R2007PageSlot secMapPage = null;
        for (ContainerModels.R2007PageSlot p : pages) {
            if (p.id == hdr.sectionsMapID) { secMapPage = p; break; }
        }
        if (secMapPage == null) throw new Exception("R2007 段表页不存在");

        byte[] sysData = readR2007SystemPage(data, secMapPage.address, hdr.sectionsMapSizeCompressed, hdr.sectionsMapSizeUncompressed, hdr.sectionsMapCorrectionFactor);
        
        int off = 0;
        while (off + 64 <= sysData.length) {
            long size = ContainerBasic.readUInt64LE(sysData, off);
            long nameLen = ContainerBasic.readUInt64LE(sysData, off + 32);
            long encoded = ContainerBasic.readUInt64LE(sysData, off + 48);
            long pageCount = ContainerBasic.readUInt64LE(sysData, off + 56);
            off += 64;

            if (size == 0 && pageCount == 0 && nameLen == 0) break;
            String secName = new String(sysData, off, (int) nameLen, StandardCharsets.UTF_16LE);
            int nullIdx = secName.indexOf('\0');
            if (nullIdx >= 0) secName = secName.substring(0, nullIdx);
            off += nameLen;

            if (secName.equals(name)) {
                // 读取装配
                return assembleR2007Section(data, size, encoded, sysData, off, pageCount, pages);
            }
            off += pageCount * 56; // 跳过页信息
        }
        throw new Exception("段 " + name + " 不存在 (R2007)");
    }

    private static byte[] assembleR2007Section(byte[] data, long size, long encoded, byte[] sysData, int off, long pageCount, List<ContainerModels.R2007PageSlot> pages) throws Exception {
        byte[] out = new byte[(int) size];
        for (int i = 0; i < pageCount; i++) {
            long pgOffset = ContainerBasic.readUInt64LE(sysData, off);
            long pgId = ContainerBasic.readUInt64LE(sysData, off + 16);
            long pgSizeUC = ContainerBasic.readUInt64LE(sysData, off + 24);
            long pgSizeC = ContainerBasic.readUInt64LE(sysData, off + 32);
            off += 56;

            ContainerModels.R2007PageSlot slot = null;
            for (ContainerModels.R2007PageSlot p : pages) if (p.id == pgId) slot = p;
            if (slot == null) continue;

            byte[] pageData = readR2007DataPage(data, slot, (int)encoded, (int)pgSizeC, (int)pgSizeUC);
            int start = (int) pgOffset;
            if (start >= out.length) continue;
            int end = Math.min(start + pageData.length, out.length);
            System.arraycopy(pageData, 0, out, start, end - start);
        }
        return out;
    }

    private static byte[] readR2007DataPage(byte[] data, ContainerModels.R2007PageSlot slot, int encoded, int sizeC, int sizeUC) throws Exception {
        long compressedPadded = (sizeC + 7) / 8 * 8;
        int blocks = (int) ((compressedPadded + 250) / 251);
        long minSize = 251L * blocks;
        long readSize = Math.max(slot.size, minSize);
        
        int start = (int) slot.address;
        int end = (int) (slot.address + readSize);
        byte[] pageBuf = new byte[end - start];
        System.arraycopy(data, start, pageBuf, 0, pageBuf.length);

        byte[] dec = r2007Deinterleave(pageBuf, 251, blocks, encoded);
        if (sizeC < sizeUC) {
            byte[] packed = new byte[sizeC];
            System.arraycopy(dec, 0, packed, 0, sizeC);
            return R21Decompressor.decompress(packed, sizeUC);
        }
        byte[] res = new byte[sizeUC];
        System.arraycopy(dec, 0, res, 0, sizeUC);
        return res;
    }

    // ---- R2000 (不压缩目录) ----
    public static byte[] readR2000Section(byte[] data, int recordNo) throws Exception {
        if (data.length < 0x15 + 4) throw new Exception("缺少 R2000 目录");
        int count = ContainerBasic.readUInt32LE(data, 0x15);
        int off = 0x19;
        for (int i = 0; i < count && off + 9 <= data.length; i++) {
            int rn = data[off] & 0xFF;
            long offset = ContainerBasic.readUInt32LE(data, off + 1);
            long size = ContainerBasic.readUInt32LE(data, off + 5);
            off += 9;
            if (rn == recordNo) {
                int start = (int) offset;
                int end = (int) (offset + size);
                if (start > data.length || end > data.length) throw new Exception("R2000 段越界");
                byte[] out = new byte[(int) size];
                System.arraycopy(data, start, out, 0, (int) size);
                return out;
            }
        }
        throw new Exception("R2000 段 " + recordNo + " 不存在");
    }
}
