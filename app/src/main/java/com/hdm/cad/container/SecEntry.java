package com.hdm.cad.container;

import java.util.ArrayList;
import java.util.List;

public class SecEntry {
    public long size;
    public int maxDecompressedSz;
    public int compressed;
    public int encrypted;
    public String name;
    public List<Integer> pageIDs = new ArrayList<>();
}
