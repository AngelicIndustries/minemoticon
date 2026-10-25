package industries.angelic.minemoticon.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import industries.angelic.minemoticon.Minemoticon;
import industries.angelic.minemoticon.ServerCapabilities;
import io.netty.buffer.ByteBuf;

// Empty packet. Its arrival alone proves the server has the mod.
public class PacketServerPresence implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketServerPresence, IMessage> {

        @Override
        public IMessage onMessage(PacketServerPresence message, MessageContext ctx) {
            ServerCapabilities.setServerHasMod(true);
            Minemoticon.debug("Received server presence packet, server has mod");
            return null;
        }
    }
}
