package industries.angelic.minemoticon.harfbuzz;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import industries.angelic.minemoticon.colorfont.VariationAxis;
import industries.angelic.minemoticon.font.TextRunLayout;

/**
 * A font in HarfBuzz: shaping, plus color glyphs (COLRv0/v1, PNG bitmaps) and outlines drawn by
 * libharfbuzz-raster. SVG glyphs aren't drawn here since the raster library dropped them, so
 * ColorFont sends those to jsvg. The Java parsers in colorfont are the fallback when the native
 * library can't load.
 */
public final class HarfBuzzFont implements AutoCloseable {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int RENDERABLE = HarfBuzz.GLYPH_COLOR_PAINT | HarfBuzz.GLYPH_PNG | HarfBuzz.GLYPH_INK;

    private final long baseFont;
    private final Map<Integer, Integer> glyphFlags = new ConcurrentHashMap<>();
    // Separate immutable instances per variation setting, so concurrent renders never race
    private long regularFont;
    private long boldFont;
    // Line metrics in font units that size text glyphs, see textScale()
    private int lineAscent;
    private int lineDescent;

    private HarfBuzzFont(long baseFont, int lineAscent, int lineDescent) {
        this.baseFont = baseFont;
        this.regularFont = baseFont;
        this.lineAscent = lineAscent;
        this.lineDescent = lineDescent;
    }

    // Null if the native library or the font can't be loaded
    public static HarfBuzzFont load(byte[] fontData) {
        if (!HarfBuzzNative.load()) return null;
        long font = HarfBuzz.createFont(fontData);
        if (font == 0) return null;
        int[] metrics = HarfBuzz.fontMetrics(font);
        return new HarfBuzzFont(font, metrics[1], metrics[2]);
    }

    // ColorFont passes the hhea metrics its AWT text path uses, so bold and regular glyphs match
    public void setLineMetrics(int ascent, int descent) {
        this.lineAscent = ascent;
        this.lineDescent = descent;
    }

    public synchronized void configureVariations(List<VariationAxis> variationAxes, Map<String, Float> settings) {
        if (variationAxes == null || variationAxes.isEmpty()) return;

        int[] tags = new int[variationAxes.size()];
        float[] regular = new float[variationAxes.size()];
        int weightIndex = -1;
        for (int i = 0; i < variationAxes.size(); i++) {
            VariationAxis axis = variationAxes.get(i);
            tags[i] = tagOf(axis.getTag());
            float value = settings != null && settings.containsKey(axis.getTag()) ? settings.get(axis.getTag())
                : axis.getDefaultValue();
            regular[i] = clamp(value, axis.getMinValue(), axis.getMaxValue());
            if (weightIndex < 0 && "wght".equals(axis.getTag()) && axis.getMaxValue() >= 700.0f) {
                weightIndex = i;
            }
        }

        regularFont = HarfBuzz.createVariationFont(baseFont, tags, regular);
        if (weightIndex < 0) return;

        VariationAxis weightAxis = variationAxes.get(weightIndex);
        float boldWeight = clamp(
            Math.max(regular[weightIndex], 700.0f),
            weightAxis.getMinValue(),
            weightAxis.getMaxValue());
        if (Math.abs(boldWeight - regular[weightIndex]) < 0.01f) return;

        float[] bold = regular.clone();
        bold[weightIndex] = boldWeight;
        boldFont = HarfBuzz.createVariationFont(baseFont, tags, bold);
    }

    public boolean supportsBoldVariation() {
        return boldFont != 0;
    }

    public int nominalGlyph(int codepoint) {
        return HarfBuzz.nominalGlyph(baseFont, codepoint);
    }

    public int getGlyphFlags(int glyph) {
        return glyphFlags.computeIfAbsent(glyph, g -> HarfBuzz.glyphFlags(regularFont, g));
    }

    public boolean canRenderGlyphId(int glyph) {
        return glyph != 0 && (getGlyphFlags(glyph) & RENDERABLE) != 0;
    }

    public boolean canRenderGlyph(int codepoint) {
        return canRenderGlyphId(nominalGlyph(codepoint));
    }

    // The glyph an emoji sequence (ZWJ, flags, skin tones, keycaps) shapes to, or 0 if it isn't one glyph
    public int shapeToSingleGlyph(int[] codepoints) {
        int[] shaped = HarfBuzz.shape(baseFont, codepoints);
        return shaped != null && shaped.length == 5 ? shaped[0] : 0;
    }

    public BufferedImage renderGlyph(int codepoint, int size) {
        return renderGlyphId(nominalGlyph(codepoint), size);
    }

    // Glyph scaled to fill a size x size square, centered
    public BufferedImage renderGlyphId(int glyph, int size) {
        if (glyph == 0) return null;
        int[] extents = HarfBuzz.glyphExtents(regularFont, glyph);
        if (extents == null) return null;

        float inkSize = Math.max(Math.abs(extents[2]), Math.abs(extents[3]));
        int[] painted = HarfBuzz.paintGlyph(regularFont, glyph, size / inkSize, WHITE);
        BufferedImage image = toImage(painted);
        if (image == null) return null;

        var result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        // Rounding can make the painted image a pixel larger than the square
        float fit = Math.min(1.0f, Math.min((float) size / image.getWidth(), (float) size / image.getHeight()));
        int width = Math.max(1, Math.round(image.getWidth() * fit));
        int height = Math.max(1, Math.round(image.getHeight() * fit));
        g2d.drawImage(image, (size - width) / 2, (size - height) / 2, width, height, null);
        g2d.dispose();
        return result;
    }

    // Text glyph image laid out like ColorFont's AWT text glyphs: cell height = size, baseline at
    // pad + ascent, image starting at the glyph's left edge or the pen, whichever is further left
    public BufferedImage renderTextGlyph(int codepoint, int size, boolean bold) {
        TextGlyph glyph = loadTextGlyph(codepoint, size, bold);
        if (glyph == null) return null;

        float pad = textPad(size);
        float minX = Math.min(0.0f, glyph.left);
        float maxX = Math.max(glyph.advance, glyph.left + glyph.width());
        int imageWidth = Math.max(1, (int) Math.ceil(maxX - minX + pad * 2.0f));
        var result = new BufferedImage(imageWidth, size, BufferedImage.TYPE_INT_ARGB);
        if (glyph.image == null) return result;

        int baseline = (int) Math.rint(pad + lineAscent * textScale(size));
        int originX = (int) Math.rint(pad - minX);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(glyph.image, originX + glyph.left, baseline - glyph.top, null);
        g2d.dispose();
        return result;
    }

    public float getTextGlyphAdvance(int codepoint, int size, boolean bold) {
        int glyph = nominalGlyph(codepoint);
        if (glyph == 0) return -1.0f;
        return HarfBuzz.glyphAdvance(textFont(bold), glyph) * textScale(size);
    }

    public float getTextGlyphOffsetX(int codepoint, int size, boolean bold) {
        TextGlyph glyph = loadTextGlyph(codepoint, size, bold);
        if (glyph == null) return 0.0f;
        return -(float) Math.rint(textPad(size) - Math.min(0.0f, glyph.left));
    }

    // Pen position per codepoint, with the font's GPOS kerning. Null if shaping merges or drops codepoints.
    public TextRunLayout layoutTextRun(String text, int size, boolean bold) {
        if (text == null || text.isEmpty()) return null;

        int[] codepoints = text.codePoints()
            .toArray();
        int[] shaped = HarfBuzz.shape(textFont(bold), codepoints);
        if (shaped == null || shaped.length != codepoints.length * 5) return null;

        float scale = textScale(size);
        float[] penPositions = new float[codepoints.length];
        float penX = 0.0f;
        for (int i = 0; i < codepoints.length; i++) {
            if (shaped[i * 5] == 0) return null;
            penPositions[i] = penX;
            penX += shaped[i * 5 + 2] * scale;
        }
        return new TextRunLayout(penPositions, penX);
    }

    @Override
    public synchronized void close() {
        if (boldFont != 0) HarfBuzz.destroyFont(boldFont);
        if (regularFont != baseFont) HarfBuzz.destroyFont(regularFont);
        HarfBuzz.destroyFont(baseFont);
        boldFont = 0;
        regularFont = 0;
    }

    private long textFont(boolean bold) {
        return bold && boldFont != 0 ? boldFont : regularFont;
    }

    // Same scale as ColorFont's AWT text path: the line's full ascent-to-descent height fits the
    // cell, so tall accents and descenders aren't clipped
    private float textScale(int size) {
        double lineHeight = Math.max(1.0d, lineAscent - lineDescent);
        return (float) Math.max(0.01d, (size - textPad(size) * 2.0d) / lineHeight);
    }

    private static float textPad(int size) {
        return Math.max(1.0f, size * 0.03125f);
    }

    private TextGlyph loadTextGlyph(int codepoint, int size, boolean bold) {
        int glyph = nominalGlyph(codepoint);
        if (glyph == 0) return null;
        long font = textFont(bold);
        float scale = textScale(size);
        float advance = HarfBuzz.glyphAdvance(font, glyph) * scale;
        int[] painted = HarfBuzz.paintGlyph(font, glyph, scale, WHITE);
        if (painted == null) {
            // Blank glyphs like space still advance
            return advance > 0.0f ? new TextGlyph(null, 0, 0, advance) : null;
        }
        return new TextGlyph(toImage(painted), painted[2], painted[3], advance);
    }

    private static BufferedImage toImage(int[] painted) {
        if (painted == null || painted.length < 4) return null;
        int width = painted[0];
        int height = painted[1];
        if (width <= 0 || height <= 0) return null;
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] pixels = ((DataBufferInt) image.getRaster()
            .getDataBuffer()).getData();
        System.arraycopy(painted, 4, pixels, 0, width * height);
        return image;
    }

    private static int tagOf(String tag) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = value << 8 | (i < tag.length() ? tag.charAt(i) & 0xFF : ' ');
        }
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class TextGlyph {

        final BufferedImage image; // null for blank glyphs
        final int left; // image left edge relative to the pen
        final int top; // image top above the baseline
        final float advance;

        TextGlyph(BufferedImage image, int left, int top, float advance) {
            this.image = image;
            this.left = left;
            this.top = top;
            this.advance = advance;
        }

        int width() {
            return image != null ? image.getWidth() : 0;
        }
    }
}
