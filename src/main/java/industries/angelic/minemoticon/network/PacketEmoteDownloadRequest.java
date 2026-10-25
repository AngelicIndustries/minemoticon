package industries.angelic.minemoticon.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

// Client -> Server: "I need the image for :name:"
public class PacketEmoteDownloadRequest implements IMessage {

    public String checksum;

    public PacketEmoteDownloadRequest() {}

    public PacketEmoteDownloadRequest(String checksum) {
        this.checksum = checksum;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        checksum = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, checksum);
    }

    public static class Handler implements IMessageHandler<PacketEmoteDownloadRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketEmoteDownloadRequest message, MessageContext ctx) {
            EmoteServerHandler.onDownloadRequest(ctx.getServerHandler().playerEntity, message.checksum);
            return null;
        }
    }
}
