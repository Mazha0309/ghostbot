package com.ghostbot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BotTabCompleter implements TabCompleter {
    
    private final BotManager botManager;
    private final List<String> subCommands = Arrays.asList(
        "spawn", "remove", "list", "tp", "teleport", 
        "look", "move", "stop", "inv", "inventory"
    );
    
    public BotTabCompleter(BotManager botManager) {
        this.botManager = botManager;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 第一级参数：子命令
            String partial = args[0].toLowerCase();
            completions.addAll(subCommands.stream()
                .filter(cmd -> cmd.toLowerCase().startsWith(partial))
                .collect(Collectors.toList()));
                
        } else if (args.length >= 2) {
            // 第二级及以后的参数
            String subCommand = args[0].toLowerCase();
            String partial = args[args.length - 1].toLowerCase();
            
            switch (subCommand) {
                case "spawn":
                case "create":
                    // spawn命令的第二参数可以提示一些常见玩家名
                    if (args.length == 2) {
                        completions.addAll(getOnlinePlayerNames(partial));
                    } else if (args.length == 3) {
                        // 第三参数提示 --nopush
                        if ("--nopush".startsWith(partial)) {
                            completions.add("--nopush");
                        }
                    }
                    break;
                    
                case "remove":
                case "delete":
                case "tp":
                case "teleport":
                case "look":
                case "move":
                case "stop":
                case "inv":
                case "inventory":
                    // 这些命令需要假人名称
                    if (args.length == 2) {
                        completions.addAll(getBotNames(partial));
                    }
                    break;
            }
            
            // look命令的参数提示
            if (subCommand.equals("look") && args.length == 3) {
                // yaw值建议
                completions.addAll(Arrays.asList("0", "90", "180", "-90", "-180", "45", "-45", "135", "-135"));
            } else if (subCommand.equals("look") && args.length == 4) {
                // pitch值建议
                completions.addAll(Arrays.asList("0", "45", "-45", "90", "-90"));
            }
            
            // move命令的参数提示 - 提供当前位置坐标
            if (subCommand.equals("move") && args.length >= 3 && sender instanceof Player) {
                Player player = (Player) sender;
                Location loc = player.getLocation();
                if (args.length == 3) {
                    completions.add(String.format("%.0f", loc.getX()));
                } else if (args.length == 4) {
                    completions.add(String.format("%.0f", loc.getY()));
                } else if (args.length == 5) {
                    completions.add(String.format("%.0f", loc.getZ()));
                }
            }
        }
        
        return completions;
    }
    
    /**
     * 获取匹配的假人名称
     */
    private List<String> getBotNames(String partial) {
        return botManager.getAllBots().stream()
            .map(BotEntity::getName)
            .filter(name -> name.toLowerCase().startsWith(partial))
            .collect(Collectors.toList());
    }
    
    /**
     * 获取匹配的在线玩家名称
     */
    private List<String> getOnlinePlayerNames(String partial) {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(partial))
            .collect(Collectors.toList());
    }
}
