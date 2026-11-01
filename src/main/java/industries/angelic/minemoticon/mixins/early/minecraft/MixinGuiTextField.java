package industries.angelic.minemoticon.mixins.early.minecraft;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import industries.angelic.minemoticon.ClientEmojiHandler;
import industries.angelic.minemoticon.Minemoticon;
import industries.angelic.minemoticon.network.EmoteClientHandler;
import industries.angelic.minemoticon.render.EmojiRenderer;
import industries.angelic.minemoticon.render.FontRendererEmojiCompat;
import industries.angelic.minemoticon.text.EmojiPua;
import industries.angelic.minemoticon.text.TextStyleCompat;

@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {

    private static final int MINEMOTICON$MAX_EMOJI_SEQUENCE_LENGTH = 32;

    @Shadow
    private String text;

    @Shadow
    private int cursorPosition;

    @Shadow
    private int selectionEnd;

    @Shadow
    private int lineScrollOffset;

    @Shadow
    private int maxStringLength;

    @Shadow
    public abstract boolean isFocused();

    @Shadow
    public abstract void setText(String text);

    @Shadow
    public abstract void setCursorPosition(int position);

    @Shadow
    public abstract int getWidth();

    @Unique
    private int minemoticon$drawTextBoxStringCall;

    @Unique
    private boolean minemoticon$drawTextBoxRenderedWholePreview;

    @Unique
    private static int minemoticon$textFieldDebugLogs;

    @Unique
    private boolean minemoticon$arrowKeyTyped;

    @Inject(method = "textboxKeyTyped", at = @At("HEAD"))
    private void minemoticon$beginKeyTyped(char typedChar, int keyCode, CallbackInfoReturnable<Boolean> cir) {
        minemoticon$arrowKeyTyped = keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT;
    }

    @Inject(method = "textboxKeyTyped", at = @At("RETURN"))
    private void minemoticon$endKeyTyped(char typedChar, int keyCode, CallbackInfoReturnable<Boolean> cir) {
        minemoticon$arrowKeyTyped = false;
    }

    // Expand cursor movement to skip over full Unicode emoji sequences
    @ModifyVariable(method = "moveCursorBy", at = @At("HEAD"), argsOnly = true)
    private int minemoticon$expandEmojiCursorMove(int amount) {
        if (text == null || text.isEmpty()) return amount;
        if (minemoticon$arrowKeyTyped && (amount == -1 || amount == 1)) {
            return minemoticon$arrowStep(selectionEnd, amount) - selectionEnd;
        }

        if (amount == -1) {
            int start = findEmojiStart(text, selectionEnd);
            if (start >= 0) {
                return start - selectionEnd;
            }
        } else if (amount == 1) {
            int end = findEmojiEnd(text, selectionEnd);
            if (end > selectionEnd) {
                return end - selectionEnd;
            }
        }

        return amount;
    }

    // Expand single-char delete to cover full Unicode emoji sequences
    @ModifyVariable(method = "deleteFromCursor", at = @At("HEAD"), argsOnly = true)
    private int minemoticon$expandEmojiDelete(int amount) {
        if (text == null || text.isEmpty()) return amount;

        if (amount == -1) {
            // Backspace: check if chars before cursor form a Unicode emoji
            int start = findEmojiStart(text, cursorPosition);
            if (start >= 0) {
                return start - cursorPosition; // negative offset to delete whole emoji
            }
        } else if (amount == 1) {
            // Delete key: check if chars after cursor form a Unicode emoji
            int end = findEmojiEnd(text, cursorPosition);
            if (end > cursorPosition) {
                return end - cursorPosition;
            }
        }

        return amount;
    }

    @ModifyVariable(method = "setCursorPosition", at = @At("HEAD"), argsOnly = true)
    private int minemoticon$snapCursorPositionToEmojiBoundary(int position) {
        return snapToEmojiBoundary(position, cursorPosition);
    }

    @ModifyVariable(method = "setSelectionPos", at = @At("HEAD"), argsOnly = true)
    private int minemoticon$snapSelectionPositionToEmojiBoundary(int position) {
        // Shift+arrow selects through here
        int delta = position - selectionEnd;
        if (minemoticon$arrowKeyTyped && text != null && (delta == -1 || delta == 1)) {
            return minemoticon$arrowStep(selectionEnd, delta);
        }
        return snapToEmojiBoundary(position, selectionEnd);
    }

    // One arrow press: past the format codes next to the cursor, then one character or emoji. Format
    // codes draw nothing, so a stop on each side of one looked like a press that did nothing.
    @Unique
    private int minemoticon$arrowStep(int from, int direction) {
        if (direction > 0) {
            int pos = from;
            while (pos < text.length()) {
                int tokenEnd = TextStyleCompat.inputTokenEnd(text, pos);
                if (tokenEnd <= pos) break;
                pos = tokenEnd;
            }
            if (pos >= text.length()) return text.length();
            int end = findEmojiEnd(text, pos);
            return end > pos ? end : pos + Character.charCount(text.codePointAt(pos));
        }

        int pos = minemoticon$formatCodesStart(text, Math.min(from, text.length()));
        if (pos <= 0) return 0;
        int start = findEmojiStart(text, pos);
        return start >= 0 ? start : pos - 1;
    }

    // Start of the format codes that end right at pos, or pos. Scans from the start: tokens only
    // read forwards, so a backwards scan could mistake the tail of one for another.
    @Unique
    private static int minemoticon$formatCodesStart(String text, int pos) {
        int runStart = pos;
        int i = 0;
        while (i < pos) {
            int tokenEnd = TextStyleCompat.inputTokenEnd(text, i);
            if (tokenEnd > i && tokenEnd <= pos) {
                if (runStart == pos) runStart = i;
                i = tokenEnd;
            } else {
                runStart = pos;
                i++;
            }
        }
        return runStart;
    }

    @Inject(method = "writeText", at = @At("TAIL"))
    private void minemoticon$substituteCompletedAlias(String insertedText, CallbackInfo ci) {
        if (!isFocused()) {
            return;
        }

        EmoteClientHandler.TextReplacement replacement = EmoteClientHandler
            .substituteCompletedAlias(text, cursorPosition);
        if (replacement == null) {
            return;
        }

        setText(replacement.text);
        setCursorPosition(replacement.cursorPosition);
    }

    @Inject(method = "drawTextBox", at = @At("HEAD"))
    private void minemoticon$resetTextBoxDrawState(CallbackInfo ci) {
        minemoticon$drawTextBoxStringCall = 0;
        minemoticon$drawTextBoxRenderedWholePreview = false;
    }

    @Redirect(
        method = "drawTextBox",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I"))
    private int minemoticon$drawTextBoxSegment(FontRenderer font, String segment, int x, int y, int color) {
        // The end caret. Mid-text a "_" is the text after the cursor, already in the whole preview.
        if ("_".equals(segment) && !minemoticon$caretIsBar()) {
            return font.drawStringWithShadow(segment, x, y, color);
        }

        String visible = minemoticon$getVisibleText(font);
        if (!minemoticon$needsWholePreviewDraw(font, visible)) {
            return font.drawStringWithShadow(segment, x, y, color);
        }

        boolean previousColonAliasMatching = EmojiRenderer
            .pushColonAliasMatching(minemoticon$shouldMatchColonAliases(visible));
        try {
            int call = minemoticon$drawTextBoxStringCall++;
            if (call == 0) {
                String prefix = TextStyleCompat.activeFormatPrefix(text, lineScrollOffset);
                if (minemoticon$canDrawCompat(font)) {
                    // The caret is placed by measuring, so draw with the renderer that measures
                    ((FontRendererEmojiCompat) font)
                        .minemoticon$drawStringCompatDirect(prefix + visible, x, y, color, true);
                } else {
                    font.drawStringWithShadow(prefix + visible, x, y, color);
                }
                minemoticon$drawTextBoxRenderedWholePreview = true;
                minemoticon$logTextFieldCompat(font, visible, segment, x);
                return minemoticon$textBoxShadowEnd(font, visible, x);
            }

            if (minemoticon$drawTextBoxRenderedWholePreview) {
                return minemoticon$textBoxShadowEnd(font, visible, x);
            }
            return font.drawStringWithShadow(segment, x, y, color);
        } finally {
            EmojiRenderer.popColonAliasMatching(previousColonAliasMatching);
        }
    }

    @Unique
    private int minemoticon$textBoxShadowEnd(FontRenderer font, String visible, int x) {
        String prefix = TextStyleCompat.activeFormatPrefix(text, lineScrollOffset);
        String beforeCursor = minemoticon$getVisibleTextBeforeCursor(visible);
        if (!(font instanceof FontRendererEmojiCompat compat)) {
            return x + font.getStringWidth(prefix + beforeCursor) + 1;
        }
        // The bar is drawn one left of this, the end caret underscore at it
        if (!minemoticon$caretIsBar()) {
            return x + compat.minemoticon$getStringWidthCompatDirect(prefix + beforeCursor) + 1;
        }
        // Custom font glyphs sit at fractional x with barely a gap between them. Flooring centers the
        // bar on where the next one starts, ceil pushed it into the glyph.
        float offset = compat
            .minemoticon$getCaretOffsetCompatDirect(prefix + visible, prefix.length() + beforeCursor.length());
        return x + (int) Math.floor(offset + 0.001f) + 1;
    }

    // Vanilla's choice between the 1px bar mid-text and the underscore at the end
    @Unique
    private boolean minemoticon$caretIsBar() {
        return cursorPosition < text.length() || text.length() >= maxStringLength;
    }

    @Unique
    private String minemoticon$getVisibleTextBeforeCursor(String visible) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        int start = Math.max(0, Math.min(lineScrollOffset, text.length()));
        int end = Math.max(start, Math.min(cursorPosition, text.length()));
        String beforeCursor = text.substring(start, end);
        if (visible == null) {
            return beforeCursor;
        }
        if (beforeCursor.length() > visible.length()) {
            return visible;
        }
        if (!visible.startsWith(beforeCursor)) {
            return visible.substring(0, Math.min(beforeCursor.length(), visible.length()));
        }
        return beforeCursor;
    }

    @Unique
    private void minemoticon$logTextFieldCompat(FontRenderer font, String visible, String segment, int x) {
        if (minemoticon$textFieldDebugLogs >= 16) {
            return;
        }
        minemoticon$textFieldDebugLogs++;
        int vanillaWidth = font.getStringWidth(segment);
        String beforeCursor = minemoticon$getVisibleTextBeforeCursor(visible);
        int compatWidth = font instanceof FontRendererEmojiCompat compat
            ? compat.minemoticon$getStringWidthCompatDirect(
                TextStyleCompat.activeFormatPrefix(text, lineScrollOffset) + beforeCursor)
            : vanillaWidth;
        Minemoticon.debug(
            "TextField compat draw {} x={} visibleLen={} segmentLen={} preCaretLen={} vanillaWidth={} compatWidth={} cursor={} scroll={} visible='{}' segment='{}'",
            minemoticon$textFieldDebugLogs,
            x,
            visible.length(),
            segment.length(),
            beforeCursor.length(),
            vanillaWidth,
            compatWidth,
            cursorPosition,
            lineScrollOffset,
            minemoticon$debugSnippet(visible),
            minemoticon$debugSnippet(segment));
    }

    @Unique
    private static String minemoticon$debugSnippet(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value.replace('\u00A7', '&')
            .replace('\n', ' ');
        return escaped.length() > 80 ? escaped.substring(0, 80) : escaped;
    }

    @Unique
    private String minemoticon$getVisibleText(FontRenderer font) {
        if (text == null || text.isEmpty() || lineScrollOffset >= text.length()) {
            return "";
        }
        int start = Math.max(0, lineScrollOffset);
        return font.trimStringToWidth(text.substring(start), getWidth());
    }

    @Unique
    private boolean minemoticon$shouldMatchColonAliases(String visible) {
        String prefix = TextStyleCompat.activeFormatPrefix(text, lineScrollOffset);
        String candidate = prefix + visible;
        return candidate.indexOf(':') >= 0 && TextStyleCompat.hasExtendedStyle(TextStyleCompat.normalize(candidate));
    }

    @Unique
    private boolean minemoticon$needsWholePreviewDraw(FontRenderer font, String visible) {
        String prefix = TextStyleCompat.activeFormatPrefix(text, lineScrollOffset);
        String candidate = prefix + visible;
        if (candidate.isEmpty()) {
            return false;
        }

        String normalized = TextStyleCompat.normalize(candidate);
        if (EmojiRenderer.parse(normalized) != null || TextStyleCompat.hasExtendedStyle(normalized)) {
            return true;
        }
        // Otherwise vanilla draws the text before and after the cursor as two strings. The second
        // loses the styles set in the first, and custom font glyphs after the cursor shift with it.
        return minemoticon$canDrawCompat(font) && (normalized.indexOf(TextStyleCompat.FORMAT) >= 0
            || ((FontRendererEmojiCompat) font).minemoticon$usesCompatRendering(normalized));
    }

    @Unique
    private static boolean minemoticon$canDrawCompat(FontRenderer font) {
        return font instanceof FontRendererEmojiCompat && ClientEmojiHandler.getFontStack() != null;
    }

    // Walk backwards from pos to find the start of a Unicode emoji sequence ending at pos
    private int findEmojiStart(String text, int pos) {
        if (pos <= 0 || pos > text.length()) return -1;

        if (pos >= EmojiPua.TOKEN_LENGTH) {
            String puaToken = EmojiPua.tokenAt(text, pos - EmojiPua.TOKEN_LENGTH);
            if (puaToken != null) {
                return pos - EmojiPua.TOKEN_LENGTH;
            }
        }

        // Prefer the longest emoji ending at pos so we do not stop on trailing components
        // inside a larger ZWJ ligature.
        for (int len = Math.min(MINEMOTICON$MAX_EMOJI_SEQUENCE_LENGTH, pos); len >= 2; len--) {
            int start = pos - len;
            if (start < 0) break;
            String candidate = text.substring(start, pos);
            var keys = ClientEmojiHandler.UNICODE_KEYS_BY_CHAR.get(candidate.charAt(0));
            if (keys != null) {
                for (String key : keys) {
                    if (key.equals(candidate)) {
                        return start;
                    }
                }
            }
        }

        // Check for surrogate pairs (codepoints above U+FFFF)
        if (pos >= 2 && Character.isLowSurrogate(text.charAt(pos - 1))
            && Character.isHighSurrogate(text.charAt(pos - 2))) {
            return pos - 2;
        }

        // Check for variation selectors (FE0F, FE0E) after a base char
        if (pos >= 2) {
            char prev = text.charAt(pos - 1);
            if (prev == '\uFE0F' || prev == '\uFE0E') {
                return pos - 2; // delete base char + variation selector together
            }
        }

        return -1;
    }

    // Find the end of a Unicode emoji sequence starting at pos
    private int findEmojiEnd(String text, int pos) {
        if (pos < 0 || pos >= text.length()) return pos;

        String puaToken = EmojiPua.tokenAt(text, pos);
        if (puaToken != null) {
            return pos + EmojiPua.TOKEN_LENGTH;
        }

        var keys = ClientEmojiHandler.UNICODE_KEYS_BY_CHAR.get(text.charAt(pos));
        if (keys != null) {
            for (String key : keys) { // sorted longest-first
                if (text.startsWith(key, pos)) {
                    return pos + key.length();
                }
            }
        }

        // Surrogate pair
        if (pos + 1 < text.length() && Character.isHighSurrogate(text.charAt(pos))
            && Character.isLowSurrogate(text.charAt(pos + 1))) {
            int end = pos + 2;
            // Also consume trailing variation selector
            if (end < text.length() && (text.charAt(end) == '\uFE0F' || text.charAt(end) == '\uFE0E')) {
                end++;
            }
            return end;
        }

        return pos;
    }

    private int snapToEmojiBoundary(int position, int previousPosition) {
        if (text == null || text.isEmpty()) return position;
        if (position <= 0 || position >= text.length()) return position;

        int[] bounds = findContainingEmojiBounds(text, position);
        if (bounds == null) {
            return position;
        }

        int start = bounds[0];
        int end = bounds[1];

        if (Math.abs(position - previousPosition) == 1) {
            return position < previousPosition ? start : end;
        }

        int midpoint = start + (end - start) / 2;
        return position <= midpoint ? start : end;
    }

    private int[] findContainingEmojiBounds(String text, int pos) {
        if (pos <= 0 || pos >= text.length()) return null;

        int puaStart = pos - 1;
        if (puaStart >= 0) {
            String leftToken = EmojiPua.tokenAt(text, puaStart);
            if (leftToken != null && pos > puaStart && pos < puaStart + EmojiPua.TOKEN_LENGTH) {
                return new int[] { puaStart, puaStart + EmojiPua.TOKEN_LENGTH };
            }
        }

        String currentToken = EmojiPua.tokenAt(text, pos);
        if (currentToken != null) {
            return new int[] { pos, pos + EmojiPua.TOKEN_LENGTH };
        }

        int scanStart = Math.max(0, pos - MINEMOTICON$MAX_EMOJI_SEQUENCE_LENGTH);
        int bestStart = -1;
        int bestEnd = -1;

        for (int start = pos - 1; start >= scanStart; start--) {
            var keys = ClientEmojiHandler.UNICODE_KEYS_BY_CHAR.get(text.charAt(start));
            if (keys == null) {
                continue;
            }

            for (String key : keys) { // sorted longest-first
                int end = start + key.length();
                if (end <= pos || end > text.length()) {
                    continue;
                }
                if (text.startsWith(key, start)) {
                    if (bestStart < 0 || end - start > bestEnd - bestStart) {
                        bestStart = start;
                        bestEnd = end;
                    }
                    break;
                }
            }
        }

        if (bestStart >= 0 && pos > bestStart && pos < bestEnd) {
            return new int[] { bestStart, bestEnd };
        }

        if (Character.isLowSurrogate(text.charAt(pos)) && Character.isHighSurrogate(text.charAt(pos - 1))) {
            return new int[] { pos - 1, pos + 1 };
        }

        char current = text.charAt(pos);
        if (current == '\uFE0F' || current == '\uFE0E') {
            return new int[] { pos - 1, pos + 1 };
        }

        return null;
    }
}
