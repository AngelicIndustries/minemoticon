package industries.angelic.minemoticon.core;

import java.util.List;
import java.util.Set;

import com.falsepattern.deploader.DeploaderStub;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import industries.angelic.angellib.core.AngelEarlyMixinLoader;
import industries.angelic.minemoticon.EmojiConfig;
import industries.angelic.minemoticon.Minemoticon;
import industries.angelic.minemoticon.ServerConfig;

@SuppressWarnings("unused")
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class EarlyMixinLoader extends AngelEarlyMixinLoader {

    static {
        DeploaderStub.bootstrap(false);
        DeploaderStub.runDepLoader();
    }

    public EarlyMixinLoader() {
        try {
            ConfigurationManager.registerConfig(EmojiConfig.class);
            ConfigurationManager.registerConfig(ServerConfig.class);
        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getMixinConfig() {
        return "mixins." + Minemoticon.MODID + ".early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return Mixins.getEarlyMixinsForLoader();
    }
}
