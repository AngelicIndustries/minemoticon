package industries.angelic.minemoticon;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = Minemoticon.MODID, category = "debug", configSubDirectory = "minemoticon")
public class DebugConfig {

    @Config.Comment("Enable debug logging, show builtin emojis in the picker and add /emojiangelicatest (needs Angelica)")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean debugMode;
}
