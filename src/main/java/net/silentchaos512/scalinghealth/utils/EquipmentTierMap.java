/*
 * Scaling Health
 * Copyright (C) 2018 SilentChaos512
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 3
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.silentchaos512.scalinghealth.utils;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.MobEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TieredItem;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.silentchaos512.scalinghealth.ScalingHealth;
import net.silentchaos512.scalinghealth.capability.IDifficultyAffected;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class EquipmentTierMap {
    private static final Marker MARKER = MarkerManager.getMarker("EquipmentTierMap");

    public int maxTier = 0;
    public final EquipmentSlotType slot;

    List<List<EquipmentEntry>> sets;

    public EquipmentTierMap(EquipmentSlotType type) {
        this.slot = type;

        //Init the list, plus add an empty list since we don't have a tier 0.
        sets = new ArrayList<>();
        sets.add(new ArrayList<>());
    }

    @ParametersAreNullableByDefault
    public void put(EquipmentEntry entry) {
        //if our entry is null, we simply do not add it to our set
        if(entry == null) return;

        if(entry.tier > maxTier) {
            for (int i = maxTier; i < entry.tier; ++i) {
                sets.add(new ArrayList<>());
            }
            maxTier = entry.tier;
        }

        if (entry.getTier() <= 0) {
            throw new IllegalArgumentException(entry.getTier() + " is invalid, tier must be higher than 0");
        }
        if(MobEntity.getSlotForItemStack(entry.getEquipment().get()) != slot){
            throw new IllegalArgumentException("This item, " + entry.getEquipment().get().getItem().getName().getString() + ", is not valid for this slot: " + slot.getName());
        }
        sets.get(entry.getTier()).add(entry);
    }

    @Nullable
    private EquipmentEntry getRandom(int tier) {
        if (tier <= 0 || tier > maxTier) {
            throw new IllegalArgumentException(tier + " is invalid, tier must be between 1 and " + maxTier);
        }

        List<EquipmentEntry> list = sets.get(tier);
        if (list.isEmpty()) {
            return null;
        }

        Random rand = ScalingHealth.random;
        return list.get(rand.nextInt(list.size()));
    }

    @Nullable
    public EquipmentEntry get(int tier, int index) {
        if (tier <= 0 || tier >= maxTier) {
            throw new IllegalArgumentException(tier + " is invalid, tier must be between 1 and " + maxTier);
        }

        List<EquipmentEntry> list = sets.get(tier);
        if (list.isEmpty()) {
            return null;
        }
        if (index < 0 || index >= list.size()) {
            throw new IllegalArgumentException("index must be between 0 and " + list.size());
        }
        return list.get(index);
    }

    public void equip(MobEntity mob, int tier){
        ScalingHealth.LOGGER.debug(MARKER, "Equipping with tier {}", tier);
        IDifficultyAffected data = SHDifficulty.affected(mob);
        EquipmentEntry entry = getRandom(tier);
        if(entry == null) return;
        ItemStack equipment = entry.equipment.get();
        ScalingHealth.LOGGER.debug(MARKER, "Entry is {}", entry);
        //decide whether to equip or not
        if(data.affectiveDifficulty(mob.world) <= entry.getCost()) return;
        //TODO check if mob can equip? doesn't seem to matter though..
        int chances;
        if(entry.getCost() == 0)
            chances = tier*5;
        else
            chances = tier + (int) (data.affectiveDifficulty(mob.world) / entry.getCost() / tier);  //if tier = 3 and cost is 40. Has 3 rolls by default + 1 each 120 difficulty (cost*tier)
                                                                                                    //in contrast tier = 2 and cost is 40, 2 rolls by default + 1 per 80 difficulty
        boolean equip = false;
        int success = this.maxTier * 3;
        for(int i = chances; i > 0; i--){
            //each time have 1 in success chances of success
            if((int) (Math.random()*success + 1) == success) equip = true;
            if(equip) break;
        }
        if(!equip) return;
        ScalingHealth.LOGGER.debug(MARKER, "Success");

        //we decided to equip so now we apply enchantment
        entry.getEnchantments().forEach(enchant -> {
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(enchant));
            int enchantLevel = 0;
            //Might need to tweak this, because it might not be balanced... at first try changing the 3 for a higher number
            if(((int) (Math.random()*3* SHDifficulty.maxValue(mob.world)/data.affectiveDifficulty(mob.world))) % (this.maxTier + 2) - tier == 0){
                int r = (int) (Math.random()* (this.maxTier * 2 + 3));
                if(r <= maxTier * 2){
                    enchantLevel = MathHelper.clamp(r - this.maxTier + tier, 0, 2 * enchantment.getMaxLevel());
                }
            }
            equipment.addEnchantment(enchantment, enchantLevel);
        });
        ScalingHealth.LOGGER.debug(MARKER,"Equipped: " + equipment.getItem().getName().getString());
        mob.setItemStackToSlot(this.slot, equipment.copy());
    }

    public static class EquipmentEntry {
        final Supplier<ItemStack> equipment;
        final int tier;
        final int cost;
        final List<String> enchantments;

        EquipmentEntry(Supplier<ItemStack> equipment, int tier, int cost, List<String> enchantments){
            this.cost = cost;
            this.enchantments = enchantments;
            this.tier = tier;
            this.equipment = equipment;
        }

        @Nullable
        public static EquipmentEntry from(CommentedConfig config, boolean includeCost){
            String nameRaw = config.get("equipment");
            ResourceLocation name = ResourceLocation.tryCreate(nameRaw);
            if(name == null){
                ScalingHealth.LOGGER.error(MARKER, "Invalid ID {}", nameRaw);
                return null;
            }
            ItemStack equipment = new ItemStack(ForgeRegistries.ITEMS.getValue(name));
            if(!(equipment.getItem() instanceof ArmorItem) && !(equipment.getItem() instanceof TieredItem)){
                ScalingHealth.LOGGER.error(MARKER, "No equipment with ID {}", name);
                return null;
            }

            List<String> enchantments = config.get("enchantments");
            if(enchantments.isEmpty()) ScalingHealth.LOGGER.warn("0 possible enchantments were added for this entry");


            int tier = config.getOrElse("tier", 1);
            if(tier < 1) return null;
            int cost = includeCost ? config.getOrElse("minDifficulty", 10) : 0;

            return new EquipmentEntry(() -> equipment, tier, cost, enchantments);
        }

        public Supplier<ItemStack> getEquipment(){
            return equipment;
        }

        public int getTier(){
            return tier;
        }

        public int getCost(){
            return cost;
        }

        public List<String> getEnchantments() {
            return enchantments;
        }

        @Override
        public String toString() {
            return "EquipmentEntry{" +
                    "equipment=" + equipment.get().getItem().getRegistryName() +
                    ", enchantments=" + enchantments +
                    ", cost=" + cost +
                    ", tier=" + tier +
                    "}";
        }
    }
}
