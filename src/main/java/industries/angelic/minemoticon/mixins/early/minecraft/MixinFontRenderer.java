package industries.angelic.minemoticon.mixins.early.minecraft;

import java.lang.reflect.Method;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import industries.angelic.minemoticon.ClientEmojiHandler;
import industries.angelic.minemoticon.font.FontSource;
import industries.angelic.minemoticon.font.FontStack;
import industries.angelic.minemoticon.font.GlyphCache;
import industries.angelic.minemoticon.font.MinecraftFontSource;
import industries.angelic.minemoticon.font.TextRunLayout;
import industries.angelic.minemoticon.render.EmojiQuadBatch;
import industries.angelic.minemoticon.render.EmojiRenderer;
import industries.angelic.minemoticon.render.FontRendererEmojiCompat;
import industries.angelic.minemoticon.text.TextStyleCompat;

@Mixin(value = FontRenderer.class, priority = 2000)
public abstract class MixinFontRenderer implements FontRendererEmojiCompat {

    @Unique
    private static final float minemoticon$INLINE_EMOJI_Y_OFFSET = -(EmojiRenderer.EMOJI_SIZE - 8.0f) / 2.0f;

    @Unique
    private static final int minemoticon$MAX_EMOJI_TRIM_SEQUENCE_LENGTH = 64;

    @Unique
    private static final double minemoticon$WAVE_TIME_SCALE = 5.0E-9;

    @Unique
    private static final float minemoticon$WAVE_FREQUENCY = 0.5f;

    @Unique
    private static final float minemoticon$WAVE_AMPLITUDE = 2.0f;

    @Unique
    private static final int minemoticon$RAINBOW_LUT_SIZE = 24;

    @Unique
    private static final int[] minemoticon$RAINBOW_LUT = minemoticon$buildRainbowLut();

    @Unique
    private static boolean minemoticon$swanSongTextBridgeChecked = false;

    @Unique
    private static Method minemoticon$swanSongBeginTessellating = null;

    @Unique
    private static Method minemoticon$swanSongDraw = null;

    @Unique
    private static Method minemoticon$swanSongSetColor = null;

    @Shadow
    private float posX;

    @Shadow
    private float posY;

    @Shadow
    private float red;

    @Shadow
    private float green;

    @Shadow
    private float blue;

    @Shadow
    private float alpha;

    @Shadow
    private boolean bidiFlag;

    @Shadow
    private int[] colorCode;

    @Shadow
    private boolean randomStyle;

    @Shadow
    private boolean boldStyle;

    @Shadow
    private boolean italicStyle;

    @Shadow
    private boolean underlineStyle;

    @Shadow
    private boolean strikethroughStyle;

    @Shadow
    public int FONT_HEIGHT;

    @Shadow
    public abstract int getStringWidth(String text);

    @Shadow
    public abstract int getCharWidth(char c);

    @Shadow
    protected abstract void renderStringAtPos(String text, boolean shadow);

    @Shadow
    protected abstract String bidiReorder(String text);

    @Shadow
    protected abstract void resetStyles();

    @Shadow
    protected ResourceLocation locationFontTexture;

    @Unique
    private boolean minemoticon$rendering = false;

    @Unique
    private static final Random minemoticon$OBFUSCATION_RANDOM = new Random();

    // Emojis of the string being rendered, drawn grouped by texture once the string is done
    @Unique
    private final EmojiQuadBatch minemoticon$emojiBatch = new EmojiQuadBatch();

    @Unique
    private boolean minemoticon$measuringWidth = false;

    @Unique
    private boolean minemoticon$renderingCompatString = false;

    @Unique
    private int minemoticon$currentRenderColor = 0xFFFFFFFF;

    @Unique
    private int minemoticon$baseRenderColor = 0xFFFFFFFF;

    @Unique
    private boolean minemoticon$rainbowStyle = false;

    @Unique
    private boolean minemoticon$waveStyle = false;

    // &u: an extra drop shadow, optionally in its own color (-1 for the usual darkened one)
    @Unique
    private boolean minemoticon$extraShadowStyle = false;

    @Unique
    private int minemoticon$shadowColorOverride = -1;

    // &v: glyphs drawn upside down
    @Unique
    private boolean minemoticon$dinnerboneStyle = false;

    // A shadow pass that only runs for &u spans in text drawn without a drop shadow
    @Unique
    private boolean minemoticon$extraShadowPassOnly = false;

    @Unique
    private boolean minemoticon$gradientStyle = false;

    @Unique
    private int minemoticon$gradientStartRgb = 0;

    @Unique
    private int minemoticon$gradientEndRgb = 0;

    @Unique
    private int minemoticon$gradientCharIndex = 0;

    @Unique
    private int minemoticon$gradientTotalChars = 0;

    @Unique
    private int minemoticon$rainbowCharIndex = 0;

    @Unique
    private int minemoticon$visibleCharIndex = 0;

    @Unique
    private long minemoticon$styleTimeNanos = 0L;

    // --- Width measurement ---

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void minemoticon$fixStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (minemoticon$measuringWidth) return;
        if (ClientEmojiHandler.getFontStack() == null) return;
        if (minemoticon$isSplashFontRenderer()) return;

        minemoticon$measuringWidth = true;
        try {
            cir.setReturnValue((int) Math.ceil(minemoticon$measureCompatWidthExact(text)));
        } finally {
            minemoticon$measuringWidth = false;
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void minemoticon$trimStringToWidthCompat(String text, int width, boolean reverse,
        CallbackInfoReturnable<String> cir) {
        if (ClientEmojiHandler.getFontStack() == null) return;
        if (minemoticon$isSplashFontRenderer()) return;

        cir.setReturnValue(minemoticon$trimStringToWidthCompat(text, width, reverse));
    }

    @Inject(method = "sizeStringToWidth", at = @At("HEAD"), cancellable = true)
    private void minemoticon$sizeStringToWidthCompat(String text, int wrapWidth, CallbackInfoReturnable<Integer> cir) {
        if (ClientEmojiHandler.getFontStack() == null) return;
        if (minemoticon$isSplashFontRenderer()) return;

        cir.setReturnValue(minemoticon$sizeStringToWidthCompat(text, wrapWidth));
    }

    // --- drawString / renderString compat ---

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    private void minemoticon$drawStringCompat(String text, int x, int y, int color, boolean dropShadow,
        CallbackInfoReturnable<Integer> cir) {
        if (minemoticon$renderingCompatString) return;
        if (minemoticon$isSplashFontRenderer()) return;
        if (minemoticon$shouldUseVanillaMainMenuSplash(y)) return;
        if (!Minecraft.getMinecraft()
            .func_152345_ab()) return;
        if (!minemoticon$shouldUseCompatString(text)) return;

        minemoticon$renderingCompatString = true;
        try {
            cir.setReturnValue(minemoticon$drawStringVanillaCompat(text, x, y, color, dropShadow));
        } finally {
            minemoticon$renderingCompatString = false;
        }
    }

    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true)
    private void minemoticon$renderStringCompat(String text, int x, int y, int color, boolean dropShadow,
        CallbackInfoReturnable<Integer> cir) {
        if (minemoticon$renderingCompatString) return;
        if (minemoticon$isSplashFontRenderer()) return;
        if (minemoticon$shouldUseVanillaMainMenuSplash(y)) return;
        if (!Minecraft.getMinecraft()
            .func_152345_ab()) return;
        if (!minemoticon$shouldUseCompatString(text)) return;

        minemoticon$renderingCompatString = true;
        try {
            cir.setReturnValue(minemoticon$renderStringVanillaCompat(text, x, y, color, dropShadow));
        } finally {
            minemoticon$renderingCompatString = false;
        }
    }

    // --- Core rendering ---

    @Inject(method = "renderStringAtPos", at = @At("HEAD"), cancellable = true)
    private void minemoticon$renderWithEmojis(String text, boolean shadow, CallbackInfo ci) {
        if (!minemoticon$canRenderCompat()) return;

        ci.cancel();
        minemoticon$renderStringAtPosCompat(text, shadow);
    }

    @Unique
    private boolean minemoticon$canRenderCompat() {
        return !minemoticon$rendering && ClientEmojiHandler.getFontStack() != null
            && !minemoticon$isSplashFontRenderer()
            && Minecraft.getMinecraft()
                .func_152345_ab();
    }

    // Our renderStringAtPos. The compat draw calls it directly rather than through renderStringAtPos,
    // which Hodgepodge wraps (@WrapMethod) to strip &u before any injector sees the text.
    @Unique
    private void minemoticon$renderStringAtPosCompat(String text, boolean shadow) {
        minemoticon$rendering = true;
        try {
            text = TextStyleCompat.normalize(text);
            this.minemoticon$baseRenderColor = minemoticon$getBaseRenderColor();
            this.minemoticon$currentRenderColor = this.minemoticon$baseRenderColor;
            this.minemoticon$styleTimeNanos = System.nanoTime();
            minemoticon$resetExtendedStyles();
            minemoticon$applyRenderColor(this.minemoticon$currentRenderColor);
            minemoticon$renderTextSegment(text, shadow, true);
            if (!minemoticon$emojiBatch.isEmpty()) {
                minemoticon$emojiBatch.draw();
                minemoticon$applyRenderColor(this.minemoticon$currentRenderColor);
            }
        } finally {
            minemoticon$emojiBatch.discard();
            minemoticon$rendering = false;
        }
    }

    // For each codepoint: resolve via font stack. First font with the glyph wins.
    // MinecraftFontSource or null -> batch to vanilla. Anything else -> textured quad.
    @Unique
    private void minemoticon$renderTextSegment(String text, boolean shadow, boolean matchEmoji) {
        FontStack stack = ClientEmojiHandler.getFontStack();

        var vanillaBuf = new StringBuilder();
        MinemoticonRunStyle vanillaRunStyle = null;
        var customRunBuf = new StringBuilder();
        FontSource customRunSource = null;
        MinemoticonRunStyle customRunStyle = null;
        int i = 0;
        while (i < text.length()) {
            int tokenEnd = TextStyleCompat.tokenEnd(text, i);
            if (tokenEnd > i) {
                if (vanillaBuf.length() > 0) {
                    minemoticon$flushVanillaText(vanillaBuf, shadow, vanillaRunStyle);
                    vanillaRunStyle = null;
                }
                if (customRunBuf.length() > 0) {
                    minemoticon$flushCustomTextRun(customRunBuf, customRunSource, shadow, customRunStyle);
                    customRunSource = null;
                    customRunStyle = null;
                }
                minemoticon$applyFormattingToken(text, i, tokenEnd, shadow);
                i = tokenEnd;
                continue;
            }

            // Width is measured through EmojiRenderer.parse(), which drops the escaping backslash
            EmojiRenderer.EscapeMatch escaped = matchEmoji ? EmojiRenderer.matchEscapeAt(text, i) : null;
            if (escaped != null) {
                if (vanillaBuf.length() > 0) {
                    minemoticon$flushVanillaText(vanillaBuf, shadow, vanillaRunStyle);
                    vanillaRunStyle = null;
                }
                if (customRunBuf.length() > 0) {
                    minemoticon$flushCustomTextRun(customRunBuf, customRunSource, shadow, customRunStyle);
                    customRunSource = null;
                    customRunStyle = null;
                }
                minemoticon$renderTextSegment(escaped.getLiteralText(), shadow, false);
                i = escaped.getNextIndex();
                continue;
            }

            EmojiRenderer.EmojiMatch emoji = matchEmoji ? EmojiRenderer.matchAt(text, i) : null;
            if (emoji != null) {
                if (vanillaBuf.length() > 0) {
                    minemoticon$flushVanillaText(vanillaBuf, shadow, vanillaRunStyle);
                    vanillaRunStyle = null;
                }
                if (customRunBuf.length() > 0) {
                    minemoticon$flushCustomTextRun(customRunBuf, customRunSource, shadow, customRunStyle);
                    customRunSource = null;
                    customRunStyle = null;
                }
                minemoticon$renderEmoji(emoji, shadow);
                i += emoji.getLength();
                continue;
            }

            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);

            FontSource source = stack.resolve(cp);
            boolean isVanilla = source == null || source instanceof MinecraftFontSource;
            boolean isTextRunSource = minemoticon$isTextRunSource(source);

            if (isVanilla) {
                if (customRunBuf.length() > 0) {
                    minemoticon$flushCustomTextRun(customRunBuf, customRunSource, shadow, customRunStyle);
                    customRunSource = null;
                    customRunStyle = null;
                }
                if (vanillaBuf.length() == 0) {
                    vanillaRunStyle = minemoticon$captureRunStyle();
                }
                vanillaBuf.append(text, i, i + charCount);
                minemoticon$advanceVisibleStyle();
            } else if (isTextRunSource) {
                if (vanillaBuf.length() > 0) {
                    minemoticon$flushVanillaText(vanillaBuf, shadow, vanillaRunStyle);
                    vanillaRunStyle = null;
                }
                if (customRunSource != source && customRunBuf.length() > 0) {
                    minemoticon$flushCustomTextRun(customRunBuf, customRunSource, shadow, customRunStyle);
                    customRunStyle = null;
                }
                customRunSource = source;
                if (customRunBuf.length() == 0) {
                    customRunStyle = minemoticon$captureRunStyle();
                }
                customRunBuf.append(text, i, i + charCount);
                minemoticon$advanceVisibleStyle();
            } else {
                if (vanillaBuf.length() > 0) {
                    minemoticon$flushVanillaText(vanillaBuf, shadow, vanillaRunStyle);
                    vanillaRunStyle = null;
                }
                if (customRunBuf.length() > 0) {
                    minemoticon$flushCustomTextRun(customRunBuf, customRunSource, shadow, customRunStyle);
                    customRunSource = null;
                    customRunStyle = null;
                }
                minemoticon$renderFontStackGlyph(source, cp, shadow, minemoticon$captureRunStyle());
                minemoticon$advanceVisibleStyle();
            }
            i += charCount;
        }

        if (customRunBuf.length() > 0) {
            minemoticon$flushCustomTextRun(customRunBuf, customRunSource, shadow, customRunStyle);
        }
        if (vanillaBuf.length() > 0) {
            minemoticon$flushVanillaText(vanillaBuf, shadow, vanillaRunStyle);
        }
    }

    @Unique
    private void minemoticon$renderFontStackGlyph(FontSource source, int codepoint, boolean shadow) {
        minemoticon$renderFontStackGlyph(source, codepoint, shadow, minemoticon$captureRunStyle());
    }

    @Unique
    private void minemoticon$renderFontStackGlyph(FontSource source, int codepoint, boolean shadow,
        MinemoticonRunStyle style) {
        boolean textBold = minemoticon$usesTextBold(source, style.bold);
        GlyphCache cache = GlyphCache.forSource(source, textBold);
        int drawnGlyph = minemoticon$obfuscate(cache, codepoint, style);
        float[] uv = cache.getGlyphUV(drawnGlyph);
        float drawWidth = cache.getGlyphDrawWidth(drawnGlyph) * source.getWidthScale();
        float advance = cache.getGlyphAdvance(codepoint) * source.getWidthScale();
        float xOffset = cache.getGlyphOffsetX(drawnGlyph) * source.getWidthScale();
        float displayHeight = source.getDisplayHeight();
        float verticalOffset = source.getVerticalOffset();

        if (uv == null) {
            this.posX += advance > 0.0f ? advance : 8.0f;
            return;
        }

        int color = minemoticon$colorForStyle(style, style.rainbowStart, style.gradientStart, shadow);
        float yOffset = style.wave ? minemoticon$waveOffset(style.visibleStart) : 0.0f;
        boolean syntheticBold = minemoticon$usesSyntheticBold(source, style.bold);
        float vTop = style.dinnerbone ? uv[3] : uv[1];
        float vBottom = style.dinnerbone ? uv[1] : uv[3];
        boolean hidden = minemoticon$hiddenInShadowPass(style, shadow);
        if ((!shadow || source.usesTextColor()) && !hidden) {
            float x0 = this.posX + xOffset;
            float y0 = this.posY + yOffset + (8.0f - displayHeight) * 0.5f + verticalOffset;
            if (source.usesTextColor()) {
                x0 = Math.round(x0);
                y0 = Math.round(y0);
            }
            float x1 = x0 + drawWidth;
            float y1 = y0 + displayHeight;
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(cache.getResourceLocation());

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (source.usesTextColor()) {
                minemoticon$applyRenderColor(color);
            } else {
                GL11.glColor4f(1.0f, 1.0f, 1.0f, this.alpha);
            }

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_TRIANGLE_STRIP);
            tessellator.addVertexWithUV(x0, y0, 0.0, uv[0], vTop);
            tessellator.addVertexWithUV(x0, y1, 0.0, uv[0], vBottom);
            tessellator.addVertexWithUV(x1, y0, 0.0, uv[2], vTop);
            tessellator.addVertexWithUV(x1, y1, 0.0, uv[2], vBottom);
            tessellator.draw();

            if (source.usesTextColor()) {
                minemoticon$applyRenderColor(color);
            }
        }

        if (source.usesTextColor() && syntheticBold && !hidden) {
            float x0 = Math.round(this.posX + xOffset) + 1.0f;
            float y0 = Math.round(this.posY + yOffset + (8.0f - displayHeight) * 0.5f + verticalOffset);
            float x1 = x0 + drawWidth;
            float y1 = y0 + displayHeight;

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_TRIANGLE_STRIP);
            tessellator.addVertexWithUV(x0, y0, 0.0, uv[0], vTop);
            tessellator.addVertexWithUV(x0, y1, 0.0, uv[0], vBottom);
            tessellator.addVertexWithUV(x1, y0, 0.0, uv[2], vTop);
            tessellator.addVertexWithUV(x1, y1, 0.0, uv[2], vBottom);
            tessellator.draw();
        }

        if (advance <= 0.0f) {
            advance = drawWidth > 0.0f ? drawWidth : 8.0f;
        }
        if (syntheticBold && advance > 0.0f) {
            advance += 1.0f;
        }
        minemoticon$drawDecorationLines(advance, yOffset, style);
        this.posX += advance;
    }

    @Unique
    private void minemoticon$flushCustomTextRun(StringBuilder customRunBuf, FontSource source, boolean shadow) {
        minemoticon$flushCustomTextRun(customRunBuf, source, shadow, minemoticon$captureRunStyle());
    }

    @Unique
    private void minemoticon$flushCustomTextRun(StringBuilder customRunBuf, FontSource source, boolean shadow,
        MinemoticonRunStyle style) {
        if (customRunBuf.length() <= 0 || source == null) {
            customRunBuf.setLength(0);
            return;
        }
        if (style == null) {
            style = minemoticon$captureRunStyle();
        }

        String run = customRunBuf.toString();
        customRunBuf.setLength(0);
        boolean textBold = minemoticon$usesTextBold(source, style.bold);
        GlyphCache cache = GlyphCache.forSource(source, textBold);
        TextRunLayout layout = source.layoutTextRun(run, cache.getRenderSize(), textBold);
        int glyphCount = run.codePointCount(0, run.length());
        if (layout == null || layout.getGlyphCount() != glyphCount) {
            int visibleIndex = style.visibleStart;
            int rainbowIndex = style.rainbowStart;
            int gradientIndex = style.gradientStart;
            for (int i = 0; i < run.length();) {
                int codepoint = run.codePointAt(i);
                minemoticon$renderFontStackGlyph(
                    source,
                    codepoint,
                    shadow,
                    style.withIndexes(visibleIndex, rainbowIndex, gradientIndex));
                if (style.rainbow) {
                    rainbowIndex++;
                }
                if (style.gradient) {
                    gradientIndex++;
                }
                visibleIndex++;
                i += Character.charCount(codepoint);
            }
            return;
        }

        float baseX = this.posX;
        float displayHeight = source.getDisplayHeight();
        float verticalOffset = source.getVerticalOffset();
        float widthScale = source.getWidthScale();
        float layoutScale = displayHeight / cache.getRenderSize();
        float runAdvance = layout.getTotalAdvance() * layoutScale * widthScale;
        float lineY = this.posY + (8.0f - displayHeight) * 0.5f + verticalOffset;
        if (source.usesTextColor()) {
            lineY = Math.round(lineY);
        }

        boolean drawGlyphs = (!shadow || source.usesTextColor()) && !minemoticon$hiddenInShadowPass(style, shadow);
        Tessellator tessellator = Tessellator.instance;
        if (drawGlyphs) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(cache.getResourceLocation());
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (source.usesTextColor()) {
                minemoticon$applyRenderColor(
                    minemoticon$colorForStyle(style, style.rainbowStart, style.gradientStart, shadow));
            } else {
                GL11.glColor4f(1.0f, 1.0f, 1.0f, this.alpha);
            }
            tessellator.startDrawingQuads();
        }

        int glyphIndex = 0;
        int visibleIndex = style.visibleStart;
        int rainbowIndex = style.rainbowStart;
        int gradientIndex = style.gradientStart;
        for (int i = 0; i < run.length();) {
            int codepoint = run.codePointAt(i);
            int drawnGlyph = minemoticon$obfuscate(cache, codepoint, style);
            float[] uv = cache.getGlyphUV(drawnGlyph);
            float drawWidth = cache.getGlyphDrawWidth(drawnGlyph) * widthScale;
            float xOffset = cache.getGlyphOffsetX(drawnGlyph) * widthScale;
            float penX = layout.getPenPosition(glyphIndex) * layoutScale * widthScale;

            if (drawGlyphs && uv != null) {
                int color = minemoticon$colorForStyle(style, rainbowIndex, gradientIndex, shadow);
                if (source.usesTextColor()) {
                    minemoticon$setTessellatorColor(tessellator, color);
                }
                float x0 = baseX + penX + xOffset;
                float y0 = lineY + (style.wave ? minemoticon$waveOffset(visibleIndex) : 0.0f);
                float x1 = x0 + drawWidth;
                float y1 = y0 + displayHeight;
                // &v: swapped texture rows draw the glyph upside down in its box
                float vTop = style.dinnerbone ? uv[3] : uv[1];
                float vBottom = style.dinnerbone ? uv[1] : uv[3];

                tessellator.addVertexWithUV(x0, y0, 0.0, uv[0], vTop);
                tessellator.addVertexWithUV(x0, y1, 0.0, uv[0], vBottom);
                tessellator.addVertexWithUV(x1, y1, 0.0, uv[2], vBottom);
                tessellator.addVertexWithUV(x1, y0, 0.0, uv[2], vTop);

                if (source.usesTextColor() && minemoticon$usesSyntheticBold(source, style.bold)) {
                    float boldX0 = x0 + 1.0f;
                    float boldX1 = boldX0 + drawWidth;
                    tessellator.addVertexWithUV(boldX0, y0, 0.0, uv[0], vTop);
                    tessellator.addVertexWithUV(boldX0, y1, 0.0, uv[0], vBottom);
                    tessellator.addVertexWithUV(boldX1, y1, 0.0, uv[2], vBottom);
                    tessellator.addVertexWithUV(boldX1, y0, 0.0, uv[2], vTop);
                }
            }

            if (style.rainbow) {
                rainbowIndex++;
            }
            if (style.gradient) {
                gradientIndex++;
            }
            visibleIndex++;
            glyphIndex++;
            i += Character.charCount(codepoint);
        }
        if (drawGlyphs) {
            tessellator.draw();
        }

        if (minemoticon$usesSyntheticBold(source, style.bold) && runAdvance > 0.0f) {
            runAdvance += glyphCount;
        }
        minemoticon$applyRenderColor(minemoticon$colorForStyle(style, style.rainbowStart, style.gradientStart, shadow));
        minemoticon$drawDecorationLines(runAdvance, 0.0f, style);
        this.posX = baseX + runAdvance;
    }

    @Unique
    private boolean minemoticon$isTextRunSource(FontSource source) {
        return source != null && !(source instanceof MinecraftFontSource)
            && source.preserveTextLineMetrics()
            && source.usesTextColor();
    }

    @Unique
    private float minemoticon$measureTextSegmentExact(String text) {
        return minemoticon$measureTextSegmentExact(text, false, false, Integer.MAX_VALUE);
    }

    // Mirrors renderTextSegment in one pass, so bold carries across emojis and escapes like it does
    // when drawn. Measuring the pieces between emojis separately restarted them all unbolded.
    // stopAt: where in text to stop, as drawn with what follows it
    @Unique
    private float minemoticon$measureTextSegmentExact(String text, boolean matchEmoji, boolean initialBold,
        int stopAt) {
        FontStack stack = ClientEmojiHandler.getFontStack();
        if (stack == null) {
            return minemoticon$measureVanillaStringWidth(
                text.substring(0, Math.min(stopAt, text.length())),
                initialBold);
        }

        float width = 0.0f;
        var vanillaBuf = new StringBuilder();
        var customRunBuf = new StringBuilder();
        FontSource customRunSource = null;
        boolean bold = initialBold;
        int i = 0;
        while (i < text.length()) {
            if (i >= stopAt) {
                if (customRunBuf.length() > 0) {
                    width += minemoticon$measureCustomRunBefore(
                        customRunBuf.toString(),
                        customRunSource,
                        bold,
                        text,
                        i,
                        matchEmoji);
                    customRunBuf.setLength(0);
                }
                break;
            }
            int tokenEnd = TextStyleCompat.tokenEnd(text, i);
            boolean token = tokenEnd > i;
            EmojiRenderer.EscapeMatch escaped = !token && matchEmoji ? EmojiRenderer.matchEscapeAt(text, i) : null;
            EmojiRenderer.EmojiMatch emoji = !token && escaped == null && matchEmoji ? EmojiRenderer.matchAt(text, i)
                : null;
            if (token || escaped != null || emoji != null) {
                if (vanillaBuf.length() > 0) {
                    width += minemoticon$measureVanillaStringWidth(vanillaBuf.toString(), bold);
                    vanillaBuf.setLength(0);
                }
                if (customRunBuf.length() > 0) {
                    width += minemoticon$measureCustomTextRunExact(customRunBuf.toString(), customRunSource, bold);
                    customRunBuf.setLength(0);
                    customRunSource = null;
                }
            }
            if (token) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int format = minemoticon$getFormattingIndex(code);
                if (format >= 0 && format < 16) {
                    bold = false;
                } else if (format == 17) {
                    bold = true;
                } else if (format == 21) {
                    bold = false;
                }
                i = tokenEnd;
                continue;
            }
            if (escaped != null) {
                if (escaped.getNextIndex() > stopAt) {
                    break;
                }
                // Drawn as its literal text, see renderTextSegment
                width += minemoticon$measureTextSegmentExact(escaped.getLiteralText(), false, bold, Integer.MAX_VALUE);
                i = escaped.getNextIndex();
                continue;
            }
            if (emoji != null) {
                if (i + emoji.getLength() > stopAt) {
                    break;
                }
                width += EmojiRenderer.EMOJI_SIZE;
                i += emoji.getLength();
                continue;
            }

            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);

            FontSource source = stack.resolve(cp);
            boolean isVanilla = source == null || source instanceof MinecraftFontSource;
            boolean isTextRunSource = minemoticon$isTextRunSource(source);

            if (isVanilla) {
                if (customRunBuf.length() > 0) {
                    width += minemoticon$measureCustomTextRunExact(customRunBuf.toString(), customRunSource, bold);
                    customRunBuf.setLength(0);
                    customRunSource = null;
                }
                vanillaBuf.append(text, i, i + charCount);
            } else if (isTextRunSource) {
                if (vanillaBuf.length() > 0) {
                    width += minemoticon$measureVanillaStringWidth(vanillaBuf.toString(), bold);
                    vanillaBuf.setLength(0);
                }
                if (customRunSource != source && customRunBuf.length() > 0) {
                    width += minemoticon$measureCustomTextRunExact(customRunBuf.toString(), customRunSource, bold);
                    customRunBuf.setLength(0);
                }
                customRunSource = source;
                customRunBuf.append(text, i, i + charCount);
            } else {
                if (vanillaBuf.length() > 0) {
                    width += minemoticon$measureVanillaStringWidth(vanillaBuf.toString(), bold);
                    vanillaBuf.setLength(0);
                }
                if (customRunBuf.length() > 0) {
                    width += minemoticon$measureCustomTextRunExact(customRunBuf.toString(), customRunSource, bold);
                    customRunBuf.setLength(0);
                    customRunSource = null;
                }
                GlyphCache cache = GlyphCache.forSource(source);
                float advance = cache.getGlyphAdvance(cp) * source.getWidthScale();
                if (advance <= 0.0f) {
                    float glyphW = cache.getGlyphDrawWidth(cp) * source.getWidthScale();
                    advance = glyphW > 0.0f ? glyphW : 8.0f;
                }
                if (minemoticon$usesSyntheticBold(source, bold) && advance > 0.0f) {
                    advance += 1.0f;
                }
                width += advance;
            }
            i += charCount;
        }

        if (customRunBuf.length() > 0) {
            width += minemoticon$measureCustomTextRunExact(customRunBuf.toString(), customRunSource, bold);
        }
        if (vanillaBuf.length() > 0) {
            width += minemoticon$measureVanillaStringWidth(vanillaBuf.toString(), bold);
        }

        return width;
    }

    @Unique
    private float minemoticon$measureCustomTextRunExact(String text, FontSource source, boolean bold) {
        if (text == null || text.isEmpty() || source == null) {
            return 0.0f;
        }

        boolean textBold = minemoticon$usesTextBold(source, bold);
        GlyphCache cache = GlyphCache.forSource(source, textBold);
        TextRunLayout layout = source.layoutTextRun(text, cache.getRenderSize(), textBold);
        float width;
        if (layout != null && layout.getGlyphCount() == text.codePointCount(0, text.length())) {
            width = layout.getTotalAdvance() * source.getDisplayHeight()
                / cache.getRenderSize()
                * source.getWidthScale();
        } else {
            width = 0.0f;
            for (int i = 0; i < text.length();) {
                int codepoint = text.codePointAt(i);
                float advance = cache.getGlyphAdvance(codepoint) * source.getWidthScale();
                if (advance <= 0.0f) {
                    float drawWidth = cache.getGlyphDrawWidth(codepoint) * source.getWidthScale();
                    advance = drawWidth > 0.0f ? drawWidth : 8.0f;
                }
                width += advance;
                i += Character.charCount(codepoint);
            }
        }

        if (minemoticon$usesSyntheticBold(source, bold) && width > 0.0f) {
            width += text.codePointCount(0, text.length());
        }
        return width;
    }

    // The run's width up to where the text after it continues it. It's drawn as one layout with that
    // continuation, so kerning against the next glyph moves where the next glyph starts.
    @Unique
    private float minemoticon$measureCustomRunBefore(String run, FontSource source, boolean bold, String text, int from,
        boolean matchEmoji) {
        FontStack stack = ClientEmojiHandler.getFontStack();
        int end = from;
        while (stack != null && end < text.length()) {
            if (TextStyleCompat.tokenEnd(text, end) > end) break;
            if (matchEmoji
                && (EmojiRenderer.matchEscapeAt(text, end) != null || EmojiRenderer.matchAt(text, end) != null)) {
                break;
            }
            int codepoint = text.codePointAt(end);
            if (stack.resolve(codepoint) != source) break;
            end += Character.charCount(codepoint);
        }
        if (end == from) {
            return minemoticon$measureCustomTextRunExact(run, source, bold);
        }

        String drawnRun = run + text.substring(from, end);
        boolean textBold = minemoticon$usesTextBold(source, bold);
        GlyphCache cache = GlyphCache.forSource(source, textBold);
        TextRunLayout layout = source.layoutTextRun(drawnRun, cache.getRenderSize(), textBold);
        if (layout == null || layout.getGlyphCount() != drawnRun.codePointCount(0, drawnRun.length())) {
            return minemoticon$measureCustomTextRunExact(run, source, bold);
        }
        return layout.getPenPosition(run.codePointCount(0, run.length())) * source.getDisplayHeight()
            / cache.getRenderSize()
            * source.getWidthScale();
    }

    @Unique
    private boolean minemoticon$usesSyntheticBold(FontSource source, boolean bold) {
        return bold && !minemoticon$isTextRunSource(source);
    }

    @Unique
    private boolean minemoticon$usesTextBold(FontSource source, boolean bold) {
        return bold && minemoticon$isTextRunSource(source) && source.supportsTextBold();
    }

    @Unique
    private float minemoticon$measureCompatWidthExact(String text) {
        if (text == null) {
            return 0.0f;
        }

        text = TextStyleCompat.normalize(text);
        // parse() is cached, so text without emojis or escapes skips matching them per character
        return minemoticon$measureTextSegmentExact(text, EmojiRenderer.parse(text) != null, false, Integer.MAX_VALUE);
    }

    @Unique
    private int minemoticon$measureVanillaStringWidth(String text) {
        return minemoticon$measureVanillaStringWidth(text, false);
    }

    // bold: the style already active where text starts. Runs carry no codes, so it can't be read from them.
    @Unique
    private int minemoticon$measureVanillaStringWidth(String text, boolean bold) {
        if (text == null) {
            return 0;
        }

        int width = 0;

        for (int i = 0; i < text.length(); ++i) {
            char c0 = text.charAt(i);
            int charWidth = this.getCharWidth(c0);

            if (charWidth < 0 && i < text.length() - 1) {
                ++i;
                c0 = text.charAt(i);

                if (c0 != 'l' && c0 != 'L') {
                    if (c0 == 'r' || c0 == 'R') {
                        bold = false;
                    }
                } else {
                    bold = true;
                }

                charWidth = 0;
            }

            width += charWidth;

            if (bold && charWidth > 0) {
                ++width;
            }
        }

        return width;
    }

    @Unique
    private String minemoticon$trimStringToWidthCompat(String text, int maxWidth, boolean reverse) {
        if (text == null || maxWidth <= 0) {
            return "";
        }

        if (reverse) {
            int bestStart = text.length();
            for (int index = text.length(); index > 0;) {
                int prev = minemoticon$previousTrimBoundary(text, index);
                if (minemoticon$measureCompatWidthExact(text.substring(prev)) > maxWidth) {
                    break;
                }
                bestStart = prev;
                index = prev;
            }
            return text.substring(bestStart);
        }

        int bestEnd = 0;
        for (int index = 0; index < text.length();) {
            int next = minemoticon$nextTrimBoundary(text, index);
            if (minemoticon$measureCompatWidthExact(text.substring(0, next)) > maxWidth) {
                break;
            }
            bestEnd = next;
            index = next;
        }
        return text.substring(0, bestEnd);
    }

    @Unique
    private int minemoticon$sizeStringToWidthCompat(String text, int wrapWidth) {
        if (text == null || text.isEmpty() || wrapWidth <= 0) {
            return 0;
        }

        int bestEnd = 0;
        int lastSpace = -1;

        for (int index = 0; index < text.length();) {
            char c0 = text.charAt(index);
            if (c0 == '\n') {
                return index;
            }
            if (c0 == ' ') {
                lastSpace = index;
            }

            int next = minemoticon$nextTrimBoundary(text, index);
            if (minemoticon$measureCompatWidthExact(text.substring(0, next)) > wrapWidth) {
                break;
            }

            bestEnd = next;
            index = next;
        }

        if (bestEnd < text.length() && lastSpace >= 0) {
            return lastSpace;
        }

        return bestEnd;
    }

    @Unique
    private int minemoticon$nextTrimBoundary(String text, int index) {
        if (index >= text.length()) {
            return text.length();
        }
        int tokenEnd = TextStyleCompat.inputTokenEnd(text, index);
        if (tokenEnd > index) {
            return Math.min(tokenEnd, text.length());
        }
        EmojiRenderer.EmojiMatch emoji = EmojiRenderer.matchAt(text, index);
        if (emoji != null) {
            return Math.min(index + emoji.getLength(), text.length());
        }
        int codepoint = text.codePointAt(index);
        return index + Character.charCount(codepoint);
    }

    @Unique
    private int minemoticon$previousTrimBoundary(String text, int index) {
        if (index <= 0) {
            return 0;
        }

        int emojiScanStart = Math.max(0, index - minemoticon$MAX_EMOJI_TRIM_SEQUENCE_LENGTH);
        for (int i = emojiScanStart; i < index; i++) {
            EmojiRenderer.EmojiMatch emoji = EmojiRenderer.matchAt(text, i);
            if (emoji != null && i + emoji.getLength() == index) {
                return i;
            }
        }

        int prev = index - 1;
        if (prev > 0 && Character.isLowSurrogate(text.charAt(prev))
            && Character.isHighSurrogate(text.charAt(prev - 1))) {
            prev--;
        }
        int scan = Math.max(0, prev - TextStyleCompat.GRADIENT_LENGTH);
        for (int i = scan; i < prev; i++) {
            int tokenEnd = TextStyleCompat.inputTokenEnd(text, i);
            if (tokenEnd == index) {
                return i;
            }
        }
        if (prev > 0 && text.charAt(prev - 1) == TextStyleCompat.FORMAT) {
            return prev - 1;
        }
        return prev;
    }

    @Unique
    private boolean minemoticon$shouldUseCompatString(String text) {
        if (text == null || ClientEmojiHandler.getFontStack() == null) {
            return false;
        }
        text = TextStyleCompat.normalize(text);
        if (EmojiRenderer.parse(text) != null) {
            return true;
        }
        if (TextStyleCompat.hasExtendedStyle(text)) {
            return true;
        }

        FontStack stack = ClientEmojiHandler.getFontStack();
        int i = 0;
        while (i < text.length()) {
            int tokenEnd = TextStyleCompat.tokenEnd(text, i);
            if (tokenEnd > i) {
                i = tokenEnd;
                continue;
            }
            int cp = text.codePointAt(i);
            FontSource source = stack.resolve(cp);
            if (source != null && !(source instanceof MinecraftFontSource)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    @Unique
    private boolean minemoticon$isSplashFontRenderer() {
        return this.getClass()
            .getName()
            .endsWith("SplashFontRenderer");
    }

    @Unique
    private boolean minemoticon$shouldUseVanillaMainMenuSplash(int y) {
        return Minecraft.getMinecraft().currentScreen instanceof GuiMainMenu && y < 0;
    }

    // &k for custom fonts: vanilla only scrambles its own bitmap glyphs
    @Unique
    private int minemoticon$obfuscate(GlyphCache cache, int codepoint, MinemoticonRunStyle style) {
        if (!style.random || Character.isWhitespace(codepoint)) {
            return codepoint;
        }
        return cache.randomGlyphLike(codepoint, minemoticon$OBFUSCATION_RANDOM);
    }

    @Unique
    private void minemoticon$flushVanillaText(StringBuilder vanillaBuf, boolean shadow) {
        minemoticon$flushVanillaText(vanillaBuf, shadow, minemoticon$captureRunStyle());
    }

    @Unique
    private void minemoticon$flushVanillaText(StringBuilder vanillaBuf, boolean shadow, MinemoticonRunStyle style) {
        if (vanillaBuf.length() <= 0) {
            return;
        }
        if (style == null) {
            style = minemoticon$captureRunStyle();
        }

        String hiddenRun = vanillaBuf.toString();
        if (minemoticon$hiddenInShadowPass(style, shadow)) {
            // Only the pen moves, by what the run would have drawn
            this.posX += minemoticon$measureVanillaStringWidth(hiddenRun, this.boldStyle);
            return;
        }

        boolean swanSongBatchStarted = minemoticon$beginSwanSongTextBatch();
        minemoticon$bindVanillaFontTexture();
        try {
            String run = hiddenRun;
            if (!style.hasPerGlyphEffect()) {
                int color = minemoticon$colorForStyle(style, style.rainbowStart, style.gradientStart, shadow);
                minemoticon$applyRenderColor(color);
                minemoticon$applySwanSongTextColor(color);
                minemoticon$renderVanillaRun(run, shadow, style);
                return;
            }

            int visibleIndex = style.visibleStart;
            int rainbowIndex = style.rainbowStart;
            int gradientIndex = style.gradientStart;
            for (int i = 0; i < run.length();) {
                int codepoint = run.codePointAt(i);
                int charCount = Character.charCount(codepoint);
                int color = minemoticon$colorForStyle(style, rainbowIndex, gradientIndex, shadow);
                float yOffset = style.wave ? minemoticon$waveOffset(visibleIndex) : 0.0f;
                minemoticon$applyRenderColor(color);
                minemoticon$applySwanSongTextColor(color);
                this.posY += yOffset;
                try {
                    minemoticon$renderVanillaRun(run.substring(i, i + charCount), shadow, style);
                } finally {
                    this.posY -= yOffset;
                }
                if (style.rainbow) {
                    rainbowIndex++;
                }
                if (style.gradient) {
                    gradientIndex++;
                }
                visibleIndex++;
                i += charCount;
            }
        } finally {
            minemoticon$endSwanSongTextBatch(swanSongBatchStarted);
            vanillaBuf.setLength(0);
        }
    }

    @Unique
    private void minemoticon$renderEmoji(EmojiRenderer.EmojiMatch emoji, boolean shadow) {
        MinemoticonRunStyle style = minemoticon$captureRunStyle();
        float yOffset = style.wave ? minemoticon$waveOffset(style.visibleStart) : 0.0f;
        if (!shadow) {
            minemoticon$emojiBatch.add(
                emoji.getEmoji(),
                this.posX,
                this.posY + minemoticon$INLINE_EMOJI_Y_OFFSET + yOffset,
                style.dinnerbone);
        }
        this.posX += EmojiRenderer.EMOJI_SIZE;
        minemoticon$advanceVisibleStyle();
    }

    @Unique
    private void minemoticon$resetExtendedStyles() {
        this.minemoticon$rainbowStyle = false;
        this.minemoticon$waveStyle = false;
        this.minemoticon$gradientStyle = false;
        this.minemoticon$extraShadowStyle = false;
        this.minemoticon$shadowColorOverride = -1;
        this.minemoticon$dinnerboneStyle = false;
        this.minemoticon$gradientCharIndex = 0;
        this.minemoticon$gradientTotalChars = 0;
        this.minemoticon$rainbowCharIndex = 0;
        this.minemoticon$visibleCharIndex = 0;
    }

    @Unique
    private MinemoticonRunStyle minemoticon$captureRunStyle() {
        return new MinemoticonRunStyle(
            this.minemoticon$currentRenderColor,
            this.minemoticon$waveStyle,
            this.minemoticon$rainbowStyle,
            this.minemoticon$gradientStyle,
            this.boldStyle,
            this.underlineStyle,
            this.strikethroughStyle,
            this.minemoticon$visibleCharIndex,
            this.minemoticon$rainbowCharIndex,
            this.minemoticon$gradientCharIndex,
            this.minemoticon$gradientTotalChars,
            this.minemoticon$gradientStartRgb,
            this.minemoticon$gradientEndRgb,
            this.minemoticon$extraShadowStyle,
            this.minemoticon$shadowColorOverride,
            this.minemoticon$dinnerboneStyle,
            this.randomStyle);
    }

    @Unique
    private void minemoticon$advanceVisibleStyle() {
        if (this.minemoticon$rainbowStyle) {
            this.minemoticon$rainbowCharIndex++;
        }
        if (this.minemoticon$gradientStyle) {
            this.minemoticon$gradientCharIndex++;
        }
        this.minemoticon$visibleCharIndex++;
    }

    @Unique
    private void minemoticon$applyFormattingToken(String text, int tokenStart, int tokenEnd, boolean shadow) {
        char code = Character.toLowerCase(text.charAt(tokenStart + 1));
        if (code == 'x') {
            int rgb = TextStyleCompat.parseSectionXAt(text, tokenStart);
            if (rgb >= 0) {
                this.minemoticon$rainbowStyle = false;
                this.minemoticon$gradientStyle = false;
                minemoticon$applyRgbColor(rgb, shadow);
            }
            return;
        }

        if (code == 'g') {
            int color1 = TextStyleCompat.parseSectionXAt(text, tokenStart + 2);
            int color2 = TextStyleCompat.parseSectionXAt(text, tokenStart + 2 + TextStyleCompat.SECTION_X_LENGTH);
            if (color1 >= 0 && color2 >= 0) {
                this.minemoticon$rainbowStyle = false;
                this.minemoticon$gradientStyle = true;
                this.minemoticon$gradientStartRgb = color1;
                this.minemoticon$gradientEndRgb = color2;
                this.minemoticon$gradientCharIndex = 0;
                this.minemoticon$gradientTotalChars = minemoticon$countVisibleCharsUntilColor(text, tokenEnd);
                minemoticon$applyRgbColor(color1, shadow);
            }
            return;
        }

        if (code == 'q') {
            this.minemoticon$rainbowStyle = true;
            this.minemoticon$gradientStyle = false;
            this.minemoticon$rainbowCharIndex = 0;
            return;
        }

        if (code == 'z') {
            this.minemoticon$waveStyle = !this.minemoticon$waveStyle;
            return;
        }

        if (code == 'u') {
            // Alone it toggles; with a &x payload it turns the shadow on in that color
            int rgb = TextStyleCompat.parseSectionXAt(text, tokenStart + 2);
            if (rgb >= 0) {
                this.minemoticon$extraShadowStyle = true;
                this.minemoticon$shadowColorOverride = rgb;
            } else {
                this.minemoticon$extraShadowStyle = !this.minemoticon$extraShadowStyle;
                if (!this.minemoticon$extraShadowStyle) {
                    this.minemoticon$shadowColorOverride = -1;
                }
            }
            return;
        }

        if (code == 'v') {
            this.minemoticon$dinnerboneStyle = !this.minemoticon$dinnerboneStyle;
            return;
        }

        minemoticon$applyFormattingCode(code, shadow);
    }

    @Unique
    private void minemoticon$applyRgbColor(int rgb, boolean shadow) {
        if (shadow) {
            rgb = minemoticon$shadowRgb(rgb);
        }
        this.minemoticon$currentRenderColor = this.minemoticon$baseRenderColor & 0xFF000000 | rgb & 0x00FFFFFF;
        minemoticon$applyRenderColor(this.minemoticon$currentRenderColor);
    }

    @Unique
    private int minemoticon$countVisibleCharsUntilColor(String text, int start) {
        int count = 0;
        for (int i = start; i < text.length();) {
            int tokenEnd = TextStyleCompat.tokenEnd(text, i);
            if (tokenEnd > i) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int format = minemoticon$getFormattingIndex(code);
                if (code == 'x' || code == 'g' || code == 'q' || format == 21 || format >= 0 && format < 16) {
                    break;
                }
                i = tokenEnd;
                continue;
            }

            EmojiRenderer.EmojiMatch emoji = EmojiRenderer.matchAt(text, i);
            if (emoji != null) {
                count++;
                i += emoji.getLength();
                continue;
            }

            count++;
            i += Character.charCount(text.codePointAt(i));
        }
        return Math.max(count, 1);
    }

    @Unique
    private int minemoticon$colorForStyle(MinemoticonRunStyle style, int rainbowIndex, int gradientIndex,
        boolean shadow) {
        if (style == null) {
            return this.minemoticon$currentRenderColor;
        }
        int color = minemoticon$effectColor(style, rainbowIndex, gradientIndex, shadow);
        if (!shadow) {
            return color;
        }
        return style.shadowColor >= 0 ? color & 0xFF000000 | style.shadowColor : color;
    }

    // &u: a shadow pass that only exists for &u spans draws nothing else
    @Unique
    private boolean minemoticon$hiddenInShadowPass(MinemoticonRunStyle style, boolean shadow) {
        return shadow && this.minemoticon$extraShadowPassOnly && !style.extraShadow;
    }

    @Unique
    private int minemoticon$effectColor(MinemoticonRunStyle style, int rainbowIndex, int gradientIndex,
        boolean shadow) {
        if (!style.rainbow && !style.gradient) {
            return style.baseColor;
        }

        int rgb;
        if (style.rainbow) {
            rgb = minemoticon$RAINBOW_LUT[Math.floorMod(rainbowIndex, minemoticon$RAINBOW_LUT_SIZE)];
        } else if (style.gradientTotal > 1) {
            float t = Math.min((float) gradientIndex / (style.gradientTotal - 1), 1.0f);
            rgb = minemoticon$lerpRgb(style.gradientStartRgb, style.gradientEndRgb, t);
        } else {
            rgb = style.gradientStartRgb;
        }

        if (shadow) {
            rgb = minemoticon$shadowRgb(rgb);
        }
        return style.baseColor & 0xFF000000 | rgb & 0x00FFFFFF;
    }

    @Unique
    private float minemoticon$waveOffset(int visibleIndex) {
        float time = (float) ((this.minemoticon$styleTimeNanos & 0xFFFFFFFFFFFFL) * minemoticon$WAVE_TIME_SCALE);
        return (float) Math.sin(visibleIndex * minemoticon$WAVE_FREQUENCY + time) * minemoticon$WAVE_AMPLITUDE;
    }

    @Unique
    private int minemoticon$shadowRgb(int rgb) {
        return (rgb & 0xFCFCFC) >> 2;
    }

    @Unique
    private int minemoticon$lerpRgb(int start, int end, float t) {
        int r = (int) ((start >> 16 & 255) * (1.0f - t) + (end >> 16 & 255) * t);
        int g = (int) ((start >> 8 & 255) * (1.0f - t) + (end >> 8 & 255) * t);
        int b = (int) ((start & 255) * (1.0f - t) + (end & 255) * t);
        return r << 16 | g << 8 | b;
    }

    @Unique
    private void minemoticon$setTessellatorColor(Tessellator tessellator, int argb) {
        float r = (float) (argb >> 16 & 255) / 255.0f;
        float g = (float) (argb >> 8 & 255) / 255.0f;
        float b = (float) (argb & 255) / 255.0f;
        tessellator.setColorRGBA_F(r, g, b, this.alpha);
    }

    @Unique
    private int minemoticon$getBaseRenderColor() {
        int a = Math.round(this.alpha * 255.0f) & 255;
        int r = Math.round(this.red * 255.0f) & 255;
        int g = Math.round(this.blue * 255.0f) & 255;
        int b = Math.round(this.green * 255.0f) & 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    @Unique
    private void minemoticon$applyRenderColor(int argb) {
        float r = (float) (argb >> 16 & 255) / 255.0f;
        float g = (float) (argb >> 8 & 255) / 255.0f;
        float b = (float) (argb & 255) / 255.0f;
        GL11.glColor4f(r, g, b, this.alpha);
    }

    @Unique
    private static int[] minemoticon$buildRainbowLut() {
        int[] colors = new int[minemoticon$RAINBOW_LUT_SIZE];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = minemoticon$hsvToRgb(i * 15.0f, 1.0f, 1.0f);
        }
        return colors;
    }

    @Unique
    private static int minemoticon$hsvToRgb(float hue, float saturation, float value) {
        int h = (int) (hue / 60.0f) % 6;
        float f = hue / 60.0f - h;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - f * saturation);
        float t = value * (1.0f - (1.0f - f) * saturation);
        float r;
        float g;
        float b;
        switch (h) {
            case 0:
                r = value;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = value;
                b = p;
                break;
            case 2:
                r = p;
                g = value;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = value;
                break;
            case 4:
                r = t;
                g = p;
                b = value;
                break;
            default:
                r = value;
                g = p;
                b = q;
                break;
        }
        return (int) (r * 255.0f) << 16 | (int) (g * 255.0f) << 8 | (int) (b * 255.0f);
    }

    @Unique
    private boolean minemoticon$beginSwanSongTextBatch() {
        if (!minemoticon$resolveSwanSongTextBridge(this.getClass())) {
            return false;
        }

        try {
            return (Boolean) minemoticon$swanSongBeginTessellating.invoke(this);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Unique
    private void minemoticon$endSwanSongTextBatch(boolean thisStart) {
        if (!thisStart || minemoticon$swanSongDraw == null) {
            return;
        }

        try {
            minemoticon$swanSongDraw.invoke(this, true);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // SwanSong is optional. Falling back to vanilla behavior is better than breaking text rendering.
        }
    }

    @Unique
    private void minemoticon$applySwanSongTextColor(int argb) {
        if (minemoticon$swanSongSetColor == null) {
            return;
        }

        float r = (float) (argb >> 16 & 255) / 255.0f;
        float g = (float) (argb >> 8 & 255) / 255.0f;
        float b = (float) (argb & 255) / 255.0f;

        try {
            minemoticon$swanSongSetColor.invoke(this, r, g, b, this.alpha);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // SwanSong is optional. GL state was already updated by minemoticon$applyRenderColor.
        }
    }

    @Unique
    private static boolean minemoticon$resolveSwanSongTextBridge(Class<?> rendererClass) {
        if (minemoticon$swanSongTextBridgeChecked) {
            return minemoticon$swanSongBeginTessellating != null && minemoticon$swanSongDraw != null
                && minemoticon$swanSongSetColor != null;
        }

        minemoticon$swanSongTextBridgeChecked = true;
        try {
            minemoticon$swanSongBeginTessellating = minemoticon$getRendererMethod(
                rendererClass,
                "swan$beginTessellating");
            minemoticon$swanSongDraw = minemoticon$getRendererMethod(rendererClass, "swan$draw", Boolean.TYPE);
            minemoticon$swanSongSetColor = minemoticon$getRendererMethod(
                rendererClass,
                "setColor",
                Float.TYPE,
                Float.TYPE,
                Float.TYPE,
                Float.TYPE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            minemoticon$swanSongBeginTessellating = null;
            minemoticon$swanSongDraw = null;
            minemoticon$swanSongSetColor = null;
        }

        return minemoticon$swanSongBeginTessellating != null && minemoticon$swanSongDraw != null
            && minemoticon$swanSongSetColor != null;
    }

    @Unique
    private static Method minemoticon$getRendererMethod(Class<?> rendererClass, String name, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Class<?> current = rendererClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Unique
    private int minemoticon$getFormattingIndex(char formatChar) {
        return "0123456789abcdefklmnor".indexOf(Character.toLowerCase(formatChar));
    }

    @Unique
    private void minemoticon$applyFormattingCode(char formatChar, boolean shadow) {
        int format = minemoticon$getFormattingIndex(formatChar);

        if (format < 16) {
            this.randomStyle = false;
            this.boldStyle = false;
            this.strikethroughStyle = false;
            this.underlineStyle = false;
            this.italicStyle = false;
            this.minemoticon$rainbowStyle = false;
            this.minemoticon$gradientStyle = false;

            if (format < 0 || format > 15) {
                format = 15;
            }

            if (shadow) {
                format += 16;
            }

            this.minemoticon$currentRenderColor = this.colorCode[format];
            minemoticon$applyRenderColor(this.minemoticon$currentRenderColor);
        } else if (format == 16) {
            this.randomStyle = true;
        } else if (format == 17) {
            this.boldStyle = true;
        } else if (format == 18) {
            this.strikethroughStyle = true;
        } else if (format == 19) {
            this.underlineStyle = true;
        } else if (format == 20) {
            this.italicStyle = true;
        } else if (format == 21) {
            this.randomStyle = false;
            this.boldStyle = false;
            this.strikethroughStyle = false;
            this.underlineStyle = false;
            this.italicStyle = false;
            this.minemoticon$rainbowStyle = false;
            this.minemoticon$waveStyle = false;
            this.minemoticon$gradientStyle = false;
            this.minemoticon$extraShadowStyle = false;
            this.minemoticon$shadowColorOverride = -1;
            this.minemoticon$dinnerboneStyle = false;
            this.minemoticon$rainbowCharIndex = 0;
            this.minemoticon$gradientCharIndex = 0;
            this.minemoticon$currentRenderColor = this.minemoticon$baseRenderColor;
            minemoticon$applyRenderColor(this.minemoticon$currentRenderColor);
        }
    }

    // &v flips the run upside down in place, like Angelica's dinnerbone quads. Underline and
    // strikethrough stay put, so they're drawn separately, unflipped.
    @Unique
    private void minemoticon$renderVanillaRun(String run, boolean shadow, MinemoticonRunStyle style) {
        if (!style.dinnerbone) {
            this.renderStringAtPos(run, shadow);
            return;
        }

        boolean underline = this.underlineStyle;
        boolean strikethrough = this.strikethroughStyle;
        float startX = this.posX;
        float middle = this.posY + (this.FONT_HEIGHT - 1) / 2.0f;
        this.underlineStyle = false;
        this.strikethroughStyle = false;
        // Mirroring reverses the quads' winding, so culling would drop every glyph
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0f, middle, 0.0f);
            GL11.glScalef(1.0f, -1.0f, 1.0f);
            GL11.glTranslatef(0.0f, -middle, 0.0f);
            this.renderStringAtPos(run, shadow);
        } finally {
            GL11.glPopMatrix();
            if (cullWasEnabled) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }
            this.underlineStyle = underline;
            this.strikethroughStyle = strikethrough;
        }

        float endX = this.posX;
        this.posX = startX;
        minemoticon$drawDecorationLines(endX - startX, 0.0f, style);
        this.posX = endX;
    }

    @Unique
    private void minemoticon$drawDecorationLines(float width, float yOffset, MinemoticonRunStyle style) {
        if (style == null) {
            style = minemoticon$captureRunStyle();
        }
        if (!style.strikethrough && !style.underline) {
            return;
        }

        Tessellator tessellator = Tessellator.instance;
        float lineY = this.posY + yOffset;

        if (style.strikethrough) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            tessellator.startDrawingQuads();
            tessellator.addVertex(this.posX, lineY + (float) (this.FONT_HEIGHT / 2), 0.0D);
            tessellator.addVertex(this.posX + width, lineY + (float) (this.FONT_HEIGHT / 2), 0.0D);
            tessellator.addVertex(this.posX + width, lineY + (float) (this.FONT_HEIGHT / 2) - 1.0F, 0.0D);
            tessellator.addVertex(this.posX, lineY + (float) (this.FONT_HEIGHT / 2) - 1.0F, 0.0D);
            tessellator.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        if (style.underline) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            tessellator.startDrawingQuads();
            tessellator.addVertex(this.posX - 1.0F, lineY + (float) this.FONT_HEIGHT, 0.0D);
            tessellator.addVertex(this.posX + width, lineY + (float) this.FONT_HEIGHT, 0.0D);
            tessellator.addVertex(this.posX + width, lineY + (float) this.FONT_HEIGHT - 1.0F, 0.0D);
            tessellator.addVertex(this.posX - 1.0F, lineY + (float) this.FONT_HEIGHT - 1.0F, 0.0D);
            tessellator.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        minemoticon$applyRenderColor(style.baseColor);
    }

    @Unique
    private void minemoticon$bindVanillaFontTexture() {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(this.locationFontTexture);
    }

    private static final class MinemoticonRunStyle {

        private final int baseColor;
        private final boolean wave;
        private final boolean rainbow;
        private final boolean gradient;
        private final boolean bold;
        private final boolean underline;
        private final boolean strikethrough;
        private final int visibleStart;
        private final int rainbowStart;
        private final int gradientStart;
        private final int gradientTotal;
        private final int gradientStartRgb;
        private final int gradientEndRgb;
        private final boolean extraShadow;
        private final int shadowColor;
        private final boolean dinnerbone;
        private final boolean random;

        private MinemoticonRunStyle(int baseColor, boolean wave, boolean rainbow, boolean gradient, boolean bold,
            boolean underline, boolean strikethrough, int visibleStart, int rainbowStart, int gradientStart,
            int gradientTotal, int gradientStartRgb, int gradientEndRgb, boolean extraShadow, int shadowColor,
            boolean dinnerbone, boolean random) {
            this.baseColor = baseColor;
            this.wave = wave;
            this.rainbow = rainbow;
            this.gradient = gradient;
            this.bold = bold;
            this.underline = underline;
            this.strikethrough = strikethrough;
            this.visibleStart = visibleStart;
            this.rainbowStart = rainbowStart;
            this.gradientStart = gradientStart;
            this.gradientTotal = gradientTotal;
            this.gradientStartRgb = gradientStartRgb;
            this.gradientEndRgb = gradientEndRgb;
            this.extraShadow = extraShadow;
            this.shadowColor = shadowColor;
            this.dinnerbone = dinnerbone;
            this.random = random;
        }

        private boolean hasPerGlyphEffect() {
            return wave || rainbow || gradient;
        }

        private MinemoticonRunStyle withIndexes(int visibleStart, int rainbowStart, int gradientStart) {
            return new MinemoticonRunStyle(
                baseColor,
                wave,
                rainbow,
                gradient,
                bold,
                underline,
                strikethrough,
                visibleStart,
                rainbowStart,
                gradientStart,
                gradientTotal,
                gradientStartRgb,
                gradientEndRgb,
                extraShadow,
                shadowColor,
                dinnerbone,
                random);
        }
    }

    // --- Compat helpers ---

    @Unique
    private static boolean minemoticon$hasShadowCode(String text) {
        String normalized = TextStyleCompat.normalize(text);
        for (int i = normalized.indexOf(TextStyleCompat.FORMAT); i >= 0
            && i + 1 < normalized.length(); i = normalized.indexOf(TextStyleCompat.FORMAT, i + 1)) {
            if (Character.toLowerCase(normalized.charAt(i + 1)) == 'u') return true;
        }
        return false;
    }

    @Unique
    private int minemoticon$drawStringVanillaCompat(String text, int x, int y, int color, boolean dropShadow) {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        this.resetStyles();

        if (dropShadow) {
            int shadowWidth = minemoticon$renderStringVanillaCompat(text, x + 1, y + 1, color, true);
            int normalWidth = minemoticon$renderStringVanillaCompat(text, x, y, color, false);
            return Math.max(shadowWidth, normalWidth);
        }

        // &u adds a shadow to its spans of unshadowed text, like Angelica's per-glyph shadow
        if (minemoticon$hasShadowCode(text)) {
            this.minemoticon$extraShadowPassOnly = true;
            try {
                minemoticon$renderStringVanillaCompat(text, x + 1, y + 1, color, true);
            } finally {
                this.minemoticon$extraShadowPassOnly = false;
            }
        }
        return minemoticon$renderStringVanillaCompat(text, x, y, color, false);
    }

    @Override
    public int minemoticon$drawStringCompatDirect(String text, int x, int y, int color, boolean dropShadow) {
        return minemoticon$drawStringVanillaCompat(text, x, y, color, dropShadow);
    }

    @Override
    public int minemoticon$getStringWidthCompatDirect(String text) {
        return (int) Math.ceil(minemoticon$measureCompatWidthExact(text));
    }

    @Override
    public boolean minemoticon$usesCompatRendering(String text) {
        return minemoticon$shouldUseCompatString(text);
    }

    @Override
    public float minemoticon$getCaretOffsetCompatDirect(String text, int index) {
        if (text == null || index <= 0) {
            return 0.0f;
        }
        // index counts the typed & codes, which normalizing turns into longer section sign tokens
        int stopAt = TextStyleCompat.normalize(text.substring(0, Math.min(index, text.length())))
            .length();
        String normalized = TextStyleCompat.normalize(text);
        return minemoticon$measureTextSegmentExact(normalized, EmojiRenderer.parse(normalized) != null, false, stopAt);
    }

    @Unique
    private int minemoticon$renderStringVanillaCompat(String text, int x, int y, int color, boolean dropShadow) {
        if (text == null) {
            return 0;
        }

        text = TextStyleCompat.normalize(text);
        if (this.bidiFlag) {
            text = this.bidiReorder(text);
        }

        if ((color & 0xFC000000) == 0) {
            color |= 0xFF000000;
        }

        if (dropShadow) {
            color = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
        }

        this.red = (float) (color >> 16 & 255) / 255.0F;
        this.blue = (float) (color >> 8 & 255) / 255.0F;
        this.green = (float) (color & 255) / 255.0F;
        this.alpha = (float) (color >> 24 & 255) / 255.0F;
        GL11.glColor4f(this.red, this.blue, this.green, this.alpha);
        this.posX = x;
        this.posY = y;
        if (minemoticon$canRenderCompat()) {
            minemoticon$renderStringAtPosCompat(text, dropShadow);
        } else {
            this.renderStringAtPos(text, dropShadow);
        }
        return (int) this.posX;
    }
}
