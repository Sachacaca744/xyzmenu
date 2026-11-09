package fr.udynamax;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Dyn {
    public static final float TARGET_HEIGHT = 30f;
    private static final long MAX_TICKS = 20L * 60 * 10;

    private static class Active {
        UUID player, battleId;
        Pokemon pokemon;
        float oldScale, factor;
        long start;
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    /** joueur -> dernier combat ou il a deja dynamaxe (1 fois par combat). */
    private static final Map<UUID, UUID> USED = new HashMap<>();

    public static boolean hasBand(ServerPlayer sp) {
        for (ItemStack s : sp.getInventory().items) if (s.is(Reg.BAND.get())) return true;
        for (ItemStack s : sp.getInventory().offhand) if (s.is(Reg.BAND.get())) return true;
        return false;
    }

    /** Renvoie un message d'erreur, ou null si le Dynamax a demarre. */
    public static String tryStart(ServerPlayer sp) {
        if (!hasBand(sp)) return "Il te faut le Bracelet Dynamax dans ton inventaire.";
        if (ACTIVE.containsKey(sp.getUUID())) return "Ton Pokémon est déjà dynamaxé !";
        PokemonBattle battle = BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(sp);
        if (battle == null) return "Tu dois être en combat Pokémon.";
        if (battle.getBattleId().equals(USED.get(sp.getUUID()))) return "Tu as déjà utilisé le Dynamax dans ce combat.";
        if (WardData.get(sp.serverLevel()).covers(sp.blockPosition()))
            return "Une force étrange t'empêche de dynamaxer ici...";
        BattleActor actor = battle.getActor(sp);
        if (actor == null) return "Tu ne participes pas à ce combat.";
        BattlePokemon bp = null;
        for (ActiveBattlePokemon ap : actor.getActivePokemon()) {
            if (ap.getBattlePokemon() != null) {
                bp = ap.getBattlePokemon();
                break;
            }
        }
        if (bp == null) return "Aucun Pokémon sur le terrain.";
        Pokemon pk = bp.getOriginalPokemon();
        PokemonEntity ent = bp.getEntity();
        if (ent == null) ent = pk.getEntity();
        if (ent == null) return "Ton Pokémon n'est pas sur le terrain.";

        Active a = new Active();
        a.player = sp.getUUID();
        a.battleId = battle.getBattleId();
        a.pokemon = pk;
        a.oldScale = pk.getScaleModifier();
        a.start = sp.serverLevel().getGameTime();
        float h = ent.getBbHeight();
        float factor = h > 0.01f ? a.oldScale * TARGET_HEIGHT / h : 20f;
        factor = Math.max(1f, Math.min(factor, 250f));
        DynSaved ds = DynSaved.get(sp.server);
        ds.old.put(pk.getUuid(), a.oldScale);
        ds.setDirty();
        a.factor = factor;
        pk.setScaleModifier(factor);
        ent.refreshDimensions();
        sendScale(ent, factor);
        ACTIVE.put(sp.getUUID(), a);
        USED.put(sp.getUUID(), a.battleId);

        for (ServerPlayer p : battle.getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("DYNAMAX !").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            p.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0f, 0.6f);
        }
        return null;
    }

    private static void revert(MinecraftServer server, Active a) {
        a.pokemon.setScaleModifier(a.oldScale);
        PokemonEntity e = a.pokemon.getEntity();
        if (e != null) {
            e.refreshDimensions();
            sendScale(e, a.oldScale);
        }
        DynSaved ds = DynSaved.get(server);
        ds.old.remove(a.pokemon.getUuid());
        ds.setDirty();
    }

    public static void revertPlayer(ServerPlayer sp) {
        Active a = ACTIVE.remove(sp.getUUID());
        if (a != null) revert(sp.server, a);
    }

    public static void revertAll(MinecraftServer server) {
        for (Active a : new ArrayList<>(ACTIVE.values())) revert(server, a);
        ACTIVE.clear();
    }

    /** Remet a la taille normale les Pokemon d'un joueur restes geants (crash, deconnexion...). */
    public static int restoreParty(ServerPlayer sp) {
        DynSaved ds = DynSaved.get(sp.server);
        if (ds.old.isEmpty()) return 0;
        Active act = ACTIVE.get(sp.getUUID());
        int n = 0;
        for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(sp)) {
            Float old = ds.old.get(p.getUuid());
            if (old == null) continue;
            if (act != null && act.pokemon == p) continue;
            p.setScaleModifier(old);
            ds.old.remove(p.getUuid());
            n++;
        }
        ds.setDirty();
        return n;
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long t = server.overworld().getGameTime();
        if (t % 5 != 0) return;
        for (Active a : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer sp = server.getPlayerList().getPlayer(a.player);
            PokemonBattle b = BattleRegistry.INSTANCE.getBattle(a.battleId);
            PokemonEntity ent = a.pokemon.getEntity();
            if (sp == null || b == null || b.getEnded() || ent == null || !ent.isAlive()
                    || t - a.start > MAX_TICKS) {
                ACTIVE.remove(a.player);
                revert(server, a);
                if (sp != null) sp.displayClientMessage(
                        Component.literal("Le Dynamax se dissipe...").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            if (t % 20 == 0) sendScale(ent, a.factor);
            effects((ServerLevel) ent.level(), ent, t);
        }
    }

    /** Le client ne recoit pas la taille de Cobblemon : on la lui envoie nous-memes. */
    private static void sendScale(PokemonEntity ent, float scale) {
        Net.CH.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> ent),
                new Net.ScaleMsg(ent.getId(), scale));
    }

    private static void effects(ServerLevel level, PokemonEntity ent, long t) {
        Vec3 c = ent.position();
        DustParticleOptions red = new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.05f), 4.0f);
        DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.25f, 0.0f, 0.02f), 4.0f);
        DustParticleOptions rim = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.1f), 3.0f);
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(c) < 160 * 160) viewers.add(p);
        }
        if (viewers.isEmpty()) return;
        // 3 nuages rouges qui tournent autour
        for (int k = 0; k < 3; k++) {
            double ang = t * 0.03 + k * (Math.PI * 2 / 3);
            double cx = c.x + Math.cos(ang) * 16;
            double cz = c.z + Math.sin(ang) * 16;
            double cy = c.y + 24 + Math.sin(t * 0.05 + k) * 2;
            for (ServerPlayer p : viewers) {
                level.sendParticles(p, red, true, cx, cy, cz, 40, 4.0, 1.5, 4.0, 0.0);
                level.sendParticles(p, dark, true, cx, cy - 1, cz, 14, 3.0, 1.0, 3.0, 0.0);
            }
        }
        // trou rougeatre au-dessus du combat
        double hy = c.y + 46;
        for (ServerPlayer p : viewers) {
            for (int i = 0; i < 28; i++) {
                double a = i * (Math.PI * 2 / 28) + t * 0.04;
                level.sendParticles(p, rim, true, c.x + Math.cos(a) * 12, hy, c.z + Math.sin(a) * 12, 1, 0, 0.2, 0, 0.0);
            }
            level.sendParticles(p, dark, true, c.x, hy, c.z, 30, 7.0, 0.2, 7.0, 0.0);
            // colonne rouge entre le Pokemon et le trou
            for (int i = 0; i < 6; i++) {
                double y = c.y + 32 + i * 2.5;
                level.sendParticles(p, red, true, c.x, y, c.z, 3, 1.5, 0.5, 1.5, 0.0);
            }
        }
    }
}
