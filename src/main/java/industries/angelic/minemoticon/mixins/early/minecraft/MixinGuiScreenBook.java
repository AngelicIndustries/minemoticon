package industries.angelic.minemoticon.mixins.early.minecraft;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import industries.angelic.minemoticon.gui.EmojiPickerGui;
import industries.angelic.minemoticon.gui.EmojiSuggestionHelper;
import industries.angelic.minemoticon.network.EmoteClientHandler;
import industries.angelic.minemoticon.render.EmojiRenderer;
import industries.angelic.minemoticon.text.EmojiPua;

// Book and quill: the emoji picker, autocomplete and :alias: substitution for the page and the title
// being signed. Vanilla edits both by appending at the end, there's no cursor.
@Mixin(GuiScreenBook.class)
public abstract class MixinGuiScreenBook extends GuiScreen {

    @Unique
    private static final int BOOK_TOP = 2;

    @Unique
    private static final int PAGE_TEXT_X = 36;

    @Unique
    private static final int PAGE_TEXT_Y = 32;

    @Unique
    private static final int PAGE_TEXT_WIDTH = 116;

    // Vanilla checks page fit at 118 wide but draws at 116
    @Unique
    private static final int PAGE_FIT_WIDTH = 118;

    @Unique
    private static final int PAGE_FIT_HEIGHT = 118;

    @Unique
    private static final int MAX_PAGE_LENGTH = 255;

    @Unique
    private static final int TITLE_Y = 48;

    // The server rejects longer titles when signing
    @Unique
    private static final int MAX_TITLE_LENGTH = 16;

    @Unique
    private static final int PICKER_BUTTON_SIZE = 12;

    @Unique
    private static final int PICKER_MARGIN = 4;

    @Unique
    private static final int BOOK_BUTTON_HEIGHT = 20;

    @Unique
    private static final int POPUP_MARGIN = 4;

    // Longest text one emoji can be drawn from, :pack/name: aliases included
    @Unique
    private static final int MAX_EMOJI_TEXT_LENGTH = 64;

    @Unique
    private static final String LWJGL3IFY_KEYBOARD_LISTENER = "me.eigenraven.lwjgl3ify.api.InputEvents$KeyboardListener";

    @Shadow
    @Final
    private boolean bookIsUnsigned;

    // Edited since opened
    @Shadow
    private boolean field_146481_r;

    // Signing, editing the title
    @Shadow
    private boolean field_146480_s;

    @Shadow
    private int bookImageWidth;

    @Shadow
    private int bookImageHeight;

    @Shadow
    private String bookTitle;

    @Shadow
    private void updateButtons() {}

    // Current page text
    @Shadow
    private String func_146456_p() {
        return null;
    }

    // Replaces the current page
    @Shadow
    private void func_146457_a(String text) {}

    // Appends to the current page if it still fits
    @Shadow
    private void func_146459_b(String text) {}

    @Unique
    private EmojiPickerGui minemoticon$picker;

    @Unique
    private EmojiSuggestionHelper minemoticon$suggestions;

    // lwjgl3ify's text input is on for this screen. It adds its KeyboardListener here, turns on SDL
    // text events and appends each one to the current page, signing or not. Every typed character
    // also arrives twice as a key event: with the key press, and alone with key code 0.
    @Unique
    private boolean minemoticon$lwjgl3ifyTextInput;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void minemoticon$initGui(CallbackInfo ci) {
        minemoticon$lwjgl3ifyTextInput = false;
        for (Class<?> type = getClass(); type != null; type = type.getSuperclass()) {
            for (Class<?> implemented : type.getInterfaces()) {
                if (LWJGL3IFY_KEYBOARD_LISTENER.equals(implemented.getName())) {
                    minemoticon$lwjgl3ifyTextInput = true;
                }
            }
        }

        if (!bookIsUnsigned) {
            minemoticon$picker = null;
            minemoticon$suggestions = null;
            return;
        }

        // Left of the sign button, so the panel opens beside the book instead of over the page
        int buttonX = Math.max(2, width / 2 - 100 - PICKER_MARGIN - PICKER_BUTTON_SIZE);
        int buttonY = 4 + bookImageHeight + (BOOK_BUTTON_HEIGHT - PICKER_BUTTON_SIZE) / 2;
        minemoticon$picker = new EmojiPickerGui(null, fontRendererObj, width, height, buttonX, buttonY, false);
        minemoticon$suggestions = new EmojiSuggestionHelper(
            minemoticon$getBookTextInput(),
            fontRendererObj,
            false,
            false,
            width,
            height);
    }

    @Inject(method = "updateScreen", at = @At("TAIL"))
    private void minemoticon$updateScreen(CallbackInfo ci) {
        if (minemoticon$picker != null) {
            minemoticon$picker.tick();
        }
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void minemoticon$drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (minemoticon$picker == null) return;

        int dwheel = Mouse.getDWheel();
        if (dwheel != 0 && minemoticon$picker.isOpen()) {
            minemoticon$picker.handleScroll(mouseX, mouseY, Integer.signum(dwheel));
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            minemoticon$suggestions.update();
            minemoticon$suggestions.render(mouseX, mouseY);
            minemoticon$picker.render(mouseX, mouseY);
        } finally {
            GL11.glPopAttrib();
        }
    }

    // The book doesn't override mouseClicked. Taking clicks here keeps them from also pressing the
    // book's buttons under the popups.
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (minemoticon$picker != null) {
            if (minemoticon$suggestions.mouseClicked(mouseX, mouseY, button)) {
                minemoticon$registerInsertedPua(minemoticon$suggestions.consumeInsertText());
                return;
            }

            String insertText = minemoticon$picker.mouseClicked(mouseX, mouseY, button);
            if (insertText != null) {
                minemoticon$insertText(insertText);
                return;
            }
            if (minemoticon$picker.isInsidePanel(mouseX, mouseY)) {
                return;
            }
            if (minemoticon$picker.isOpen()) {
                minemoticon$picker.toggle();
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    // Vanilla only appends while editing a page. lwjgl3ify's text events append always, which put
    // the title being signed into the page, and typing into a signed book showed on its page.
    @Inject(method = "func_146459_b", at = @At("HEAD"), cancellable = true)
    private void minemoticon$appendOnlyWhileEditing(String text, CallbackInfo ci) {
        if (field_146480_s || !bookIsUnsigned) {
            ci.cancel();
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void minemoticon$keyTyped(char c, int keyCode, CallbackInfo ci) {
        if (minemoticon$picker == null) return;

        if (minemoticon$suggestions.isActive() && minemoticon$suggestions.keyTyped(c, keyCode)) {
            minemoticon$registerInsertedPua(minemoticon$suggestions.consumeInsertText());
            ci.cancel();
            return;
        }

        if (minemoticon$picker.keyTyped(c, keyCode)) {
            String text = minemoticon$picker.consumeInsertText();
            if (text != null) {
                minemoticon$insertText(text);
            }
            ci.cancel();
            return;
        }

        if (field_146480_s && minemoticon$dropTitleChar(c, keyCode)) {
            ci.cancel();
            return;
        }

        // Vanilla only pastes into pages
        if (field_146480_s && c == 22) {
            minemoticon$appendToTitle(ChatAllowedCharacters.filerAllowedCharacters(GuiScreen.getClipboardString()));
            ci.cancel();
            return;
        }

        // Vanilla backspace drops one char, which leaves half of a surrogate pair or emoji sequence
        if (keyCode == 14) {
            String text = field_146480_s ? bookTitle : func_146456_p();
            if (text.isEmpty()) return;
            int start = minemoticon$lastUnitStart(text);
            if (start < text.length() - 1) {
                minemoticon$setActiveText(text.substring(0, start));
                ci.cancel();
            }
        }
    }

    @Inject(method = "keyTyped", at = @At("TAIL"))
    private void minemoticon$substituteCompletedAlias(char typedChar, int keyCode, CallbackInfo ci) {
        if (minemoticon$picker == null) return;

        String text = field_146480_s ? bookTitle : func_146456_p();
        EmoteClientHandler.TextReplacement replacement = EmoteClientHandler
            .substituteCompletedAlias(text, text.length());
        if (replacement != null && minemoticon$fits(replacement.text)) {
            minemoticon$setActiveText(replacement.text);
        }
    }

    // Characters vanilla's title input would take that it shouldn't
    @Unique
    private boolean minemoticon$dropTitleChar(char c, int keyCode) {
        if (!ChatAllowedCharacters.isAllowedCharacter(c)) return false;
        // lwjgl3ify's second copy, the key press. Its key code 0 copy carries IME and emoji input too.
        if (minemoticon$lwjgl3ifyTextInput && keyCode != 0) return true;
        // Typed emojis come one surrogate at a time, keep pairs whole at the length limit
        if (Character.isHighSurrogate(c)) return bookTitle.length() + 2 > MAX_TITLE_LENGTH;
        if (Character.isLowSurrogate(c)) {
            return bookTitle.isEmpty() || !Character.isHighSurrogate(bookTitle.charAt(bookTitle.length() - 1));
        }
        return false;
    }

    @Unique
    private void minemoticon$insertText(String text) {
        if (field_146480_s) {
            if (bookTitle.length() + text.length() <= MAX_TITLE_LENGTH) {
                minemoticon$setActiveText(bookTitle + text);
            }
        } else {
            func_146459_b(text);
        }
        minemoticon$registerInsertedPua(text);
    }

    // As much of text as fits, without cutting an emoji
    @Unique
    private void minemoticon$appendToTitle(String text) {
        int room = MAX_TITLE_LENGTH - bookTitle.length();
        int end = 0;
        while (end < text.length()) {
            EmojiRenderer.EmojiMatch emoji = EmojiRenderer.matchAt(text, end);
            int next = emoji != null ? end + emoji.getLength() : end + Character.charCount(text.codePointAt(end));
            if (next > room) break;
            end = next;
        }
        if (end > 0) {
            minemoticon$setActiveText(bookTitle + text.substring(0, end));
        }
    }

    @Unique
    private boolean minemoticon$fits(String text) {
        if (field_146480_s) {
            return text.length() <= MAX_TITLE_LENGTH;
        }
        // Vanilla's check when typing into a page, caret included
        return text.length() <= MAX_PAGE_LENGTH
            && fontRendererObj.splitStringWidth(text + EnumChatFormatting.BLACK + "_", PAGE_FIT_WIDTH)
                <= PAGE_FIT_HEIGHT;
    }

    @Unique
    private void minemoticon$setActiveText(String text) {
        if (field_146480_s) {
            bookTitle = text;
            field_146481_r = true;
            updateButtons();
        } else {
            func_146457_a(text);
        }
    }

    // Start of the emoji or character that ends text
    @Unique
    private static int minemoticon$lastUnitStart(String text) {
        int end = text.length();
        for (int start = Math.max(0, end - MAX_EMOJI_TEXT_LENGTH); start < end; start++) {
            EmojiRenderer.EmojiMatch emoji = EmojiRenderer.matchAt(text, start);
            if (emoji != null && start + emoji.getLength() == end) {
                return start;
            }
        }
        return end - Character.charCount(text.codePointBefore(end));
    }

    @Unique
    private void minemoticon$registerInsertedPua(String text) {
        if (EmojiPua.isPuaToken(text)) {
            EmoteClientHandler.onPuaObserved(text);
        }
    }

    @Unique
    private int minemoticon$bookLeft() {
        return (width - bookImageWidth) / 2;
    }

    @Unique
    private String minemoticon$caret() {
        return fontRendererObj.getBidiFlag() ? "_" : EnumChatFormatting.BLACK + "_";
    }

    // Where the page's last drawn line starts in the page text, and which line it is. Pages wrap,
    // and the caret is always at the end. The last line is the format carried over from the line
    // before, then the end of the page and the caret, wrapped the way drawScreen wraps it.
    @Unique
    private int[] minemoticon$pageLastLine() {
        String page = func_146456_p();
        String caret = minemoticon$caret();
        List<String> lines = fontRendererObj.listFormattedStringToWidth(page + caret, PAGE_TEXT_WIDTH);
        int lineIndex = Math.max(0, lines.size() - 1);
        String last = lines.isEmpty() ? "" : lines.get(lineIndex);
        String tail = last.endsWith(caret) ? last.substring(0, last.length() - caret.length()) : last;
        for (int i = 0; i <= tail.length(); i += 2) {
            String candidate = tail.substring(i);
            if (page.endsWith(candidate)) {
                return new int[] { page.length() - candidate.length(), lineIndex };
            }
            if (i + 1 >= tail.length() || tail.charAt(i) != '§') break;
        }
        return new int[] { page.length(), lineIndex };
    }

    // The page's last line or the title, whichever is being typed into. Suggestions only look back
    // from the caret to the ':', which is on that line.
    @Unique
    private EmojiSuggestionHelper.TextInput minemoticon$getBookTextInput() {
        return new EmojiSuggestionHelper.TextInput() {

            @Override
            public String getText() {
                if (field_146480_s) return bookTitle;
                return func_146456_p().substring(minemoticon$pageLastLine()[0]);
            }

            @Override
            public int getCursorPosition() {
                return getText().length();
            }

            @Override
            public boolean setText(String text) {
                String full = field_146480_s ? text
                    : func_146456_p().substring(0, minemoticon$pageLastLine()[0]) + text;
                if (!minemoticon$fits(full)) return false;
                minemoticon$setActiveText(full);
                return true;
            }

            @Override
            public void setCursorPosition(int position) {}

            @Override
            public int getX() {
                int x = minemoticon$bookLeft() + PAGE_TEXT_X;
                if (!field_146480_s) return x;
                // Centered like drawScreen centers it
                return x + (PAGE_TEXT_WIDTH - fontRendererObj.getStringWidth(bookTitle + minemoticon$caret())) / 2;
            }

            @Override
            public int getY() {
                if (field_146480_s) return BOOK_TOP + TITLE_Y;
                return BOOK_TOP + PAGE_TEXT_Y + minemoticon$pageLastLine()[1] * fontRendererObj.FONT_HEIGHT;
            }

            @Override
            public int getHeight() {
                return fontRendererObj.FONT_HEIGHT;
            }

            @Override
            public int getVisibleWidth() {
                return PAGE_TEXT_WIDTH;
            }

            @Override
            public int getScrollOffset() {
                return 0;
            }

            @Override
            public boolean hasBackground() {
                return false;
            }

            // Beside the book, clear of the page and its buttons
            @Override
            public int getPopupX(int preferredX, int popupWidth, int screenWidth) {
                int rightX = minemoticon$bookLeft() + bookImageWidth + POPUP_MARGIN;
                if (rightX + popupWidth <= screenWidth - 2) {
                    return rightX;
                }
                return minemoticon$bookLeft() - POPUP_MARGIN - popupWidth;
            }

            @Override
            public int getPopupY(int preferredY, int popupHeight, int screenHeight) {
                return getY();
            }
        };
    }
}
