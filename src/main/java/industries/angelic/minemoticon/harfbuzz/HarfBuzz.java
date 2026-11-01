package industries.angelic.minemoticon.harfbuzz;

// JNI bindings to the bundled HarfBuzz build (shaping and libharfbuzz-raster).
// Font handles are hb_font_t pointers. Only call these after HarfBuzzNative.load() succeeded.
// Kept free of other classes so native/build-zig.sh can generate the JNI header with javac -h.
public final class HarfBuzz {

    // glyphFlags() bits
    public static final int GLYPH_COLOR_PAINT = 1; // COLRv0/COLRv1
    public static final int GLYPH_PNG = 2; // CBDT/sbix bitmap
    public static final int GLYPH_SVG = 4; // OT-SVG, which the raster library doesn't draw
    public static final int GLYPH_INK = 8; // non-empty extents

    private HarfBuzz() {}

    public static native String version();

    // 0 if the data isn't a usable font
    public static native long createFont(byte[] data);

    // New font on the same face with the given variation axis values. Tags are big-endian 4-char ints.
    public static native long createVariationFont(long font, int[] axisTags, float[] axisValues);

    public static native void destroyFont(long font);

    // { unitsPerEm, ascender, descender } in font units
    public static native int[] fontMetrics(long font);

    public static native int nominalGlyph(long font, int codepoint);

    public static native int glyphFlags(long font, int glyph);

    // { xBearing, yBearing, width, height } in font units, y up (height is negative), or null if empty
    public static native int[] glyphExtents(long font, int glyph);

    public static native int glyphAdvance(long font, int glyph);

    // Per output glyph { glyph, cluster, xAdvance, xOffset, yOffset } in font units.
    // Default ignorables the font doesn't consume (stray ZWJ, variation selectors) are removed.
    public static native int[] shape(long font, int[] codepoints);

    // Glyph painted at `scale` pixels per font unit: { width, height, left, top, pixels... } with
    // premultiplied ARGB rows top-down. left/top locate the image's top-left corner relative to the
    // glyph origin, y up. Null if nothing was painted.
    public static native int[] paintGlyph(long font, int glyph, float scale, int foregroundArgb);
}
