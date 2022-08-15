package net.silentchaos512.scalinghealth.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.silentchaos512.scalinghealth.resources.mechanics.SHMechanicListener;
import net.silentchaos512.scalinghealth.resources.mechanics.SHMechanics;
import net.silentchaos512.scalinghealth.utils.config.EnabledFeatures;
import net.silentchaos512.scalinghealth.utils.config.SHMobs;

//TODO add tag based forcing, as the HOSTILE and PEACEFUL cases are purely determined by the IMob interface
// and the BOSS case makes no more sense as there is no way to know in code
public enum EntityGroup {
    PEACEFUL,
    HOSTILE,
    BOSS,
    BLIGHT,
    PLAYER;

    public static EntityGroup from(LivingEntity entity) {
        return from(entity, false);
    }

    public static EntityGroup from(String name) {
        for(EntityGroup group : values()){
            if(group.name().equalsIgnoreCase(name)){
                return group;
            }
        }
        throw new RuntimeException("Could not get an entity group from name: " + name);
    }

    public static EntityGroup from(LivingEntity entity, boolean ignoreBlightStatus) {
        if (entity instanceof Player)
            return PLAYER;
        if (entity instanceof Enemy) {
            if (!ignoreBlightStatus && SHMobs.isBlight((Mob) entity))
                return BLIGHT;
            return HOSTILE;
        }
        return PEACEFUL;
    }

    public double getPotionChance() {
        if (this == PEACEFUL)
            return SHMobs.passivePotionChance();
        return SHMobs.hostilePotionChance();
    }

    public boolean isAffectedByDamageScaling() {
        switch (this) {
            case PLAYER:
                return EnabledFeatures.playerDamageScalingEnabled();
            case PEACEFUL:
                return SHMechanics.getMechanics().damageScalingMechanics().affectPeaceful;
            default:
                return SHMechanics.getMechanics().damageScalingMechanics().affectHostiles;
        }
    }
}
