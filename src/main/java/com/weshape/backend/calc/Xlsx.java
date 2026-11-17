package com.weshape.backend.calc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the cell values of an Excel workbook (.xlsx): a zip of XML files. Only what the calculation
 * tables need: text and numbers by sheet, row and column, with formulas read as their last saved
 * value. No library, so the file is read the same way everywhere.
 */
public final class Xlsx {

    /** Sheet name → rows (from row 1) → cells (from column A); a missing cell is null. */
    private final Map<String, List<List<Object>>> sheets;

    private Xlsx(Map<String, List<List<Object>>> sheets) {
        this.sheets = sheets;
    }

    /** Reads the workbook; throws IllegalArgumentException when it is not a readable .xlsx. */
    public static Xlsx read(InputStream in) {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory() && (e.getName().endsWith(".xml") || e.getName().endsWith(".rels"))) {
                    parts.put(e.getName(), zip.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Not a readable .xlsx file: " + e.getMessage(), e);
        }
        if (!parts.containsKey("xl/workbook.xml")) {
            throw new IllegalArgumentException("Not an .xlsx file: xl/workbook.xml is missing");
        }
        List<String> shared = new ArrayList<>();
        if (parts.containsKey("xl/sharedStrings.xml")) {
            NodeList items = xml(parts.get("xl/sharedStrings.xml")).getElementsByTagName("si");
            for (int i = 0; i < items.getLength(); i++) shared.add(text((Element) items.item(i)));
        }
        Map<String, String> targets = new HashMap<>();
        NodeList rels = xml(parts.get("xl/_rels/workbook.xml.rels")).getElementsByTagName("Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            String target = r.getAttribute("Target");
            if (target.startsWith("/")) target = target.substring(1);
            else if (!target.startsWith("xl/")) target = "xl/" + target;
            targets.put(r.getAttribute("Id"), target);
        }
        Map<String, List<List<Object>>> sheets = new HashMap<>();
        NodeList list = xml(parts.get("xl/workbook.xml")).getElementsByTagName("sheet");
        for (int i = 0; i < list.getLength(); i++) {
            Element s = (Element) list.item(i);
            String id = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            byte[] part = parts.get(targets.get(id));
            if (part != null) sheets.put(s.getAttribute("name"), cells(xml(part), shared));
        }
        return new Xlsx(sheets);
    }

    /** The rows of a sheet; throws IllegalArgumentException when there is no such sheet. */
    public List<List<Object>> sheet(String name) {
        List<List<Object>> rows = sheets.get(name);
        if (rows == null) throw new IllegalArgumentException("Sheet \"" + name + "\" is missing");
        return rows;
    }

    private static List<List<Object>> cells(Document doc, List<String> shared) {
        List<List<Object>> rows = new ArrayList<>();
        NodeList cs = doc.getElementsByTagName("c");
        for (int i = 0; i < cs.getLength(); i++) {
            Element c = (Element) cs.item(i);
            String ref = c.getAttribute("r");
            int col = 0;
            int k = 0;
            while (k < ref.length() && Character.isLetter(ref.charAt(k))) {
                col = col * 26 + (ref.charAt(k) - 'A' + 1);
                k++;
            }
            int row = Integer.parseInt(ref.substring(k));
            Object value = value(c, shared);
            while (rows.size() < row) rows.add(new ArrayList<>());
            List<Object> r = rows.get(row - 1);
            while (r.size() < col) r.add(null);
            r.set(col - 1, value);
        }
        return rows;
    }

    private static Object value(Element c, List<String> shared) {
        String type = c.getAttribute("t");
        if (type.equals("inlineStr")) {
            NodeList is = c.getElementsByTagName("is");
            return is.getLength() == 0 ? null : text((Element) is.item(0));
        }
        NodeList vs = c.getElementsByTagName("v");
        if (vs.getLength() == 0) return null;
        String v = vs.item(0).getTextContent();
        // A formula saved without its result (some tools do that) has an empty value.
        if (v.isBlank() && !type.equals("str")) return null;
        return switch (type) {
            case "s" -> shared.get(Integer.parseInt(v.strip()));
            case "str", "e" -> v;
            case "b" -> v.strip().equals("1");
            default -> Double.parseDouble(v.strip());
        };
    }

    /** All text of a string item, runs included. */
    private static String text(Element e) {
        StringBuilder sb = new StringBuilder();
        NodeList ts = e.getElementsByTagName("t");
        for (int i = 0; i < ts.getLength(); i++) {
            Node t = ts.item(i);
            // Phonetic hints (rPh) are not part of the text.
            if (!t.getParentNode().getNodeName().equals("rPh")) sb.append(t.getTextContent());
        }
        return sb.toString();
    }

    private static Document xml(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("A part of the .xlsx file is missing");
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Broken XML in the .xlsx file: " + e.getMessage(), e);
        }
    }
}
