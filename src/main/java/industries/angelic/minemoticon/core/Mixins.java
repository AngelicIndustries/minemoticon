package industries.angelic.minemoticon.core;

import industries.angelic.angellib.core.AngelMixins;
import industries.angelic.angellib.util.MiscUtil;
import industries.angelic.angellib.util.MixinUtil;

public class Mixins extends AngelMixins {

    private static final Mixins INSTANCE = new Mixins();

    @Override
    protected void registerMixins(MixinUtil.Registry registry) {
        registry.mixin("MixinFontRenderer")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinBatchingFontRenderer")
            .modid("angelica")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();
        registry.mixin("AccessorGuiTextField")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinGuiTextField")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinGuiChat")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinGuiNewChat")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinGuiEditSign")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinGuiRepair")
            .phase(MixinUtil.Phase.EARLY)
            .side(MiscUtil.Side.CLIENT)
            .build();

        registry.mixin("MixinContainerRepair")
            .phase(MixinUtil.Phase.EARLY)
            .build();
    }

    public static java.util.List<String> getEarlyMixinsForLoader() {
        return INSTANCE.getEarlyMixins();
    }

    public static java.util.List<String> getLateMixinsForLoader(java.util.Set<String> loadedCoreMods) {
        return INSTANCE.getLateMixins(loadedCoreMods);
    }
}
