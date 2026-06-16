package com.humankindmc.claysoldiers;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
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
    private final ClaySoldierMessages messages;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey teamKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey modifiersKey;
    private final NamespacedKey sourceTeamIdKey;
    private final NamespacedKey soldierDollRecipeKey;
    private final NamespacedKey shapelessSoldierDollRecipeKey;

    public ClaySoldierItems(Plugin plugin, ClaySoldierSettings settings, ClaySoldierMessages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.itemTypeKey = new NamespacedKey(plugin, "clay_item_type");
        this.teamKey = new NamespacedKey(plugin, "clay_team");
        this.roleKey = new NamespacedKey(plugin, "clay_role");
        this.modifiersKey = new NamespacedKey(plugin, "clay_modifiers");
        this.sourceTeamIdKey = new NamespacedKey(plugin, "clay_source_team_id");
        this.soldierDollRecipeKey = new NamespacedKey(plugin, "clay_soldier_doll");
        this.shapelessSoldierDollRecipeKey = new NamespacedKey(plugin, "clay_soldier_doll_shapeless");
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
        return createSoldierDoll(team, role, amount, Set.of());
    }

    public ItemStack createSoldierDoll(ClayTeam team, ClaySoldierRole role, int amount, Set<ClaySoldierModifier> modifiers) {
        ItemStack item = new ItemStack(Material.CLAY_BALL, Math.max(1, Math.min(this.settings.dollStackSize(), amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.messages.component("items.doll-name", placeholders(team, role, null)));
        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(this.messages.componentList("items.doll-lore", placeholders(team, role, null)));
        if( !modifiers.isEmpty() ) {
            lore.add(this.messages.component("items.doll-modifiers-header"));
            for( ClaySoldierModifier modifier : modifiers ) {
                lore.add(this.messages.component("items.doll-modifier-line", placeholders(team, role, modifier)));
            }
        }
        meta.lore(lore);
        meta.setMaxStackSize(this.settings.dollStackSize());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(this.itemTypeKey, PersistentDataType.STRING, SOLDIER_DOLL_TYPE);
        data.set(this.teamKey, PersistentDataType.STRING, team.key());
        data.set(this.roleKey, PersistentDataType.STRING, role.key());
        data.set(this.modifiersKey, PersistentDataType.STRING, serializeModifiers(modifiers));
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

    public Set<ClaySoldierModifier> getDollModifiers(ItemStack item) {
        if( !isSoldierDoll(item) ) {
            return Set.of();
        }

        String serialized = item.getItemMeta().getPersistentDataContainer().get(this.modifiersKey, PersistentDataType.STRING);
        if( serialized == null || serialized.isBlank() ) {
            return Set.of();
        }

        Set<ClaySoldierModifier> modifiers = new LinkedHashSet<>();
        Arrays.stream(serialized.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(ClaySoldierModifier::fromKey)
                .flatMap(Optional::stream)
                .forEach(modifiers::add);

        return Set.copyOf(modifiers);
    }

    public Optional<ItemStack> createCraftingResult(ItemStack doll, ItemStack ingredient) {
        if( !this.settings.craftingEnabled() || !isSoldierDoll(doll) || ingredient == null || ingredient.getType().isAir() ) {
            return Optional.empty();
        }

        ClayTeam team = getDollTeam(doll).orElse(ClayTeam.CLAY);
        ClaySoldierRole role = getDollRole(doll);
        Set<ClaySoldierModifier> modifiers = new LinkedHashSet<>(getDollModifiers(doll));
        Material material = ingredient.getType();

        Optional<ClaySoldierRole> upgradedRole = roleUpgradeFor(material);
        if( upgradedRole.isPresent() && role == ClaySoldierRole.WARRIOR ) {
            return Optional.of(createSoldierDoll(team, upgradedRole.get(), 1, modifiers));
        }

        Optional<ClayTeam> recoloredTeam = teamForIngredient(material);
        if( recoloredTeam.isPresent() && recoloredTeam.get() != team ) {
            return Optional.of(createSoldierDoll(recoloredTeam.get(), role, 1, modifiers));
        }

        Optional<ClaySoldierModifier> modifier = modifierForIngredient(material);
        if( modifier.isPresent() && modifiers.add(modifier.get()) ) {
            return Optional.of(createSoldierDoll(team, role, 1, modifiers));
        }

        return Optional.empty();
    }

    public Optional<ItemStack> createBaseDollCraftingResult(List<ItemStack> ingredients) {
        if( !this.settings.craftingEnabled() || !matchesBaseDollRecipe(ingredients) ) {
            return Optional.empty();
        }

        return Optional.of(createSoldierDoll(ClayTeam.CLAY, ClaySoldierRole.WARRIOR, this.settings.baseDollOutputAmount()));
    }

    public ItemStack createArmorPiece(Material material, ClayTeam team) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(team.armorColor());
        meta.displayName(this.messages.component("items.armor-name", Map.of(
                "team", team.displayName(),
                "team_color", ClaySoldierMessages.colorCode(team.textColor())
        )));
        meta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public void registerRecipes() {
        this.plugin.getServer().removeRecipe(this.soldierDollRecipeKey);
        this.plugin.getServer().removeRecipe(this.shapelessSoldierDollRecipeKey);

        List<Material> ingredients = this.settings.baseDollIngredients();
        if( ingredients.isEmpty() ) {
            return;
        }

        ItemStack result = createSoldierDoll(ClayTeam.CLAY, ClaySoldierRole.WARRIOR, this.settings.baseDollOutputAmount());
        if( this.settings.baseDollShapedRecipeEnabled() && ingredients.size() >= 2 ) {
            ShapedRecipe recipe = new ShapedRecipe(this.soldierDollRecipeKey, result.clone());
            recipe.shape("A", "B");
            recipe.setIngredient('A', ingredients.get(0));
            recipe.setIngredient('B', ingredients.get(1));
            this.plugin.getServer().addRecipe(recipe);
        }

        if( this.settings.baseDollShapelessRecipeEnabled() ) {
            ShapelessRecipe shapelessRecipe = new ShapelessRecipe(this.shapelessSoldierDollRecipeKey, result.clone());
            for( Material ingredient : ingredients ) {
                shapelessRecipe.addIngredient(ingredient);
            }
            this.plugin.getServer().addRecipe(shapelessRecipe);
        }
    }

    private boolean matchesBaseDollRecipe(List<ItemStack> ingredients) {
        List<Material> recipe = this.settings.baseDollIngredients();
        if( ingredients.size() != recipe.size() ) {
            return false;
        }

        Map<Material, Integer> required = new java.util.HashMap<>();
        for( Material material : recipe ) {
            required.merge(material, 1, Integer::sum);
        }

        for( ItemStack ingredient : ingredients ) {
            if( ingredient == null || ingredient.getType().isAir() ) {
                return false;
            }

            Material material = ingredient.getType();
            Integer remaining = required.get(material);
            if( remaining == null || remaining <= 0 ) {
                return false;
            }

            if( remaining == 1 ) {
                required.remove(material);
            } else {
                required.put(material, remaining - 1);
            }
        }

        return required.isEmpty();
    }

    private Optional<ClaySoldierRole> roleUpgradeFor(Material material) {
        for( ClaySoldierRole role : List.of(ClaySoldierRole.GUARD, ClaySoldierRole.SPEARMAN, ClaySoldierRole.SKIRMISHER, ClaySoldierRole.SLINGER) ) {
            if( material == this.settings.roleUpgradeIngredient(role) ) {
                return Optional.of(role);
            }
        }

        return Optional.empty();
    }

    private Optional<ClayTeam> teamForIngredient(Material material) {
        if( !this.settings.teamRecoloringEnabled() ) {
            return Optional.empty();
        }

        return Arrays.stream(ClayTeam.values())
                .filter(team -> team.iconMaterial() == material)
                .findFirst();
    }

    private Optional<ClaySoldierModifier> modifierForIngredient(Material material) {
        return Arrays.stream(ClaySoldierModifier.values())
                .filter(modifier -> this.settings.modifier(modifier).enabled())
                .filter(modifier -> this.settings.modifier(modifier).ingredient() == material)
                .findFirst();
    }

    private String serializeModifiers(Set<ClaySoldierModifier> modifiers) {
        return modifiers.stream()
                .map(ClaySoldierModifier::key)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private Map<String, String> placeholders(ClayTeam team, ClaySoldierRole role, ClaySoldierModifier modifier) {
        java.util.HashMap<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("team", team.displayName());
        placeholders.put("team_color", ClaySoldierMessages.colorCode(team.textColor()));
        placeholders.put("role", role.displayName());
        placeholders.put("role_color", ClaySoldierMessages.colorCode(role.textColor()));
        placeholders.put("description", role.description());
        if( modifier != null ) {
            placeholders.put("modifier", this.messages.plain("modifiers." + modifier.key()));
            placeholders.put("modifier_color", ClaySoldierMessages.colorCode(modifier.textColor()));
        }

        return placeholders;
    }
}
