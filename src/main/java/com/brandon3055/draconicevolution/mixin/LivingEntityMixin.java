package com.brandon3055.draconicevolution.mixin;

import com.brandon3055.draconicevolution.init.DEDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.19.2 port only (not in 1.20.1; finding COMBAT-1, WP-30).
 * <p>
 * Emulates the 1.20.1 hurt-knockback exemption for {@code #minecraft:is_explosion}: 1.20.1 {@code LivingEntity.hurt}
 * only knocks the victim back when {@code entity1 != null && !source.is(DamageTypeTags.IS_EXPLOSION)}, while 1.19.2
 * {@code LivingEntity.hurt} (mc-1.19.2 LivingEntity.java:1133-1142) calls {@code knockback(0.4, ...)} for every source
 * that has a causing entity. Only DE damage types flagged {@link DEDamage.Flag#IS_EXPLOSION} are exempted (in practice
 * guardian_laser and guardian_projectile, the only ones built with an entity); vanilla sources keep the vanilla 1.19.2
 * behaviour. Same idea as the bypasses_cooldown emulation in {@link DEDamage#applyCooldownBypass}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Redirect(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"
            )
    )
    private void draconicevolution$hurtKnockback(LivingEntity self, double strength, double x, double z, DamageSource source, float amount) {
        if (!DEDamage.skipsHurtKnockback(source)) {
            self.knockback(strength, x, z);
        }
    }
}
