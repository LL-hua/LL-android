package com.hdm.cad.container;

public enum DwgVersion {
    VerR2000("AC1015"), VerR14("AC1014"), VerR13("AC1012"),
    VerR2004("AC1018"), VerR2007("AC1021"), VerR2010("AC1024"),
    VerR2013("AC1027"), VerR2018("AC1032"), VerR9("AC1004"),
    VerR10("AC1006"), VerR11("AC1009");

    private final String magic;
    DwgVersion(String magic) { this.magic = magic; }
    public String getMagic() { return magic; }

    public static DwgVersion detectVersion(byte[] data) throws Exception {
        if (data.length < 6) throw new Exception("文件过小，不是合法 DWG");
        String magic = new String(data, 0, 6);
        for (DwgVersion v : values()) {
            if (v.magic.equals(magic)) return v;
        }
        throw new Exception("不支持的 DWG 版本: " + magic);
    }

    public boolean isPreR13() { return this == VerR9 || this == VerR10 || this == VerR11; }
    public boolean isR2010Plus() { return this.ordinal() >= VerR2010.ordinal(); }
}
