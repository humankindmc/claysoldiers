package com.humankindgames;

import com.humankindgames.claysoldiers.ClaySoldierItems;
import com.humankindgames.claysoldiers.ClaySoldierListener;
import com.humankindgames.claysoldiers.ClaySoldierService;
import com.humankindgames.claysoldiers.ClaySoldierSettings;
import com.humankindgames.claysoldiers.ClaySoldiersCommand;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class HumankindGamesPlugin
        extends JavaPlugin {
    private ClaySoldierItems claySoldierItems;
    private ClaySoldierService claySoldierService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ClaySoldierSettings settings = ClaySoldierSettings.from(getConfig());
        this.claySoldierItems = new ClaySoldierItems(this, settings);
        this.claySoldierService = new ClaySoldierService(this, this.claySoldierItems, settings);

        this.claySoldierItems.registerRecipes();
        this.claySoldierService.start();

        getServer().getPluginManager().registerEvents(
                new ClaySoldierListener(this.claySoldierItems, this.claySoldierService, settings),
                this
        );

        ClaySoldiersCommand claySoldiersCommand = new ClaySoldiersCommand(this.claySoldierItems, this.claySoldierService);
        PluginCommand command = Objects.requireNonNull(getCommand("claysoldiers"), "claysoldiers command missing from plugin.yml");
        command.setExecutor(claySoldiersCommand);
        command.setTabCompleter(claySoldiersCommand);
    }

    @Override
    public void onDisable() {
        if( this.claySoldierService != null ) {
            this.claySoldierService.shutdown();
        }
    }
}
