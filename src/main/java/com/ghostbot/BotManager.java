package com.ghostbot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BotManager {
    
    private final BotPlugin plugin;
    private final Map<UUID, BotEntity> bots = new HashMap<>();
    private BukkitTask deathCheckTask;
    
    public BotManager(BotPlugin plugin) {
        this.plugin = plugin;
        startDeathCheckTask();
    }
    
    /**
     * 启动死亡检查任务
     */
    private void startDeathCheckTask() {
        // 每10 ticks (0.5秒) 检查一次假人是否死亡
        deathCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (BotEntity bot : new HashMap<>(bots).values()) {
                // 直接检查是否死亡
                if (bot.isDead()) {
                    plugin.getLogger().info("[死亡检查] 检测到假人 " + bot.getName() + " 死亡，正在移除...");
                    UUID uuid = bot.getUniqueId();
                    boolean removed = removeBot(uuid);
                    if (!removed) {
                        plugin.getLogger().warning("[死亡检查] 移除假人 " + bot.getName() + " 失败");
                    }
                }
            }
        }, 10L, 10L);
    }
    
    /**
     * 停止死亡检查任务
     */
    public void stopDeathCheckTask() {
        if (deathCheckTask != null) {
            deathCheckTask.cancel();
            deathCheckTask = null;
        }
    }
    
    /**
     * 创建假人
     * @param name 假人名称
     * @param location 生成位置
     * @return 创建的假人实体
     */
    public BotEntity spawnBot(String name, Location location) {
        return spawnBot(name, location, true);
    }
    
    /**
     * 创建假人
     * @param name 假人名称
     * @param location 生成位置
     * @param pushable 是否可被推动
     * @return 创建的假人实体
     */
    public BotEntity spawnBot(String name, Location location, boolean pushable) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("假人名称不能为空");
        }
        
        name = name.length() > 16 ? name.substring(0, 16) : name;
        
        for (BotEntity existingBot : bots.values()) {
            if (existingBot.getName().equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("已存在同名假人: " + name);
            }
        }
        
        BotEntity bot = new BotEntity(name, location, pushable);
        bots.put(bot.getUniqueId(), bot);
        
        plugin.getLogger().info("假人已生成: " + name + " (UUID: " + bot.getUniqueId() + ")");
        return bot;
    }
    
    /**
     * 移除假人
     * @param uuid 假人UUID
     * @return 是否成功移除
     */
    public boolean removeBot(UUID uuid) {
        BotEntity bot = bots.remove(uuid);
        if (bot != null) {
            try {
                String name = bot.getName();
                plugin.getLogger().info("[BotManager] 开始移除假人: " + name);
                // 移除假人实体（remove方法内部会处理背包保存）
                bot.remove();
                plugin.getLogger().info("[BotManager] 假人已移除: " + name);
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("[BotManager] 移除假人 " + bot.getName() + " 时出错: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        plugin.getLogger().warning("[BotManager] 找不到假人 UUID: " + uuid);
        return false;
    }
    
    /**
     * 移除假人
     * @param name 假人名称
     * @return 是否成功移除
     */
    public boolean removeBot(String name) {
        BotEntity bot = getBot(name);
        if (bot != null) {
            return removeBot(bot.getUniqueId());
        }
        return false;
    }
    
    /**
     * 移除所有假人
     */
    public void removeAllBots() {
        for (BotEntity bot : bots.values()) {
            bot.remove();
        }
        bots.clear();
        plugin.getLogger().info("所有假人已移除");
    }
    
    /**
     * 根据UUID获取假人
     */
    public BotEntity getBot(UUID uuid) {
        return bots.get(uuid);
    }
    
    /**
     * 根据名称获取假人
     */
    public BotEntity getBot(String name) {
        for (BotEntity bot : bots.values()) {
            if (bot.getName().equalsIgnoreCase(name)) {
                return bot;
            }
        }
        return null;
    }
    
    /**
     * 获取所有假人
     */
    public Collection<BotEntity> getAllBots() {
        return bots.values();
    }
    
    /**
     * 检查是否为假人
     */
    public boolean isBot(UUID uuid) {
        return bots.containsKey(uuid);
    }
    
    /**
     * 检查玩家是否为假人
     */
    public boolean isBot(Player player) {
        return isBot(player.getUniqueId());
    }
    
    /**
     * 获取假人数量
     */
    public int getBotCount() {
        return bots.size();
    }
}
