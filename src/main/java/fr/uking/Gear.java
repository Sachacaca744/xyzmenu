package fr.uking;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import java.util.List;
import java.util.UUID;

/** Armes et objets de commandement. */
public class Gear {
    static final UUID REACH = UUID.fromString("5f1b0c6e-2b43-4c0e-9d77-0a1e5d2c9e11");

    /** Arme d'hast : degats + allonge. */
    public static class Reach extends SwordItem {
        final double reach;
        final boolean slow;

        public Reach(Tier t, int dmg, float spd, double reach, boolean slow, Properties p) {
            super(t, dmg, spd, p);
            this.reach = reach;
            this.slow = slow;
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot s) {
            Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(s);
            if (s != EquipmentSlot.MAINHAND || reach <= 0) return base;
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(base);
            b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH, "Reach", reach, AttributeModifier.Operation.ADDITION));
            return b.build();
        }

        @Override
        public boolean hurtEnemy(ItemStack st, LivingEntity t, LivingEntity a) {
            if (slow) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            return super.hurtEnemy(st, t, a);
        }
    }

    /** Arbalete a pied : lourde, puissante, consomme des carreaux. */
    public static class FootCrossbow extends Item {
        public FootCrossbow(Properties p) { super(p); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
            ItemStack st = p.getItemInHand(h);
            if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
            boolean free = p.isCreative() || Cfg.FREE_AMMO.get();
            ItemStack bolt = ItemStack.EMPTY;
            if (!free) {
                for (ItemStack s : p.getInventory().items) if (s.is(Reg.BOLT.get())) { bolt = s; break; }
                if (bolt.isEmpty()) {
                    if (!l.isClientSide) p.displayClientMessage(Component.translatable("uking.msg.nobolt"), true);
                    return InteractionResultHolder.fail(st);
                }
            }
            if (!l.isClientSide) {
                Arrow a = new Arrow(l, p);
                a.setBaseDamage(6.0);
                a.setCritArrow(true);
                a.setPierceLevel((byte) 1);
                a.pickup = AbstractArrow.Pickup.DISALLOWED;
                a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 4.2f, 0.4f);
                l.addFreshEntity(a);
                l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1f, 0.8f);
                if (!free) bolt.shrink(1);
                st.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(h));
            }
            p.getCooldowns().addCooldown(this, 32);
            return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
        }
    }

    /** Plan du batisseur : maj+clic droit (en l'air) change de batiment, clic droit sur le sol construit. */
    public static class Plan extends Item {
        public Plan(Properties p) { super(p); }

        public static int type(ItemStack s) { return Math.floorMod(s.getOrCreateTag().getInt("t"), Fief.BUILDINGS.length); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
            ItemStack s = p.getItemInHand(h);
            if (p.isShiftKeyDown()) {
                s.getOrCreateTag().putInt("t", (type(s) + 1) % Fief.BUILDINGS.length);
                if (!l.isClientSide) announce(p, s);
                return InteractionResultHolder.sidedSuccess(s, l.isClientSide);
            }
            if (!l.isClientSide) announce(p, s);
            return InteractionResultHolder.sidedSuccess(s, l.isClientSide);
        }

        static void announce(Player p, ItemStack s) {
            int t = type(s);
            p.displayClientMessage(Component.translatable("uking.build." + Fief.BUILDINGS[t]).append(" - " + Cfg.cost(Fief.BUILD_COST[t]) + " or  (maj+clic droit : changer)"), true);
        }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            if (sp.isShiftKeyDown()) return InteractionResult.PASS;
            String err = Builder.tryBuild(sp, type(c.getItemInHand()), c.getClickedPos(), sp.getDirection().getOpposite());
            if (err != null) sp.displayClientMessage(Component.literal("§c" + err), true);
            return InteractionResult.CONSUME;
        }

        @Override
        public void appendHoverText(ItemStack s, Level l, List<Component> t, TooltipFlag f) {
            t.add(Component.translatable("uking.build." + Fief.BUILDINGS[type(s)]));
            t.add(Component.translatable("uking.tip.plan"));
        }
    }

    /** Etendard de commandement. */
    public static class Standard extends Item {
        public Standard(Properties p) { super(p); }

        static java.util.List<Soldier> mine(ServerPlayer p) {
            String k = p.getUUID().toString();
            return ((ServerLevel) p.level()).getEntitiesOfClass(Soldier.class, p.getBoundingBox().inflate(60, 40, 60), s -> s.key().equals(k) && s.type().fighter());
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
            ItemStack st = p.getItemInHand(h);
            if (l.isClientSide || !(p instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(st, true);
            int o = (st.getOrCreateTag().getInt("o") + 1) % 3;
            st.getOrCreateTag().putInt("o", o);
            int n = 0;
            for (Soldier s : mine(sp)) { s.setOrder(o); s.setRally(null); s.getNavigation().stop(); if (o != Soldier.O_FOLLOW) s.home = s.blockPosition(); n++; }
            sp.displayClientMessage(Component.translatable("uking.order." + o).append(" (" + n + ")"), true);
            return InteractionResultHolder.consume(st);
        }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            var pos = c.getClickedPos().above();
            int n = 0;
            for (Soldier s : mine(sp)) { s.setOrder(Soldier.O_RALLY); s.setRally(pos); n++; }
            sp.displayClientMessage(Component.translatable("uking.order.3").append(" (" + n + ")"), true);
            return InteractionResult.CONSUME;
        }

        @Override
        public void appendHoverText(ItemStack s, Level l, List<Component> t, TooltipFlag f) {
            t.add(Component.translatable("uking.tip.standard"));
        }
    }

    /** Jeton de recrutement (creatif) : fait apparaitre une unite pour le fief le plus proche. */
    public static class Recruit extends Item {
        final UType type;
        public Recruit(UType t, Properties p) { super(p); type = t; }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            ServerLevel lv = (ServerLevel) c.getLevel();
            Fiefs fs = Fiefs.get(lv);
            Fief f = fs.nearestOwned(sp.getUUID(), c.getClickedPos(), true);
            if (f == null) { sp.displayClientMessage(Component.literal("§cÀ utiliser dans votre fief."), true); return InteractionResult.CONSUME; }
            fs.spawnUnit(lv, f, type, c.getClickedPos().above(), type == UType.PEASANT ? Soldier.O_DEFEND : Soldier.O_FOLLOW);
            if (!sp.isCreative()) c.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
    }

    public static class CatapultItem extends Item {
        public CatapultItem(Properties p) { super(p); }

        @Override
        public InteractionResult useOn(UseOnContext c) {
            if (c.getLevel().isClientSide || !(c.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            ServerLevel lv = (ServerLevel) c.getLevel();
            Fiefs.get(lv).spawnCatapult(lv, sp.getUUID().toString(), c.getClickedPos().above(), sp.getYRot(), 0);
            if (!sp.isCreative()) c.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
    }
}
