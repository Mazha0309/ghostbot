package com.ghostbot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;

public class BotCommand implements CommandExecutor {
    
    private final BotManager botManager;
    
    public BotCommand(BotManager botManager) {
        this.botManager = botManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "spawn":
            case "create":
                return handleSpawn(sender, args);
            case "remove":
            case "delete":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "tp":
            case "teleport":
                return handleTeleport(sender, args);
            case "look":
                return handleLook(sender, args);
            case "move":
                return handleMove(sender, args);
            case "stop":
                return handleStop(sender, args);
            case "inv":
            case "inventory":
                return handleInventory(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "========== GhostBot 假人插件 ==========");
        sender.sendMessage(ChatColor.YELLOW + "/bot spawn <名称> [--nopush]" + ChatColor.GRAY + " - 在当前位置和视角生成假人");
        sender.sendMessage(ChatColor.GRAY + "  --nopush: 不可被推动");
        sender.sendMessage(ChatColor.YELLOW + "/bot remove <名称>" + ChatColor.GRAY + " - 移除指定假人");
        sender.sendMessage(ChatColor.YELLOW + "/bot list" + ChatColor.GRAY + " - 列出所有假人");
        sender.sendMessage(ChatColor.YELLOW + "/bot tp <名称>" + ChatColor.GRAY + " - 将假人传送到你身边");
        sender.sendMessage(ChatColor.YELLOW + "/bot look <名称> <yaw> <pitch>" + ChatColor.GRAY + " - 设置假人视角");
        sender.sendMessage(ChatColor.YELLOW + "/bot move <名称> <x> <y> <z>" + ChatColor.GRAY + " - 移动假人");
        sender.sendMessage(ChatColor.YELLOW + "/bot stop <名称>" + ChatColor.GRAY + " - 停止假人活动并移除");
        sender.sendMessage(ChatColor.YELLOW + "/bot inv <名称>" + ChatColor.GRAY + " - 查看假人背包");
        sender.sendMessage(ChatColor.GRAY + "提示: 右键点击假人也可以打开背包");
        sender.sendMessage(ChatColor.GRAY + "假人死亡时会自动退出游戏并保留背包");
    }
    
    private boolean handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此命令");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /bot spawn <名称> [--nopush]");
            return true;
        }
        
        Player player = (Player) sender;
        String name = args[1];
        // 继承玩家的位置和视角
        Location location = player.getLocation();
        
        // 检查是否有 --nopush 参数
        boolean pushable = true;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--nopush")) {
                pushable = false;
                break;
            }
        }
        
        try {
            BotEntity bot = botManager.spawnBot(name, location, pushable);
            sender.sendMessage(ChatColor.GREEN + "假人 " + name + " 已生成!");
            sender.sendMessage(ChatColor.GRAY + "UUID: " + bot.getUniqueId());
            if (!pushable) {
                sender.sendMessage(ChatColor.GRAY + "该假人不可被推动");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "生成失败: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /bot remove <名称>");
            return true;
        }
        
        String name = args[1];
        
        if (botManager.removeBot(name)) {
            sender.sendMessage(ChatColor.GREEN + "假人 " + name + " 已移除");
        } else {
            sender.sendMessage(ChatColor.RED + "找不到假人: " + name);
        }
        
        return true;
    }
    
    private boolean handleList(CommandSender sender) {
        Collection<BotEntity> bots = botManager.getAllBots();
        
        if (bots.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "当前没有假人");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "========== 假人列表 ==========");
        sender.sendMessage(ChatColor.GRAY + "共 " + bots.size() + " 个假人");
        
        for (BotEntity bot : bots) {
            Location loc = bot.getLocation();
            String world = loc.getWorld() != null ? loc.getWorld().getName() : "未知";
            String status = bot.isOnline() ? ChatColor.GREEN + "在线" : ChatColor.RED + "离线";
            
            sender.sendMessage(ChatColor.YELLOW + bot.getName() + ChatColor.GRAY + 
                " [" + status + ChatColor.GRAY + "] " +
                "世界: " + world + " " +
                "坐标: " + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
        }
        
        return true;
    }
    
    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此命令");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /bot tp <名称>");
            return true;
        }
        
        String name = args[1];
        BotEntity bot = botManager.getBot(name);
        
        if (bot == null) {
            sender.sendMessage(ChatColor.RED + "找不到假人: " + name);
            return true;
        }
        
        Player player = (Player) sender;
        bot.teleport(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "假人 " + name + " 已传送到你的位置");
        
        return true;
    }
    
    private boolean handleLook(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "用法: /bot look <名称> <yaw> <pitch>");
            return true;
        }
        
        String name = args[1];
        BotEntity bot = botManager.getBot(name);
        
        if (bot == null) {
            sender.sendMessage(ChatColor.RED + "找不到假人: " + name);
            return true;
        }
        
        try {
            float yaw = Float.parseFloat(args[2]);
            float pitch = Float.parseFloat(args[3]);
            bot.setRotation(yaw, pitch);
            sender.sendMessage(ChatColor.GREEN + "假人 " + name + " 视角已设置");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "无效的数值");
        }
        
        return true;
    }
    
    private boolean handleMove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "用法: /bot move <名称> <x> <y> <z>");
            return true;
        }
        
        String name = args[1];
        BotEntity bot = botManager.getBot(name);
        
        if (bot == null) {
            sender.sendMessage(ChatColor.RED + "找不到假人: " + name);
            return true;
        }
        
        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            
            Location newLoc = bot.getLocation().clone();
            newLoc.setX(x);
            newLoc.setY(y);
            newLoc.setZ(z);
            
            bot.teleport(newLoc);
            sender.sendMessage(ChatColor.GREEN + "假人 " + name + " 已移动到指定位置");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "无效的坐标值");
        }
        
        return true;
    }
    
    private boolean handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /bot stop <名称>");
            return true;
        }
        
        String name = args[1];
        
        if (botManager.removeBot(name)) {
            sender.sendMessage(ChatColor.GREEN + "假人 " + name + " 已停止并移除");
        } else {
            sender.sendMessage(ChatColor.RED + "找不到假人: " + name);
        }
        
        return true;
    }
    
    private boolean handleInventory(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此命令");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /bot inv <名称>");
            return true;
        }
        
        String name = args[1];
        BotEntity bot = botManager.getBot(name);
        
        if (bot == null) {
            sender.sendMessage(ChatColor.RED + "找不到假人: " + name);
            return true;
        }
        
        Inventory inv = bot.getInventory();
        if (inv != null) {
            Player player = (Player) sender;
            player.openInventory(inv);
        } else {
            sender.sendMessage(ChatColor.RED + "无法打开假人背包");
        }
        
        return true;
    }
}
