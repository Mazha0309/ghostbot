package com.ghostbot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BotListener implements Listener {
    
    private final BotManager botManager;
    private final BotPlugin plugin;
    // 记录哪些玩家正在查看假人背包
    private final Map<UUID, BotEntity> viewingBotInventory = new HashMap<>();
    
    public BotListener(BotManager botManager, BotPlugin plugin) {
        this.botManager = botManager;
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (botManager.isBot(player)) {
            botManager.removeBot(player.getUniqueId());
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (botManager.isBot(player)) {
            BotEntity bot = botManager.getBot(player.getUniqueId());
            if (bot != null) {
                plugin.getLogger().info("假人 " + player.getName() + " 死亡，准备移除...");
                
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setDroppedExp(0);
                event.setDeathMessage(null);
                
                botManager.removeBot(player.getUniqueId());
                plugin.getLogger().info("假人 " + player.getName() + " 已移除");
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Player) {
            Player clicked = (Player) event.getRightClicked();
            if (botManager.isBot(clicked)) {
                event.setCancelled(true);
                
                BotEntity bot = botManager.getBot(clicked.getUniqueId());
                if (bot != null) {
                    Player player = event.getPlayer();
                    Inventory displayInv = bot.createDisplayInventory();
                    if (displayInv != null) {
                        viewingBotInventory.put(player.getUniqueId(), bot);
                        player.openInventory(displayInv);
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        BotEntity bot = viewingBotInventory.get(player.getUniqueId());
        
        if (bot != null) {
            // 延迟1tick同步，确保物品已经移动完成
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                bot.syncDisplayToRealInventory();
            }, 1L);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        BotEntity bot = viewingBotInventory.remove(player.getUniqueId());
        
        if (bot != null) {
            // 关闭时最后同步一次
            bot.syncDisplayToRealInventory();
        }
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (botManager.isBot(player)) {
                BotEntity bot = botManager.getBot(player.getUniqueId());
                if (bot != null && !bot.isPushable()) {
                }
            }
        }
    }
}