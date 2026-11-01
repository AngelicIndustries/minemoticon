package industries.angelic.minemoticon.mixins.early.minecraft;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import industries.angelic.minemoticon.ClientEmojiHandler;
import industries.angelic.minemoticon.Minemoticon;
import industries.angelic.minemoticon.gui.ChatEmojiTooltipHelper;
import industries.angelic.minemoticon.render.EmojiRenderer;
import industries.angelic.minemoticon.render.FontRendererEmojiCompat;
import industries.angelic.minemoticon.text.TextStyleCompat;

@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {

    @Shadow
    @Final
    private Minecraft mc;

    @Shadow
    @Final
    private List<ChatLine> field_146253_i;

    @Shadow
    private int field_146250_j;

    @Shadow
    public abstract float func_146244_h();

    // Lines with inline styles stay one component, since splitting them at emojis would drop the
    // styles, so vanilla finds no emoji component under the mouse. Find the emoji in the drawn text.
    @Inject(method = "func_146236_a", at = @At("RETURN"), cancellable = true)
    private void minemoticon$emojiHoverFallback(int mouseX, int mouseY, CallbackInfoReturnable<IChatComponent> cir) {
        IChatComponent found = cir.getReturnValue();
        if (found == null || found.getChatStyle()
            .getChatHoverEvent() != null) {
            return;
        }

        // Same mapping vanilla used to pick the component
        int scale = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight).getScaleFactor();
        float chatScale = func_146244_h();
        int x = MathHelper.floor_float((mouseX / scale - 3) / chatScale);
        int y = MathHelper.floor_float((mouseY / scale - 27) / chatScale);
        int lineIndex = y / this.mc.fontRenderer.FONT_HEIGHT + this.field_146250_j;
        if (lineIndex < 0 || lineIndex >= this.field_146253_i.size()) {
            return;
        }

        String text = this.field_146253_i.get(lineIndex)
            .func_151461_a()
            .getFormattedText();
        IChatComponent hover = ChatEmojiTooltipHelper
            .emojiHoverAt(this.mc.fontRenderer, text, x, minemoticon$shouldMatchColonAliases(text));
        if (hover != null) {
            cir.setReturnValue(hover);
        }
    }

    @Unique
    private static int minemoticon$chatDebugLogs;

    @ModifyVariable(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private IChatComponent minemoticon$addEmojiTooltips(IChatComponent component) {
        return ChatEmojiTooltipHelper.withEmojiTooltips(component);
    }

    @ModifyVariable(method = "func_146237_a", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private IChatComponent minemoticon$addEmojiTooltipsOnRefresh(IChatComponent component) {
        return ChatEmojiTooltipHelper.withEmojiTooltips(component);
    }

    @Redirect(
        method = "drawChat",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I"))
    private int minemoticon$drawChatLine(FontRenderer font, String text, int x, int y, int color) {
        boolean previousColonAliasMatching = EmojiRenderer
            .pushColonAliasMatching(minemoticon$shouldMatchColonAliases(text));
        try {
            if (font instanceof FontRendererEmojiCompat compat && minemoticon$shouldUseChatCompat(text)) {
                minemoticon$logChatCompat(text, x, y, true);
                return compat.minemoticon$drawStringCompatDirect(text, x, y, color, true);
            }
            minemoticon$logChatCompat(text, x, y, false);
            return font.drawStringWithShadow(text, x, y, color);
        } finally {
            EmojiRenderer.popColonAliasMatching(previousColonAliasMatching);
        }
    }

    @Unique
    private boolean minemoticon$shouldUseChatCompat(String text) {
        if (text == null || ClientEmojiHandler.getFontStack() == null) {
            return false;
        }

        String normalized = TextStyleCompat.normalize(text);
        return TextStyleCompat.hasExtendedStyle(normalized) || EmojiRenderer.parse(normalized) != null;
    }

    @Unique
    private boolean minemoticon$shouldMatchColonAliases(String text) {
        if (text == null || text.indexOf(':') < 0) {
            return false;
        }
        String normalized = TextStyleCompat.normalize(text);
        return TextStyleCompat.hasExtendedStyle(normalized);
    }

    @Unique
    private void minemoticon$logChatCompat(String text, int x, int y, boolean compat) {
        if (minemoticon$chatDebugLogs >= 24) {
            return;
        }
        String normalized = TextStyleCompat.normalize(text);
        boolean hasEmoji = EmojiRenderer.parse(normalized) != null;
        boolean hasStyle = TextStyleCompat.hasExtendedStyle(normalized);
        if (!compat && !hasEmoji && !hasStyle) {
            return;
        }
        minemoticon$chatDebugLogs++;
        Minemoticon.debug(
            "Chat draw {} compat={} x={} y={} rawLen={} normalizedLen={} emoji={} style={} text='{}'",
            minemoticon$chatDebugLogs,
            compat,
            x,
            y,
            text == null ? -1 : text.length(),
            normalized == null ? -1 : normalized.length(),
            hasEmoji,
            hasStyle,
            minemoticon$debugSnippet(normalized));
    }

    @Unique
    private static String minemoticon$debugSnippet(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value.replace('\u00A7', '&')
            .replace('\n', ' ');
        return escaped.length() > 100 ? escaped.substring(0, 100) : escaped;
    }
}
