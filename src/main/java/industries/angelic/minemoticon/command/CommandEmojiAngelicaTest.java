package industries.angelic.minemoticon.command;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import industries.angelic.minemoticon.Minemoticon;

/**
 * Client debug command: numbered chat lines mixing every Angelica text effect with stock, custom and
 * animated emojis. Each line ends in a marker whose tooltip only shows when hovering it if the measured
 * width of the line matches what was drawn.
 */
public class CommandEmojiAngelicaTest extends CommandBase {

    private static final String S = "\u00A7";
    private static final String STOCK = "\uD83D\uDE02"; // face with tears of joy
    private static final String STOCK_SEQUENCE = "\uD83D\uDC4D\uD83C\uDFFD"; // thumbs up, medium skin tone
    private static final String CUSTOM = ":minemoticon/chad:";
    private static final String ANIMATED = ":minemoticon/pepevibin:";
    private static final String ANIMATED_ALT = ":minemoticon/jig:";
    private static final List<String> LINES = buildLines();

    @Override
    public String getCommandName() {
        return "emojiangelicatest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/emojiangelicatest [line|widths] - Print Angelica text effect test lines with emojis";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 0 && "widths".equalsIgnoreCase(args[0])) {
            reportWidths(sender);
            return;
        }
        if (args.length > 0) {
            sender.addChatMessage(line(parseIntBounded(sender, args[0], 1, LINES.size())));
            return;
        }

        sender.addChatMessage(header());
        for (int i = 1; i <= LINES.size(); i++) {
            sender.addChatMessage(line(i));
        }
    }

    private static IChatComponent header() {
        return new ChatComponentText("Angelica text test: hover each [end] marker to check widths, emojis for names")
            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY));
    }

    // Per line: what getStringWidth reports, how far drawString actually moves the pen (drawn off
    // screen), and Angelica's own measure. Chat wraps by the first, so it must match the second.
    private static void reportWidths(ICommandSender sender) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        sender.addChatMessage(
            new ChatComponentText("Widths: getStringWidth / drawn / Angelica measure (n/a without Angelica)")
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        for (int i = 0; i <= LINES.size(); i++) {
            String text = (i == 0 ? header() : line(i)).getFormattedText();
            int measured = font.getStringWidth(text);
            int drawn = font.drawString(text, 0, -10000, 0xFFFFFFFF);
            float angelica = angelicaMeasure(font, text);
            boolean mismatch = Math.abs(drawn - measured) > 1;
            String report = String.format(
                "%s: %d / %d / %s%s",
                i == 0 ? "header" : String.valueOf(i),
                measured,
                drawn,
                angelica < 0 ? "n/a" : String.format("%.1f", angelica),
                mismatch ? "  MISMATCH" : "");
            Minemoticon.LOG.info("[emojiangelicatest] {}", report);
            sender.addChatMessage(
                new ChatComponentText(report).setChatStyle(
                    new ChatStyle().setColor(mismatch ? EnumChatFormatting.RED : EnumChatFormatting.GRAY)));
        }
    }

    // Angelica's width for text it draws itself; it doesn't know emojis, so lines with them differ
    private static float angelicaMeasure(FontRenderer font, String text) {
        try {
            Object batcher = font.getClass()
                .getMethod("angelica$getBatcher")
                .invoke(font);
            Method measure = batcher.getClass()
                .getMethod("measure", CharSequence.class, int.class, int.class, boolean.class);
            return (Float) measure.invoke(batcher, text, 0, text.length(), font.getUnicodeFlag());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1.0f;
        }
    }

    private static IChatComponent line(int number) {
        // Unstyled root so the sample does not inherit the prefix or marker style
        IChatComponent line = new ChatComponentText("");
        line.appendSibling(
            new ChatComponentText(number + ". ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        line.appendSibling(new ChatComponentText(LINES.get(number - 1)));
        line.appendSibling(
            new ChatComponentText(" [end]").setChatStyle(
                new ChatStyle().setColor(EnumChatFormatting.YELLOW)
                    .setChatHoverEvent(
                        new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText("Line " + number + " width matches")))));
        return line;
    }

    private static List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        lines.add("plain " + STOCK + " " + STOCK_SEQUENCE + " " + CUSTOM + " " + ANIMATED + " " + ANIMATED_ALT);

        StringBuilder colors = new StringBuilder();
        for (char c : "0123456789abcdef".toCharArray()) {
            colors.append(S)
                .append(c)
                .append(c);
        }
        lines.add(colors + " " + STOCK + " " + S + "e" + CUSTOM + " " + S + "a" + ANIMATED);

        lines.add(S + "lbold " + STOCK + " " + STOCK_SEQUENCE + " " + CUSTOM + " " + ANIMATED + " bold");
        lines.add(
            S + "oitalic "
                + STOCK
                + " "
                + S
                + "nunderline "
                + CUSTOM
                + " "
                + S
                + "r"
                + S
                + "mstrike "
                + ANIMATED_ALT
                + " through");
        lines.add(S + "kobf" + S + "r " + STOCK + " " + S + "kobfuscated " + CUSTOM + " around " + ANIMATED);
        lines.add(S + "qrainbow " + STOCK + " across " + ANIMATED + " emojis " + STOCK_SEQUENCE + " " + CUSTOM);
        lines.add(S + "zwave " + STOCK + " " + ANIMATED_ALT + " on" + S + "z off " + CUSTOM);
        lines.add(
            S + "vflipped " + STOCK + " " + CUSTOM + " " + S + "nunderline stays down" + S + "r upright " + ANIMATED);
        lines.add(
            hex("FF8800") + "rgb "
                + STOCK
                + " "
                + hex("00C8FF")
                + S
                + "lrgb bold "
                + ANIMATED
                + hex("7CFC00")
                + " after "
                + CUSTOM);
        lines.add(gradient("FF0000", "0000FF") + "gradient over " + STOCK + " and " + ANIMATED_ALT + " to the end");
        // Chat text always has a drop shadow, so &u can only recolor it here
        lines.add(
            S + "u"
                + hex("FF0000")
                + "red shadow "
                + STOCK
                + " "
                + S
                + "akept by a color "
                + CUSTOM
                + S
                + "r reset "
                + S
                + "u"
                + hex("00C8FF")
                + "blue shadow "
                + ANIMATED);
        lines.add(
            S + "l"
                + S
                + "qbold rainbow "
                + STOCK
                + S
                + "r "
                + S
                + "z"
                + S
                + "l"
                + gradient("FFFF00", "FF00FF")
                + "wave bold gradient "
                + ANIMATED
                + S
                + "r "
                + S
                + "n"
                + S
                + "o"
                + hex("00FFAA")
                + "underline italic rgb "
                + CUSTOM);
        lines.add(S + "qaliases :joy: :thumbsup:" + S + "r plain :joy: " + CUSTOM);
        lines.add("escaped \\:joy: \\" + CUSTOM + " \\" + STOCK + " stay text");
        lines.add("&#FF8800amp rgb &lbold " + STOCK + " &qamp rainbow " + CUSTOM + " &g&#00FF00&#0000FFamp gradient");

        StringBuilder wrap = new StringBuilder(S + "l");
        for (int i = 0; i < 6; i++) {
            wrap.append("wrap ")
                .append(STOCK)
                .append(' ')
                .append(ANIMATED_ALT)
                .append(' ');
        }
        lines.add(wrap.toString());
        return lines;
    }

    private static String hex(String rgb) {
        StringBuilder sb = new StringBuilder(S + "x");
        for (char c : rgb.toCharArray()) {
            sb.append(S)
                .append(c);
        }
        return sb.toString();
    }

    private static String gradient(String from, String to) {
        return S + "g" + hex(from) + hex(to);
    }
}
