package com.hdm;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/// 从 .xlsx 读项目数据（0 依赖，JDK 自带 ZIP + XML）
public class XlsxProjectData {

    public static ProjectData Load(String xlsxPath) {
        File f = new File(xlsxPath);
        if (!f.exists()) throw new RuntimeException("项目文件不存在: " + xlsxPath);

        XlsxReader xlsx = new XlsxReader(xlsxPath);

        ProjectData data = new ProjectData();

        data.config = readConfig(xlsx);
        data.pqx = readMatrix(xlsx, "平曲线", 8);
        data.sqx = readMatrix(xlsx, "竖曲线", 3);
        data.kzb = readMatrix(xlsx, "桩号", 1);
        double[][] xyz = readMatrix(xlsx, "原地面", 3);
        data.leftWidths = readWidths(xlsx, "左板块");
        data.rightWidths = readWidths(xlsx, "右板块");
        data.slopeData = readSlope(xlsx, "边坡");
        data.leftStructures = readLeftStructures(xlsx, "左结构层");
        data.rightStructures = readRightStructures(xlsx, "右结构层");
        data.leftCrossfalls = readMatrix(xlsx, "左结构层横坡", 2);
        data.rightCrossfalls = readMatrix(xlsx, "右结构层横坡", 2);
// 坐标系配置
        readProjectionConfig(xlsx, data);

// 控制点
        readControlPoints(xlsx, data);
        data.mesh = LL.hua_Fs_Batch(data.pqx, xyz);

        data.calculator = new SectionCalculator(
        data.mesh, data.pqx, data.sqx,
        data.leftWidths, data.rightWidths, data.slopeData,
        data.leftCrossfalls, data.rightCrossfalls,
        data.leftStructures, data.rightStructures,
        data.config.clearDepth);

        return data;
    }

// ============================================================
// 读"配置"里的"投影方式 / 中央子午线 / 南北半球"
// ============================================================
    private static void readProjectionConfig(XlsxReader xlsx, ProjectData data) {
        List<Map<Integer, String>> rows = readRows(xlsx, "配置");
        for (int i = 0; i < rows.size(); i++) {
            Map<Integer, String> row = rows.get(i);
            String key = getString(row, 0).trim();
            String val = getString(row, 1).trim();
            if (key.isEmpty()) continue;

            if ("投影方式".equals(key)) {
                data.projection = val.toLowerCase();
            } else if ("中央子午线".equals(key)) {
                try {
                    data.lonCenter = Double.parseDouble(val);
                } catch (Exception e) {
                }
            } else if ("南北半球".equals(key)) {
                data.isNorthern = val.contains("北");
            }
        }
    }

// ============================================================
// 读"控制点" sheet
// 格式：名字, X, Y, Z, 经度, 纬度
// ============================================================
    private static void readControlPoints(XlsxReader xlsx, ProjectData data) {
        List<Map<Integer, String>> rows;
        try {
            rows = readRows(xlsx, "控制点");
        } catch (Exception e) {
            // sheet 不存在
            data.controlLonLat = new double[0][2];
            data.controlXy = new double[0][2];
            return;
        }

        List<double[]> llList = new ArrayList<double[]>();
        List<double[]> xyList = new ArrayList<double[]>();

        for (int i = 0; i < rows.size(); i++) {
            Map<Integer, String> row = rows.get(i);
            if (isEmptyRow(row)) continue;

            // 第 0 列：名字（忽略）
            // 第 1、2、3 列：X、Y、Z
            // 第 4、5 列：经度、纬度
            double x = getDouble(row, 1);
            double y = getDouble(row, 2);
            double lon = getDouble(row, 4);
            double lat = getDouble(row, 5);

            if (x == 0 && y == 0) continue; // 无效行

            xyList.add(new double[]{x, y});
            llList.add(new double[]{lon, lat});
        }

        data.controlXy = new double[xyList.size()][2];
        data.controlLonLat = new double[llList.size()][2];
        for (int i = 0; i < xyList.size(); i++) {
            data.controlXy[i][0] = xyList.get(i)[0];
            data.controlXy[i][1] = xyList.get(i)[1];
            data.controlLonLat[i][0] = llList.get(i)[0];
            data.controlLonLat[i][1] = llList.get(i)[1];
        }
    }

    // ============================================================
    // 读 sheet
    // ============================================================
    private static List<Map<Integer, String>> readRows(XlsxReader xlsx, String sheetName) {
        return xlsx.readSheet(sheetName);
    }

    private static double[][] readMatrix(XlsxReader xlsx, String sheetName, int cols) {
        List<Map<Integer, String>> rows = readRows(xlsx, sheetName);
        List<double[]> list = new ArrayList<>();
        for (Map<Integer, String> row : rows) {
            if (isEmptyRow(row)) continue;
            double[] d = new double[cols];
            for (int j = 0; j < cols; j++) d[j] = getDouble(row, j);
            list.add(d);
        }
        double[][] m = new double[list.size()][];
        for (int i = 0; i < list.size(); i++) m[i] = list.get(i);
        return m;
    }

    private static List<DuanMianShuJu> readWidths(XlsxReader xlsx, String sheetName) {
        List<Map<Integer, String>> rows = readRows(xlsx, sheetName);
        List<DuanMianShuJu> list = new ArrayList<>();
        for (Map<Integer, String> row : rows) {
            if (isEmptyRow(row)) continue;
            DuanMianShuJu d = new DuanMianShuJu();
            d.zhuangHao = getDouble(row, 0);

            // Java 7：不用 stream，改成 for
            int maxKey = 0;
            for (Integer k : row.keySet()) {
                if (k != null && k > maxKey) maxKey = k;
            }

            for (int j = 1; j + 1 <= maxKey; j += 2) {
                if (isCellEmpty(row, j) && isCellEmpty(row, j + 1)) break;
                d.banKuaiJiHe.add(new double[]{getDouble(row, j), getDouble(row, j + 1)});
            }
            list.add(d);
        }
        return list;
    }

    private static List<BianPoDuanLuo> readSlope(XlsxReader xlsx, String sheetName) {
        List<Map<Integer, String>> rows = readRows(xlsx, sheetName);
        List<BianPoDuanLuo> result = new ArrayList<>();
        BianPoDuanLuo cur = null;
        for (Map<Integer, String> row : rows) {
            if (isEmptyRow(row)) continue;
            Double k1 = tryGetDouble(row, 0);
            Double k2 = tryGetDouble(row, 1);
            String type = getString(row, 2).trim().toLowerCase();
            String dispStr = getString(row, 3);

            if (k1 != null && k2 != null) {
                cur = new BianPoDuanLuo();
                cur.qiShiZhuangHao = k1;
                cur.jieShuZhuangHao = k2;
                result.add(cur);
            }
            if (cur == null) continue;

            double[][] disp = parseDisp(dispStr);
            if ("leftfill".equals(type)) cur.zuoTian = disp;
            else if ("leftcut".equals(type)) cur.zuoWa = disp;
            else if ("rightfill".equals(type)) cur.youTian = disp;
            else if ("rightcut".equals(type)) cur.youWa = disp;
        }
        return result;
    }

    private static double[][] parseDisp(String s) {
        if (s == null || s.trim().isEmpty()) return new double[0][2];
        String[] parts = s.split(",");
        List<Double> nums = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            nums.add(Double.parseDouble(t));
        }
        int n = nums.size() / 2;
        double[][] m = new double[n][2];
        for (int i = 0; i < n; i++) {
            m[i][0] = nums.get(i * 2);
            m[i][1] = nums.get(i * 2 + 1);
        }
        return m;
    }

    private static List<
                    LeftJiegoucengConfig> readLeftStructures(XlsxReader xlsx, String sheetName) {
        List<Map<Integer, String>> rows = readRows(xlsx, sheetName);
        List<LeftJiegoucengConfig> list = new ArrayList<>();
        for (Map<Integer, String> row : rows) {
            if (isEmptyRow(row)) continue;
            LeftJiegoucengConfig c = new LeftJiegoucengConfig();
            c.startStation = getDouble(row, 0);
            c.endStation = getDouble(row, 1);
            c.layerIndex = (int) getDouble(row, 2);
            c.layerName = getString(row, 3).trim();
            c.thickness = getDouble(row, 4);
            c.innerStepWidth = getDouble(row, 5);
            c.innerSlope = getDouble(row, 6);
            c.outerStepWidth = getDouble(row, 7);
            c.outerSlope = getDouble(row, 8);
            list.add(c);
        }
        return list;
    }

    private static List<
                    RightJiegoucengConfig> readRightStructures(XlsxReader xlsx, String sheetName) {
        List<Map<Integer, String>> rows = readRows(xlsx, sheetName);
        List<RightJiegoucengConfig> list = new ArrayList<>();
        for (Map<Integer, String> row : rows) {
            if (isEmptyRow(row)) continue;
            RightJiegoucengConfig c = new RightJiegoucengConfig();
            c.startStation = getDouble(row, 0);
            c.endStation = getDouble(row, 1);
            c.layerIndex = (int) getDouble(row, 2);
            c.layerName = getString(row, 3).trim();
            c.thickness = getDouble(row, 4);
            c.innerStepWidth = getDouble(row, 5);
            c.innerSlope = getDouble(row, 6);
            c.outerStepWidth = getDouble(row, 7);
            c.outerSlope = getDouble(row, 8);
            list.add(c);
        }
        return list;
    }

    private static AppConfig readConfig(XlsxReader xlsx) {
        AppConfig cfg = new AppConfig();
        List<Map<Integer, String>> rows = readRows(xlsx, "配置");
        for (Map<Integer, String> row : rows) {
            String key = getString(row, 0).trim();
            String val = getString(row, 1).trim();
            if (key.isEmpty()) continue;
            if ("ClearDepth".equals(key)) {
                try {
                    cfg.clearDepth = Double.parseDouble(val);
                } catch (Exception e) {
                }
            } else if ("XyzRows".equals(key)) {
                try {
                    cfg.xyzRows = Integer.parseInt(val);
                } catch (Exception e) {
                }
            } else if ("XyzCols".equals(key)) {
                try {
                    cfg.xyzCols = Integer.parseInt(val);
                } catch (Exception e) {
                }
            }
        }
        return cfg;
    }

    // ============================================================
    private static boolean isEmptyRow(Map<Integer, String> row) {
        if (row == null || row.isEmpty()) return true;
        for (String v : row.values())
            if (v != null && !v.trim().isEmpty()) return false;
        return true;
    }

    private static boolean isCellEmpty(Map<Integer, String> row, int idx) {
        String v = row.get(idx);
        return v == null || v.trim().isEmpty();
    }

    private static double getDouble(Map<Integer, String> row, int idx) {
        String v = row.get(idx);
        if (v == null || v.trim().isEmpty()) return 0;
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static Double tryGetDouble(Map<Integer, String> row, int idx) {
        String v = row.get(idx);
        if (v == null || v.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String getString(Map<Integer, String> row, int idx) {
        String v = row.get(idx);
        return v == null ? "" : v;
    }
}

/// 项目数据 + 计算器
class ProjectData {
    public AppConfig config;
    public double[][] pqx, sqx, kzb, mesh;
    public List<DuanMianShuJu> leftWidths, rightWidths;
    public List<BianPoDuanLuo> slopeData;
    public double[][] leftCrossfalls, rightCrossfalls;
    public List<LeftJiegoucengConfig> leftStructures;
    public List<RightJiegoucengConfig> rightStructures;
    public SectionCalculator calculator;
// 坐标系 / 控制点（画地图用）
    public String projection = "gauss"; // "gauss" / "utm"
    public double lonCenter = 0; // 中央子午线（高斯用，UTM 自动算）
    public boolean isNorthern = false; // 南北半球
    public double[][] controlLonLat; // N×2：[经度, 纬度]
    public double[][] controlXy; // N×2：[X, Y]

    public ComputeResult Query(double station) {
        return calculator.Compute(station);
    }

    public double[] Stations() {
        double[] arr = new double[kzb.length];
        for (int i = 0; i < kzb.length; i++) arr[i] = kzb[i][0];
        return arr;
    }
}

// ============================================================
// .xlsx 读取器（ZIP + XML）—— Android 兼容版
// ============================================================
class XlsxReader {

    private final ZipFile zip;
    private final Map<String, String> sheetNameToPath = new HashMap<String, String>();
    private List<String> sharedStrings = new ArrayList<String>();

    public XlsxReader(String xlsxPath) {
        try {
            this.zip = new ZipFile(xlsxPath);
            loadWorkbook();
            loadSharedStrings();
        } catch (Exception e) {
            throw new RuntimeException("打开 xlsx 失败: " + xlsxPath, e);
        }
    }

    private void loadWorkbook() throws Exception {
        Map<String, String> rIdToName = new LinkedHashMap<String, String>();
        Document doc = parseXml(readEntry("xl/workbook.xml"));
        NodeList sheets = doc.getElementsByTagName("sheet");
        for (int i = 0; i < sheets.getLength(); i++) {
            Element el = (Element) sheets.item(i);
            String name = el.getAttribute("name");
            String rId = el.getAttribute("r:id");
            if (rId == null || rId.isEmpty()) {
                rId = el.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            }
            rIdToName.put(rId, name);
        }

        Document relDoc = parseXml(readEntry("xl/_rels/workbook.xml.rels"));
        NodeList rels = relDoc.getElementsByTagName("Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element el = (Element) rels.item(i);
            String id = el.getAttribute("Id");
            String target = el.getAttribute("Target");
            if (rIdToName.containsKey(id)) {
                String name = rIdToName.get(id);
                if (!target.startsWith("xl/")) target = "xl/" + target;
                sheetNameToPath.put(name, target);
            }
        }
    }

    private void loadSharedStrings() {
        if (!hasEntry("xl/sharedStrings.xml")) return;
        try {
            Document doc = parseXml(readEntry("xl/sharedStrings.xml"));
            NodeList siList = doc.getElementsByTagName("si");
            for (int i = 0; i < siList.getLength(); i++) {
                Element si = (Element) siList.item(i);
                NodeList tList = si.getElementsByTagName("t");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < tList.getLength(); j++) {
                    sb.append(tList.item(j).getTextContent());
                }
                sharedStrings.add(sb.toString());
            }
        } catch (Exception e) {
            // sharedStrings 可能不存在
        }
    }

    public List<Map<Integer, String>> readSheet(String sheetName) {
        String path = sheetNameToPath.get(sheetName);
        if (path == null) throw new RuntimeException("找不到 sheet: " + sheetName);

        List<Map<Integer, String>> rows = new ArrayList<Map<Integer, String>>();
        try {
            Document doc = parseXml(readEntry(path));
            NodeList rowList = doc.getElementsByTagName("row");

            for (int i = 0; i < rowList.getLength(); i++) {
                Element row = (Element) rowList.item(i);

                String rAttr = row.getAttribute("r");
                if (rAttr != null && rAttr.equals("1")) continue;

                Map<Integer, String> map = new HashMap<Integer, String>();
                NodeList cellList = row.getElementsByTagName("c");
                for (int j = 0; j < cellList.getLength(); j++) {
                    Element c = (Element) cellList.item(j);
                    String ref = c.getAttribute("r");
                    String type = c.getAttribute("t");

                    int colIdx = colRefToIndex(ref);
                    String val = readCellValue(c, type);
                    if (val != null && !val.isEmpty()) map.put(Integer.valueOf(colIdx), val);
                }
                rows.add(map);
            }
        } catch (Exception e) {
            throw new RuntimeException("读 sheet 失败: " + sheetName, e);
        }
        return rows;
    }

    private String readCellValue(Element c, String type) {
        if ("inlineStr".equals(type)) {
            NodeList isList = c.getElementsByTagName("is");
            if (isList.getLength() > 0) {
                Element is = (Element) isList.item(0);
                NodeList tList = is.getElementsByTagName("t");
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < tList.getLength(); k++)
                    sb.append(tList.item(k).getTextContent());
                return sb.toString();
            }
        }

        NodeList vList = c.getElementsByTagName("v");
        if (vList.getLength() == 0) return null;
        String v = vList.item(0).getTextContent();
        if (v == null) return null;

        if ("s".equals(type)) {
            int idx = Integer.parseInt(v.trim());
            if (idx >= 0 && idx < sharedStrings.size()) return sharedStrings.get(idx);
            return "";
        }
        return v;
    }

    private static int colRefToIndex(String ref) {
        if (ref == null || ref.isEmpty()) return 0;
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') col = col * 26 + (ch - 'A' + 1);
            else break;
        }
        return col - 1;
    }

    // ============================================================
    private byte[] readEntry(String path) throws IOException {
        ZipEntry e = zip.getEntry(path);
        if (e == null) throw new IOException("找不到 zip 内文件: " + path);
        InputStream in = zip.getInputStream(e);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        } finally {
            in.close();
        }
        return bos.toByteArray();
    }

    private boolean hasEntry(String path) {
        return zip.getEntry(path) != null;
    }

    /// Android 兼容：setFeature 包 try-catch
    private static Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);

        try {
            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception ignore) {
        }
        try {
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception ignore) {
        }
        try {
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignore) {
        }

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(bytes));
    }
}