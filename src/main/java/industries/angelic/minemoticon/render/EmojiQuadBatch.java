package industries.angelic.minemoticon.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import industries.angelic.minemoticon.api.RenderableEmoji;

// Collects emoji quads and draws them grouped by texture: one bind and one draw call per texture
// instead of per emoji. Stock emojis share the atlas texture, so a run of them is a single draw.
// Only defer quads that nothing gets drawn over before draw() runs.
public final class EmojiQuadBatch {

    private static final int FLOATS_PER_QUAD = 6; // x, y, u0, v0, u1, v1
    private static final int MAX_CACHED_TEXTURES = 256;

    private static final class Group {

        final ResourceLocation texture;
        float[] quads = new float[FLOATS_PER_QUAD * 8];
        int count;

        Group(ResourceLocation texture) {
            this.texture = texture;
        }
    }

    // Groups are kept between draws so steady-state rendering doesn't allocate
    private final Map<ResourceLocation, Group> groups = new HashMap<>();
    private final List<Group> pending = new ArrayList<>();

    public void add(RenderableEmoji emoji, float x, float y) {
        if (EmojiRenderer.isUnresolvedPlaceholder(emoji)) {
            // Untextured, so it can't join a texture group
            EmojiRenderer.renderQuad(emoji, x, y);
            return;
        }

        ResourceLocation texture = emoji.getResourceLocation();
        if (texture == null) return;

        Group group = groups.get(texture);
        if (group == null) {
            group = new Group(texture);
            groups.put(texture, group);
        }
        if (group.count == 0) {
            pending.add(group);
        }

        int i = group.count * FLOATS_PER_QUAD;
        if (i + FLOATS_PER_QUAD > group.quads.length) {
            group.quads = Arrays.copyOf(group.quads, group.quads.length * 2);
        }
        float[] uv = emoji.getUV();
        group.quads[i] = x;
        group.quads[i + 1] = y;
        group.quads[i + 2] = uv != null ? uv[0] : 0.0f;
        group.quads[i + 3] = uv != null ? uv[1] : 0.0f;
        group.quads[i + 4] = uv != null ? uv[2] : 1.0f;
        group.quads[i + 5] = uv != null ? uv[3] : 1.0f;
        group.count++;
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public void draw() {
        if (pending.isEmpty()) return;

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        if (!blendWasEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        }
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        TextureManager textureManager = Minecraft.getMinecraft()
            .getTextureManager();
        Tessellator tessellator = Tessellator.instance;
        float size = EmojiRenderer.EMOJI_SIZE;
        try {
            for (Group group : pending) {
                textureManager.bindTexture(group.texture);
                tessellator.startDrawingQuads();
                float[] quads = group.quads;
                for (int q = 0; q < group.count; q++) {
                    int i = q * FLOATS_PER_QUAD;
                    float x = quads[i];
                    float y = quads[i + 1];
                    tessellator.addVertexWithUV(x, y + size, 0.0, quads[i + 2], quads[i + 5]);
                    tessellator.addVertexWithUV(x + size, y + size, 0.0, quads[i + 4], quads[i + 5]);
                    tessellator.addVertexWithUV(x + size, y, 0.0, quads[i + 4], quads[i + 3]);
                    tessellator.addVertexWithUV(x, y, 0.0, quads[i + 2], quads[i + 3]);
                }
                tessellator.draw();
            }
        } finally {
            discard();
            if (!blendWasEnabled) {
                GL11.glDisable(GL11.GL_BLEND);
            }
        }
    }

    public void discard() {
        for (Group group : pending) {
            group.count = 0;
        }
        pending.clear();
        if (groups.size() > MAX_CACHED_TEXTURES) {
            groups.clear();
        }
    }
}
