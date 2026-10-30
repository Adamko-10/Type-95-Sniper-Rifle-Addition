package dev.type95addition.client;

import dev.type95addition.net.AddonNetwork;
import net.minecraft.client.Minecraft;

/** The client's side of the addon's channel: sending to the server, if it has the addon. */
public final class ClientNet {
    private ClientNet() {
    }

    /** The server we are playing on has the addon. */
    public static boolean serverHasAddon() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && AddonNetwork.channel().isRemotePresent(connection.getConnection());
    }

    public static void send(Object payload) {
        if (serverHasAddon()) {
            AddonNetwork.channel().sendToServer(payload);
        }
    }
}
