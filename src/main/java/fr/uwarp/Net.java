package fr.uwarp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";
    /** Limite d'un paquet client -> serveur : 32767 octets. */
    public static final int MAX_JSON = 20000;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Uwarp.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenWarpMsg.class, OpenWarpMsg::encode, OpenWarpMsg::decode, OpenWarpMsg::handle);
        CHANNEL.registerMessage(id++, GoMsg.class, GoMsg::encode, GoMsg::decode, GoMsg::handle);
        CHANNEL.registerMessage(id++, IdeaMsg.class, IdeaMsg::encode, IdeaMsg::decode, IdeaMsg::handle);
        CHANNEL.registerMessage(id++, OpenAdminMsg.class, OpenAdminMsg::encode, OpenAdminMsg::decode, OpenAdminMsg::handle);
        CHANNEL.registerMessage(id++, SaveAdminMsg.class, SaveAdminMsg::encode, SaveAdminMsg::decode, SaveAdminMsg::handle);
        CHANNEL.registerMessage(id++, SetHereMsg.class, SetHereMsg::encode, SetHereMsg::decode, SetHereMsg::handle);
    }

    /** Serveur -> client : ouvre le menu /warp. */
    public record OpenWarpMsg(boolean shop, boolean world, boolean spawn) {
        static void encode(OpenWarpMsg m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.shop);
            buf.writeBoolean(m.world);
            buf.writeBoolean(m.spawn);
        }

        static OpenWarpMsg decode(FriendlyByteBuf buf) {
            return new OpenWarpMsg(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        static void handle(OpenWarpMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.uwarp.client.Hooks.openWarp(m.shop(), m.world(), m.spawn())));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : le joueur choisit une destination (shop / world / spawn). */
    public record GoMsg(String dest) {
        static void encode(GoMsg m, FriendlyByteBuf buf) {
            buf.writeUtf(m.dest, 20);
        }

        static GoMsg decode(FriendlyByteBuf buf) {
            return new GoMsg(buf.readUtf(20));
        }

        static void handle(GoMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) {
                    Actions.go(sp, m.dest());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur : une idee. */
    public record IdeaMsg(String text) {
        static void encode(IdeaMsg m, FriendlyByteBuf buf) {
            buf.writeUtf(m.text, 300);
        }

        static IdeaMsg decode(FriendlyByteBuf buf) {
            return new IdeaMsg(buf.readUtf(300));
        }

        static void handle(IdeaMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) {
                    Actions.idea(sp, m.text());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Serveur -> client (OP) : ouvre /admin avec la config complete. */
    public record OpenAdminMsg(String json) {
        static void encode(OpenAdminMsg m, FriendlyByteBuf buf) {
            buf.writeUtf(m.json, 100000);
        }

        static OpenAdminMsg decode(FriendlyByteBuf buf) {
            return new OpenAdminMsg(buf.readUtf(100000));
        }

        static void handle(OpenAdminMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.uwarp.client.Hooks.openAdmin(m.json())));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur (OP) : sauvegarde la config. */
    public record SaveAdminMsg(String json) {
        static void encode(SaveAdminMsg m, FriendlyByteBuf buf) {
            buf.writeUtf(m.json, MAX_JSON);
        }

        static SaveAdminMsg decode(FriendlyByteBuf buf) {
            return new SaveAdminMsg(buf.readUtf(MAX_JSON));
        }

        static void handle(SaveAdminMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null || !Actions.isOp(sp)) {
                    return;
                }
                if (Cfg.setFromJson(m.json())) {
                    sp.sendSystemMessage(Component.literal("Configuration sauvegardée.")
                            .withStyle(ChatFormatting.GREEN));
                } else {
                    sp.sendSystemMessage(Component.literal("Erreur : configuration invalide.")
                            .withStyle(ChatFormatting.RED));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> serveur (OP) : "ma position actuelle" pour une destination. */
    public record SetHereMsg(String dest) {
        static void encode(SetHereMsg m, FriendlyByteBuf buf) {
            buf.writeUtf(m.dest, 20);
        }

        static SetHereMsg decode(FriendlyByteBuf buf) {
            return new SetHereMsg(buf.readUtf(20));
        }

        static void handle(SetHereMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp == null || !Actions.isOp(sp)) {
                    return;
                }
                Actions.setHere(sp, m.dest());
                Actions.openAdmin(sp);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
