package com.ghostbot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BotPlugin extends JavaPlugin {
    
    private static BotPlugin instance;
    private BotManager botManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("=================================");
        getLogger().info("GhostBot 假人插件已启动!");
        getLogger().info("版本: " + getDescription().getVersion());
        getLogger().info("=================================");
        
        botManager = new BotManager(this);
        
        getCommand("bot").setExecutor(new BotCommand(botManager));
        getCommand("bot").setTabCompleter(new BotTabCompleter(botManager));
        
        getServer().getPluginManager().registerEvents(new BotListener(botManager, this), this);
    }
    
    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.stopDeathCheckTask();
            botManager.removeAllBots();
        }
        getLogger().info("GhostBot 假人插件已关闭!");
    }
    
    public static BotPlugin getInstance() {
        return instance;
    }
    
    public BotManager getBotManager() {
        return botManager;
    }
}
