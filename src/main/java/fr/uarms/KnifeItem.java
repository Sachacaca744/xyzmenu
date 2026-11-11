package fr.uarms;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/** Couteau : tue en un coup. Le couteau de guerre a plus de portee. */
public class KnifeItem extends Item {
    private static final UUID REACH = UUID.fromString("5d9a1f0e-3c11-4c8e-9a52-7a1b0c0d4e11");
    private final boolean war;

    public KnifeItem(boolean war, Properties p) {
        super(p);
        this.war = war;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Knife damage", 1.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Knife speed", war ? -0.8 : -0.4, AttributeModifier.Operation.ADDITION));
        if (war) b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH, "War knife reach", 1.5, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return false;
        if (player.level().isClientSide) return true;
        if (player.getCooldowns().isOnCooldown(this)) return true;
        le.invulnerableTime = 0;
        le.hurt(player.damageSources().playerAttack(player), 10000f);
        player.level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, war ? 0.8f : 1.2f);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        player.getCooldowns().addCooldown(this, war ? 8 : 6);
        return true;
    }
}
