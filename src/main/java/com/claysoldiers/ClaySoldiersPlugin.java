package com.claysoldiers;

import com.claysoldiers.claysoldiers.ClaySoldierItems;
import com.claysoldiers.claysoldiers.ClaySoldierListener;
import com.claysoldiers.claysoldiers.ClaySoldierMessages;
import com.claysoldiers.claysoldiers.ClaySoldierService;
import com.claysoldiers.claysoldiers.ClaySoldierSettings;
import com.claysoldiers.claysoldiers.ClaySoldiersCommand;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaySoldiersPlugin
        extends JavaPlugin {
    private ClaySoldierItems claySoldierItems;
    private ClaySoldierService claySoldierService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ClaySoldierSettings settings = ClaySoldierSettings.from(getConfig());
        ClaySoldierMessages messages = new ClaySoldierMessages(this);
        this.claySoldierItems = new ClaySoldierItems(this, settings, messages);
        this.claySoldierService = new ClaySoldierService(this, this.claySoldierItems, settings);

        this.claySoldierItems.registerRecipes();
        this.claySoldierService.start();

        getServer().getPluginManager().registerEvents(
                new ClaySoldierListener(this.claySoldierItems, this.claySoldierService, settings, messages),
                this
        );

        ClaySoldiersCommand claySoldiersCommand = new ClaySoldiersCommand(this.claySoldierItems, this.claySoldierService, messages);
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
