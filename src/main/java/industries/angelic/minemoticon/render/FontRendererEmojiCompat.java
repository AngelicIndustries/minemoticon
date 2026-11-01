package industries.angelic.minemoticon.render;

public interface FontRendererEmojiCompat {

    int minemoticon$drawStringCompatDirect(String text, int x, int y, int color, boolean dropShadow);

    int minemoticon$getStringWidthCompatDirect(String text);

    // Whether drawing text needs more than the vanilla renderer: emojis, extended styles or custom fonts
    boolean minemoticon$usesCompatRendering(String text);

    // Where the character at index starts when the whole text is drawn
    float minemoticon$getCaretOffsetCompatDirect(String text, int index);
}
