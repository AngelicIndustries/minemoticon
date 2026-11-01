package industries.angelic.minemoticon.colorfont;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// Parses GSUB ligature substitutions (lookup type 4, directly or through an extension) from the
// ccmp/liga/rlig features. Enough to turn emoji sequences (ZWJ, flags, skin tones) into their
// single ligature glyph when no shaping engine is available.
public class GsubParser {

    private static final String[] LIGATURE_FEATURES = { "ccmp", "liga", "rlig" };

    // One map per lookup, in lookup order: first glyph -> ligatures in preference order.
    // Each ligature is { ligatureGlyph, component2, ..., componentN }.
    private final List<Map<Integer, List<int[]>>> lookups = new ArrayList<>();

    public GsubParser(ByteBuffer gsub) {
        int base = gsub.position();
        int featureList = base + u16(gsub, base + 6);
        int lookupList = base + u16(gsub, base + 8);

        // Features list their lookups by index; lookups must run in index order
        var lookupIndices = new TreeSet<Integer>();
        int featureCount = u16(gsub, featureList);
        for (int i = 0; i < featureCount; i++) {
            int record = featureList + 2 + i * 6;
            if (!isLigatureFeature(tag(gsub, record))) continue;
            int feature = featureList + u16(gsub, record + 4);
            int indexCount = u16(gsub, feature + 2);
            for (int j = 0; j < indexCount; j++) {
                lookupIndices.add(u16(gsub, feature + 4 + j * 2));
            }
        }

        int lookupCount = u16(gsub, lookupList);
        for (int index : lookupIndices) {
            if (index >= lookupCount) continue;
            int lookup = lookupList + u16(gsub, lookupList + 2 + index * 2);
            int type = u16(gsub, lookup);
            int subtableCount = u16(gsub, lookup + 4);

            Map<Integer, List<int[]>> ligatures = new HashMap<>();
            for (int s = 0; s < subtableCount; s++) {
                int subtable = lookup + u16(gsub, lookup + 6 + s * 2);
                int subtableType = type;
                if (type == 7) {
                    subtableType = u16(gsub, subtable + 2);
                    subtable += gsub.getInt(subtable + 4);
                }
                if (subtableType == 4) {
                    readLigatureSubst(gsub, subtable, ligatures);
                }
            }
            if (!ligatures.isEmpty()) {
                lookups.add(ligatures);
            }
        }
    }

    public boolean hasLigatures() {
        return !lookups.isEmpty();
    }

    // Glyph the whole sequence turns into, or 0 if it doesn't reduce to a single glyph
    public int resolveLigature(int[] glyphIds) {
        if (glyphIds == null || glyphIds.length < 2) return 0;

        int[] glyphs = glyphIds.clone();
        int length = glyphs.length;
        for (Map<Integer, List<int[]>> lookup : lookups) {
            // Compacts in place: the write index never passes the read index
            int out = 0;
            for (int i = 0; i < length;) {
                int[] ligature = matchAt(lookup, glyphs, i, length);
                if (ligature != null) {
                    glyphs[out++] = ligature[0];
                    i += ligature.length;
                } else {
                    glyphs[out++] = glyphs[i++];
                }
            }
            length = out;
        }
        return length == 1 ? glyphs[0] : 0;
    }

    private static int[] matchAt(Map<Integer, List<int[]>> lookup, int[] glyphs, int start, int length) {
        List<int[]> candidates = lookup.get(glyphs[start]);
        if (candidates == null) return null;
        for (int[] ligature : candidates) {
            if (start + ligature.length > length) continue;
            boolean matches = true;
            for (int c = 1; c < ligature.length && matches; c++) {
                matches = glyphs[start + c] == ligature[c];
            }
            if (matches) return ligature;
        }
        return null;
    }

    private static void readLigatureSubst(ByteBuffer buf, int subtable, Map<Integer, List<int[]>> out) {
        if (u16(buf, subtable) != 1) return;
        int[] coverage = readCoverage(buf, subtable + u16(buf, subtable + 2));
        int setCount = Math.min(u16(buf, subtable + 4), coverage.length);
        for (int i = 0; i < setCount; i++) {
            int set = subtable + u16(buf, subtable + 6 + i * 2);
            int ligatureCount = u16(buf, set);
            List<int[]> list = out.computeIfAbsent(coverage[i], k -> new ArrayList<>());
            for (int j = 0; j < ligatureCount; j++) {
                int ligature = set + u16(buf, set + 2 + j * 2);
                int componentCount = u16(buf, ligature + 2);
                if (componentCount < 2) continue;
                int[] entry = new int[componentCount];
                entry[0] = u16(buf, ligature);
                for (int c = 1; c < componentCount; c++) {
                    entry[c] = u16(buf, ligature + 2 + c * 2);
                }
                list.add(entry);
            }
        }
    }

    // Glyph ids indexed by coverage index
    private static int[] readCoverage(ByteBuffer buf, int coverage) {
        int format = u16(buf, coverage);
        int count = u16(buf, coverage + 2);
        if (format == 1) {
            int[] glyphs = new int[count];
            for (int i = 0; i < count; i++) {
                glyphs[i] = u16(buf, coverage + 4 + i * 2);
            }
            return glyphs;
        }
        if (format == 2) {
            int size = 0;
            for (int i = 0; i < count; i++) {
                int range = coverage + 4 + i * 6;
                int end = u16(buf, range + 2);
                int start = u16(buf, range);
                size = Math.max(size, u16(buf, range + 4) + end - start + 1);
            }
            int[] glyphs = new int[size];
            for (int i = 0; i < count; i++) {
                int range = coverage + 4 + i * 6;
                int start = u16(buf, range);
                int end = u16(buf, range + 2);
                int startIndex = u16(buf, range + 4);
                for (int glyph = start; glyph <= end; glyph++) {
                    glyphs[startIndex + glyph - start] = glyph;
                }
            }
            return glyphs;
        }
        return new int[0];
    }

    private static boolean isLigatureFeature(String tag) {
        for (String feature : LIGATURE_FEATURES) {
            if (feature.equals(tag)) return true;
        }
        return false;
    }

    private static String tag(ByteBuffer buf, int offset) {
        char[] chars = new char[4];
        for (int i = 0; i < 4; i++) {
            chars[i] = (char) (buf.get(offset + i) & 0xFF);
        }
        return new String(chars);
    }

    private static int u16(ByteBuffer buf, int offset) {
        return buf.getShort(offset) & 0xFFFF;
    }
}
