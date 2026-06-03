package com.humankindgames.claysoldiers;

import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class ClaySoldierItems {
    private static final String SOLDIER_DOLL_TYPE = "soldier_doll";

    private final Plugin plugin;
    private final ClaySoldierSettings settings;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey teamKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey sourceTeamIdKey;
    private final NamespacedKey soldierDollRecipeKey;

    public ClaySoldierItems(Plugin plugin, ClaySoldierSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.itemTypeKey = new NamespacedKey(plugin, "clay_item_type");
        this.teamKey = new NamespacedKey(plugin, "clay_team");
        this.roleKey = new NamespacedKey(plugin, "clay_role");
        this.sourceTeamIdKey = new NamespacedKey(plugin, "clay_source_team_id");
        this.soldierDollRecipeKey = new NamespacedKey(plugin, "clay_soldier_doll");
    }

    public NamespacedKey teamKey() {
        return this.teamKey;
    }

    public int dollStackSize() {
        return this.settings.dollStackSize();
    }

    public ItemStack createSoldierDoll(ClayTeam team, int amount) {
        return createSoldierDoll(team, ClaySoldierRole.WARRIOR, amount);
    }

    public ItemStack createSoldierDoll(ClayTeam team, ClaySoldierRole role, int amount) {
        ItemStack item = new ItemStack(Material.CLAY_BALL, Math.max(1, Math.min(this.settings.dollStackSize(), amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(team.displayName() + " " + role.displayName() + " Doll", team.textColor())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(role.description(), role.textColor())
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click a block to deploy.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Sneak-use deploys one; normal use deploys the stack.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setMaxStackSize(this.settings.dollStackSize());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(this.itemTypeKey, PersistentDataType.STRING, SOLDIER_DOLL_TYPE);
        data.set(this.teamKey, PersistentDataType.STRING, team.key());
        data.set(this.roleKey, PersistentDataType.STRING, role.key());
        data.set(this.sourceTeamIdKey, PersistentDataType.STRING, team.sourceId().toString());

        item.setItemMeta(meta);
        return item;
    }

    public boolean isSoldierDoll(ItemStack item) {
        if( item == null || item.getType().isAir() || !item.hasItemMeta() ) {
            return false;
        }

        String itemType = item.getItemMeta().getPersistentDataContainer().get(this.itemTypeKey, PersistentDataType.STRING);
        return SOLDIER_DOLL_TYPE.equals(itemType);
    }

    public Optional<ClayTeam> getDollTeam(ItemStack item) {
        if( !isSoldierDoll(item) ) {
            return Optional.empty();
        }

        String teamKeyValue = item.getItemMeta().getPersistentDataContainer().get(this.teamKey, PersistentDataType.STRING);
        if( teamKeyValue == null ) {
            return Optional.empty();
        }

        return ClayTeam.fromKey(teamKeyValue);
    }

    public ClaySoldierRole getDollRole(ItemStack item) {
        if( !isSoldierDoll(item) ) {
            return ClaySoldierRole.WARRIOR;
        }

        String roleKeyValue = item.getItemMeta().getPersistentDataContainer().get(this.roleKey, PersistentDataType.STRING);
        if( roleKeyValue == null ) {
            return ClaySoldierRole.WARRIOR;
        }

        return ClaySoldierRole.fromKey(roleKeyValue).orElse(ClaySoldierRole.WARRIOR);
    }

    public ItemStack createArmorPiece(Material material, ClayTeam team) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(team.armorColor());
        meta.displayName(Component.text(team.displayName() + " Soldier Color", team.textColor())
                .decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public void registerRecipes() {
        this.plugin.getServer().removeRecipe(this.soldierDollRecipeKey);

        ShapedRecipe recipe = new ShapedRecipe(this.soldierDollRecipeKey, createSoldierDoll(ClayTeam.CLAY, ClaySoldierRole.WARRIOR, 4));
        recipe.shape("C", "S");
        recipe.setIngredient('C', Material.CLAY_BALL);
        recipe.setIngredient('S', Material.SOUL_SAND);

        this.plugin.getServer().addRecipe(recipe);
    }
}
