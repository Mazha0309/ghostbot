package com.ghostbot;

import com.ghostbot.SkinFetcher.SkinData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class BotEntity {
    
    private final String name;
    private UUID uuid;
    private Object nmsPlayer;
    private Player bukkitPlayer;
    private Location location;
    private boolean online = false;
    private SkinData skinData;
    private Inventory savedInventory; // 保存的假人背包
    private Inventory displayInventory; // 45格的展示背包（用于右键打开）
    private boolean pushable = true; // 是否可被推动
    
    // 反射缓存
    private static Class<?> minecraftServerClass;
    private static Class<?> serverLevelClass;
    private static Class<?> gameProfileClass;
    private static Class<?> propertyClass;
    private static Class<?> clientInfoClass;
    private static Class<?> serverPlayerClass;
    private static Class<?> connectionClass;
    private static Class<?> packetFlowClass;
    private static Class<?> serverGamePacketListenerClass;
    private static Class<?> commonListenerCookieClass;
    private static Class<?> craftServerClass;
    private static Class<?> craftWorldClass;
    private static Class<?> craftPlayerClass;
    private static boolean classesInitialized = false;
    
    /**
     * 延迟初始化NMS类
     */
    private static synchronized void initClasses() {
        if (classesInitialized) return;
        
        try {
            // 获取NMS版本（兼容新旧版本）
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String nmsVersion = "";
            
            // 检查是否是旧版本包名格式 (org.bukkit.craftbukkit.v1_XX_RX)
            if (packageName.contains(".v1_")) {
                String[] parts = packageName.split("\\.");
                if (parts.length >= 4) {
                    nmsVersion = parts[3] + ".";
                }
            }
            // 新版本 (1.17+) 包名是 org.bukkit.craftbukkit，不需要版本号
            
            minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            clientInfoClass = Class.forName("net.minecraft.server.level.ClientInformation");
            serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            connectionClass = Class.forName("net.minecraft.network.Connection");
            packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
            serverGamePacketListenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            commonListenerCookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            
            // 根据版本构建类名
            if (nmsVersion.isEmpty()) {
                // 新版本 (1.17+)
                craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
                craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
                craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            } else {
                // 旧版本
                craftServerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + "CraftServer");
                craftWorldClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + "CraftWorld");
                craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + "entity.CraftPlayer");
            }
            
            classesInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
            BotPlugin.getInstance().getLogger().severe("初始化NMS类失败: " + e.getMessage());
        }
    }
    
    public BotEntity(String name, Location location) {
        this(name, location, true);
    }
    
    public BotEntity(String name, Location location, boolean pushable) {
        this.name = name;
        this.location = location.clone();
        this.pushable = pushable;
        
        // 始终生成新的随机UUID，避免与已存在的假人冲突
        this.uuid = UUID.randomUUID();
        
        // 尝试获取正版皮肤（仅用于皮肤，不使用其UUID）
        this.skinData = SkinFetcher.fetchSkin(name);
        
        spawn();
    }
    
    /**
     * 生成假人到世界
     */
    private void spawn() {
        try {
            // 延迟初始化类
            initClasses();
            
            if (!classesInitialized) {
                BotPlugin.getInstance().getLogger().severe("NMS类未初始化，无法生成假人");
                return;
            }
            
            // 获取MinecraftServer实例
            Object craftServer = craftServerClass.cast(Bukkit.getServer());
            Method getServerMethod = craftServerClass.getMethod("getServer");
            Object minecraftServer = getServerMethod.invoke(craftServer);
            
            // 获取ServerLevel
            Object craftWorld = craftWorldClass.cast(location.getWorld());
            Method getHandleMethod = craftWorldClass.getMethod("getHandle");
            Object serverLevel = getHandleMethod.invoke(craftWorld);
            
            // 创建GameProfile
            Constructor<?> gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            Object gameProfile = gameProfileConstructor.newInstance(uuid, name);
            
            // 如果有皮肤数据，添加到GameProfile
            if (skinData != null && skinData.hasSkin()) {
                applySkinToProfile(gameProfile);
            }
            
            // 创建ClientInformation
            Method createDefaultMethod = clientInfoClass.getMethod("createDefault");
            Object clientInfo = createDefaultMethod.invoke(null);
            
            // 创建ServerPlayer
            Constructor<?> serverPlayerConstructor = serverPlayerClass.getConstructor(
                minecraftServerClass, serverLevelClass, gameProfileClass, clientInfoClass
            );
            nmsPlayer = serverPlayerConstructor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);
            
            // 设置位置
            Method setPosMethod = serverPlayerClass.getMethod("setPos", double.class, double.class, double.class);
            setPosMethod.invoke(nmsPlayer, location.getX(), location.getY(), location.getZ());
            
            Method setYRotMethod = serverPlayerClass.getMethod("setYRot", float.class);
            setYRotMethod.invoke(nmsPlayer, location.getYaw());
            
            Method setXRotMethod = serverPlayerClass.getMethod("setXRot", float.class);
            setXRotMethod.invoke(nmsPlayer, location.getPitch());
            
            // 创建Connection
            Method serverboundMethod = packetFlowClass.getMethod("valueOf", String.class);
            Object serverbound = serverboundMethod.invoke(null, "SERVERBOUND");
            Constructor<?> connectionConstructor = connectionClass.getConstructor(packetFlowClass);
            Object connection = connectionConstructor.newInstance(serverbound);
            
            // 创建CommonListenerCookie
            Method createInitialMethod = commonListenerCookieClass.getMethod("createInitial", gameProfileClass, boolean.class);
            Object cookie = createInitialMethod.invoke(null, gameProfile, false);
            
            // 创建ServerGamePacketListenerImpl
            Constructor<?> packetListenerConstructor = serverGamePacketListenerClass.getConstructor(
                minecraftServerClass, connectionClass, serverPlayerClass, commonListenerCookieClass
            );
            Object packetListener = packetListenerConstructor.newInstance(minecraftServer, connection, nmsPlayer, cookie);
            
            // 设置connection字段
            Field channelField = connectionClass.getDeclaredField("channel");
            channelField.setAccessible(true);
            Object fakeChannel = createFakeChannel();
            channelField.set(connection, fakeChannel);
            
            // 设置listener - 在 1.21+ 版本中需要在特定阶段设置
            try {
                Method setListenerMethod = connectionClass.getMethod("setListener", Class.forName("net.minecraft.network.PacketListener"));
                setListenerMethod.invoke(connection, packetListener);
            } catch (NoSuchMethodException e) {
                // 1.21+ 版本可能使用不同的监听器设置方式，跳过直接设置
                BotPlugin.getInstance().getLogger().fine("未找到 setListener 方法，使用新版本方式");
            }
            
            // 设置nmsPlayer的connection字段
            Field playerConnectionField = serverPlayerClass.getDeclaredField("connection");
            playerConnectionField.setAccessible(true);
            playerConnectionField.set(nmsPlayer, packetListener);
            
            // 1.21+ 版本：重置实体的 removed 状态，防止被标记为 DISCARDED
            try {
                Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                Field removalReasonField = entityClass.getDeclaredField("removalReason");
                removalReasonField.setAccessible(true);
                removalReasonField.set(nmsPlayer, null);
            } catch (Exception e) {
                // 忽略，可能字段名不同
            }
            
            // 1.21+ 版本：直接调用 placeNewPlayer，它会内部处理 addNewPlayer
            // 旧版本需要分开调用
            Method getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");
            Object playerList = getPlayerListMethod.invoke(minecraftServer);
            
            try {
                // 尝试 1.21+ 版本的 placeNewPlayer 签名
                Method placeNewPlayerMethod = playerList.getClass().getMethod("placeNewPlayer", 
                    connectionClass, serverPlayerClass, commonListenerCookieClass);
                placeNewPlayerMethod.invoke(playerList, connection, nmsPlayer, cookie);
            } catch (NoSuchMethodException e) {
                // 旧版本：先添加到世界再触发加入
                Method addNewPlayerMethod = serverLevelClass.getMethod("addNewPlayer", serverPlayerClass);
                addNewPlayerMethod.invoke(serverLevel, nmsPlayer);
                
                Method placeNewPlayerMethod = playerList.getClass().getMethod("placeNewPlayer", 
                    connectionClass, serverPlayerClass, commonListenerCookieClass);
                placeNewPlayerMethod.invoke(playerList, connection, nmsPlayer, cookie);
            }
            
            // 获取Bukkit玩家
            Method getBukkitEntityMethod = serverPlayerClass.getMethod("getBukkitEntity");
            bukkitPlayer = (Player) getBukkitEntityMethod.invoke(nmsPlayer);
            
            // 初始化生命值 - 设置为满血（20点）
            try {
                Method getMaxHealthMethod = serverPlayerClass.getMethod("getMaxHealth");
                float maxHealth = (float) getMaxHealthMethod.invoke(nmsPlayer);
                Method setHealthMethod = serverPlayerClass.getMethod("setHealth", float.class);
                setHealthMethod.invoke(nmsPlayer, maxHealth);
            } catch (Exception e) {
                // 如果失败，尝试设置默认值
                try {
                    Method setHealthMethod = serverPlayerClass.getMethod("setHealth", float.class);
                    setHealthMethod.invoke(nmsPlayer, 20.0f);
                } catch (Exception ignored) {}
            }
            
            // 设置为生存模式（非创造/冒险模式）
            try {
                Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
                Method survivalMethod = gameTypeClass.getMethod("valueOf", String.class);
                Object survivalMode = survivalMethod.invoke(null, "SURVIVAL");
                Method setGameModeMethod = serverPlayerClass.getMethod("setGameMode", gameTypeClass);
                setGameModeMethod.invoke(nmsPlayer, survivalMode);
            } catch (Exception e) {
                BotPlugin.getInstance().getLogger().fine("无法设置游戏模式: " + e.getMessage());
            }
            
            // 设置可受伤（取消无敌状态）
            try {
                Method setInvulnerableMethod = serverPlayerClass.getMethod("setInvulnerable", boolean.class);
                setInvulnerableMethod.invoke(nmsPlayer, false);
            } catch (Exception e) {
                // 尝试字段方式
                try {
                    Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                    Field invulnerableField = entityClass.getDeclaredField("invulnerable");
                    invulnerableField.setAccessible(true);
                    invulnerableField.setBoolean(nmsPlayer, false);
                } catch (Exception ignored) {}
            }
            
            // 取消上帝模式（如果需要）
            try {
                Method setAbilitiesMethod = serverPlayerClass.getMethod("getAbilities");
                Object abilities = setAbilitiesMethod.invoke(nmsPlayer);
                if (abilities != null) {
                    Field invulnerableField = abilities.getClass().getField("invulnerable");
                    invulnerableField.setBoolean(abilities, false);
                    
                    Field mayBuildField = abilities.getClass().getField("mayBuild");
                    mayBuildField.setBoolean(abilities, true);
                    
                    // 设置instabuild为false（取消创造模式破坏能力）
                    try {
                        Field instabuildField = abilities.getClass().getField("instabuild");
                        instabuildField.setBoolean(abilities, false);
                    } catch (Exception ignored) {}
                    
                    // 设置mayfly为false（取消飞行）
                    try {
                        Field mayFlyField = abilities.getClass().getField("mayfly");
                        mayFlyField.setBoolean(abilities, false);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                BotPlugin.getInstance().getLogger().fine("无法设置能力: " + e.getMessage());
            }
            
            // 确保实体可以被物理影响（重力、推动等）
            try {
                Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                
                // 设置noPhysics为false（允许物理效果）
                try {
                    Method setNoPhysicsMethod = entityClass.getMethod("setNoPhysics", boolean.class);
                    setNoPhysicsMethod.invoke(nmsPlayer, false);
                } catch (Exception e) {
                    try {
                        Field noPhysicsField = entityClass.getDeclaredField("noPhysics");
                        noPhysicsField.setAccessible(true);
                        noPhysicsField.setBoolean(nmsPlayer, false);
                    } catch (Exception ignored) {}
                }
                
                // 设置isInPowderSnow为false
                try {
                    Field powderSnowField = entityClass.getDeclaredField("isInPowderSnow");
                    powderSnowField.setAccessible(true);
                    powderSnowField.setBoolean(nmsPlayer, false);
                } catch (Exception ignored) {}
                
                // 设置onGround为true（让假人认为自己在地面上）
                try {
                    Field onGroundField = entityClass.getDeclaredField("onGround");
                    onGroundField.setAccessible(true);
                    onGroundField.setBoolean(nmsPlayer, true);
                } catch (Exception e) {
                    try {
                        Method setOnGroundMethod = entityClass.getMethod("setOnGround", boolean.class);
                        setOnGroundMethod.invoke(nmsPlayer, true);
                    } catch (Exception ignored) {}
                }
                
            } catch (Exception e) {
                BotPlugin.getInstance().getLogger().fine("无法设置物理属性: " + e.getMessage());
            }
            
            // 重新设置位置和视角（防止被重置）
            try {
                Method setPosMethod2 = serverPlayerClass.getMethod("setPos", double.class, double.class, double.class);
                setPosMethod2.invoke(nmsPlayer, location.getX(), location.getY(), location.getZ());
                
                Method setYRotMethod2 = serverPlayerClass.getMethod("setYRot", float.class);
                setYRotMethod2.invoke(nmsPlayer, location.getYaw());
                
                Method setXRotMethod2 = serverPlayerClass.getMethod("setXRot", float.class);
                setXRotMethod2.invoke(nmsPlayer, location.getPitch());
                
                // 同时设置Bukkit位置
                if (bukkitPlayer != null) {
                    // 找到一个安全的位置（防止卡在方块里）
                    Location safeLoc = location.clone();
                    safeLoc.setY(Math.floor(safeLoc.getY())); // 向下取整到方块边界
                    bukkitPlayer.teleport(safeLoc);
                }
            } catch (Exception e) {
                BotPlugin.getInstance().getLogger().fine("重新设置位置失败: " + e.getMessage());
            }
            
            // 启用区块加载 - 设置视距（1.21+ 版本使用 setChunkRadius 或其他方法）
            try {
                Method setChunkTrackingRadiusMethod = serverPlayerClass.getMethod("setChunkTrackingRadius", int.class);
                setChunkTrackingRadiusMethod.invoke(nmsPlayer, 10);
            } catch (NoSuchMethodException e) {
                // 1.21+ 版本可能使用不同的方法名或不需要手动设置
                BotPlugin.getInstance().getLogger().fine("setChunkTrackingRadius 方法不存在，跳过");
            }
            
            // 刷新玩家显示，确保皮肤正确加载
            Bukkit.getScheduler().runTaskLater(BotPlugin.getInstance(), () -> {
                refreshPlayerDisplay();
            }, 10L);
            
            // 延迟检查物理状态
            Bukkit.getScheduler().runTaskLater(BotPlugin.getInstance(), () -> {
                checkPhysicsStatus();
            }, 40L); // 2秒后检查
            
            online = true;
            
            // 应用推动设置
            setPushable(this.pushable);
            
            String skinInfo = skinData != null && skinData.hasSkin() ? "(使用正版皮肤)" : "(使用默认皮肤)";
            BotPlugin.getInstance().getLogger().info("假人已生成: " + name + " " + skinInfo);
            BotPlugin.getInstance().getLogger().info("  位置: " + String.format("%.2f, %.2f, %.2f", location.getX(), location.getY(), location.getZ()));
            BotPlugin.getInstance().getLogger().info("  视角: Yaw=" + location.getYaw() + ", Pitch=" + location.getPitch());
            BotPlugin.getInstance().getLogger().info("  生命值: " + getHealth());
            BotPlugin.getInstance().getLogger().info("  可被推动: " + pushable);
            
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            BotPlugin.getInstance().getLogger().severe("生成假人失败: " + e.getMessage());
            BotPlugin.getInstance().getLogger().severe("完整堆栈跟踪:\n" + stackTrace);
        }
    }
    
    /**
     * 应用皮肤到GameProfile
     */
    private void applySkinToProfile(Object gameProfile) throws Exception {
        if (skinData == null || !skinData.hasSkin()) return;
        
        // 获取properties属性
        Method getPropertiesMethod = gameProfileClass.getMethod("getProperties");
        Object properties = getPropertiesMethod.invoke(gameProfile);
        
        // 创建Property - 使用正确的构造函数
        Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class, String.class);
        Object property = propertyConstructor.newInstance("textures", skinData.getTexture(), skinData.getSignature());
        
        // 添加到properties - 先清空再添加，避免重复
        try {
            Method removeAllMethod = properties.getClass().getMethod("removeAll", Object.class);
            removeAllMethod.invoke(properties, "textures");
        } catch (Exception ignored) {}
        
        // 使用 Multimap 的 put 方法
        Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
        putMethod.invoke(properties, "textures", property);
        
        BotPlugin.getInstance().getLogger().fine("皮肤已应用到 GameProfile: " + name);
    }
    
    /**
     * 刷新玩家显示，重新发送玩家信息包
     */
    private void refreshPlayerDisplay() {
        try {
            if (bukkitPlayer == null || !bukkitPlayer.isOnline()) return;
            
            // 获取ServerPlayer的getUpdateTabListName方法（如果有）
            // 或者使用其他方式刷新玩家显示
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != bukkitPlayer) {
                    player.hidePlayer(BotPlugin.getInstance(), bukkitPlayer);
                    player.showPlayer(BotPlugin.getInstance(), bukkitPlayer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 创建假的Netty通道 - 为 1.21+ 版本提供完整的假通道实现
     */
    private Object createFakeChannel() throws Exception {
        Class<?> channelClass = Class.forName("io.netty.channel.Channel");
        Class<?> eventLoopClass = Class.forName("io.netty.channel.EventLoop");
        Class<?> channelFutureClass = Class.forName("io.netty.channel.ChannelFuture");
        Class<?> pipelineClass = Class.forName("io.netty.channel.ChannelPipeline");
        Class<?> channelConfigClass = Class.forName("io.netty.channel.ChannelConfig");
        
        // 使用数组来存储引用，以便在闭包中修改
        final Object[] fakeChannelRef = new Object[1];
        final Object[] fakeChannelFutureRef = new Object[1];
        
        // 创建假 EventLoop
        Object fakeEventLoop = java.lang.reflect.Proxy.newProxyInstance(
            eventLoopClass.getClassLoader(),
            new Class<?>[]{eventLoopClass},
            (proxy, method, args) -> {
                String methodName = method.getName();
                if (methodName.equals("inEventLoop")) {
                    return true;
                }
                if (methodName.equals("execute") && args != null && args.length > 0) {
                    if (args[0] instanceof Runnable) {
                        ((Runnable) args[0]).run();
                    }
                    return null;
                }
                if (methodName.equals("submit") || methodName.equals("schedule")) {
                    return createFakeFuture();
                }
                return null;
            }
        );
        
        // 创建假 ChannelConfig
        Object fakeConfig = java.lang.reflect.Proxy.newProxyInstance(
            channelConfigClass.getClassLoader(),
            new Class<?>[]{channelConfigClass},
            (proxy, method, args) -> {
                String methodName = method.getName();
                // setAutoRead, setOption 等方法返回 this
                if (methodName.startsWith("set") || methodName.contains("Option")) {
                    return proxy;
                }
                // get 方法返回默认值
                if (methodName.startsWith("get") || methodName.startsWith("is")) {
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class || returnType == Boolean.class) {
                        return true;
                    } else if (returnType == int.class || returnType == Integer.class) {
                        return 0;
                    } else if (returnType == long.class || returnType == Long.class) {
                        return 0L;
                    }
                }
                return null;
            }
        );
        
        // 创建假 Pipeline
        final Object[] fakePipelineRef = new Object[1];
        fakePipelineRef[0] = java.lang.reflect.Proxy.newProxyInstance(
            pipelineClass.getClassLoader(),
            new Class<?>[]{pipelineClass},
            (proxy, method, args) -> {
                String methodName = method.getName();
                // ChannelPipeline 的方法返回类型：
                // add*, remove*, replace* 返回 ChannelPipeline
                // flush() 返回 ChannelPipeline
                // channel() 返回 Channel
                // context() 返回 ChannelHandlerContext
                if (methodName.equals("addLast") || methodName.equals("remove") || 
                    methodName.equals("addFirst") || methodName.contains("add") || 
                    methodName.contains("remove") || methodName.contains("replace") ||
                    methodName.equals("flush") || methodName.equals("fireChannelActive") ||
                    methodName.equals("fireChannelInactive") || methodName.equals("fireExceptionCaught") ||
                    methodName.equals("fireChannelRead") || methodName.equals("fireChannelReadComplete") ||
                    methodName.equals("fireChannelWritabilityChanged") || methodName.equals("fireUserEventTriggered")) {
                    return proxy;
                }
                if (methodName.equals("channel")) {
                    return fakeChannelRef[0];
                }
                return null;
            }
        );
        Object fakePipeline = fakePipelineRef[0];
        
        // 创建假 ChannelFuture
        fakeChannelFutureRef[0] = java.lang.reflect.Proxy.newProxyInstance(
            channelFutureClass.getClassLoader(),
            new Class<?>[]{channelFutureClass},
            (proxy, method, args) -> {
                String methodName = method.getName();
                if (methodName.equals("syncUninterruptibly") || 
                    methodName.equals("awaitUninterruptibly") ||
                    methodName.equals("await") ||
                    methodName.equals("sync") ||
                    methodName.equals("addListener") ||
                    methodName.equals("removeListener")) {
                    return proxy;
                }
                if (methodName.equals("isSuccess") || methodName.equals("isDone")) {
                    return true;
                }
                if (methodName.equals("isCancelled")) {
                    return false;
                }
                if (methodName.equals("cause")) {
                    return null;
                }
                if (methodName.equals("channel")) {
                    return fakeChannelRef[0];
                }
                return null;
            }
        );
        
        // 创建假 Channel
        fakeChannelRef[0] = java.lang.reflect.Proxy.newProxyInstance(
            channelClass.getClassLoader(),
            new Class<?>[]{channelClass},
            (proxy, method, args) -> {
                String methodName = method.getName();
                if (methodName.equals("isOpen") || methodName.equals("isActive") || 
                    methodName.equals("isRegistered") || methodName.equals("isWritable")) {
                    return true;
                }
                if (methodName.equals("eventLoop")) {
                    return fakeEventLoop;
                }
                if (methodName.equals("closeFuture")) {
                    return fakeChannelFutureRef[0];
                }
                if (methodName.equals("pipeline")) {
                    return fakePipeline;
                }
                if (methodName.equals("config")) {
                    return fakeConfig;
                }
                // Channel 的 flush() 返回 Channel (this)，不是 ChannelFuture
                if (methodName.equals("flush")) {
                    return proxy;
                }
                // writeAndFlush, write, close, disconnect 返回 ChannelFuture
                if (methodName.equals("writeAndFlush") || methodName.equals("write") || 
                    methodName.equals("close") || methodName.equals("disconnect")) {
                    return fakeChannelFutureRef[0];
                }
                if (methodName.equals("alloc")) {
                    return null;
                }
                if (methodName.equals("newPromise") || methodName.equals("newSucceededFuture") || 
                    methodName.equals("newFailedFuture")) {
                    return fakeChannelFutureRef[0];
                }
                return null;
            }
        );
        
        return fakeChannelRef[0];
    }
    
    private Object createFakeFuture() throws Exception {
        Class<?> futureClass = Class.forName("io.netty.util.concurrent.Future");
        return java.lang.reflect.Proxy.newProxyInstance(
            futureClass.getClassLoader(),
            new Class<?>[]{futureClass},
            (proxy, method, args) -> {
                String methodName = method.getName();
                if (methodName.equals("isDone") || methodName.equals("isSuccess")) {
                    return true;
                }
                if (methodName.equals("isCancelled")) {
                    return false;
                }
                if (methodName.equals("cancel")) {
                    return false;
                }
                if (methodName.equals("cause")) {
                    return null;
                }
                if (methodName.equals("get")) {
                    return null;
                }
                return null;
            }
        );
    }
    
    /**
     * 移除假人
     */
    public void remove() {
        // 修改判断条件：只要 nmsPlayer 不为 null 就可以移除
        if (nmsPlayer != null) {
            try {
                // 掉落物品和保存背包
                if (bukkitPlayer != null) {
                    Location dropLoc = bukkitPlayer.getLocation();
                    org.bukkit.World world = dropLoc.getWorld();
                    org.bukkit.inventory.PlayerInventory inv = bukkitPlayer.getInventory();
                    
                    // 1. 掉落主背包物品（0-35）
                    ItemStack[] contents = inv.getContents();
                    for (int i = 0; i < 36 && i < contents.length; i++) {
                        if (contents[i] != null && contents[i].getType() != org.bukkit.Material.AIR) {
                            world.dropItemNaturally(dropLoc, contents[i].clone());
                        }
                    }
                    
                    // 2. 掉落装备栏（头盔、胸甲、护腿、靴子）
                    ItemStack[] armor = inv.getArmorContents();
                    for (ItemStack item : armor) {
                        if (item != null && item.getType() != org.bukkit.Material.AIR) {
                            world.dropItemNaturally(dropLoc, item.clone());
                        }
                    }
                    
                    // 3. 掉落副手物品
                    ItemStack offHand = inv.getItemInOffHand();
                    if (offHand != null && offHand.getType() != org.bukkit.Material.AIR) {
                        world.dropItemNaturally(dropLoc, offHand.clone());
                    }
                    
                    // 4. 广播退出游戏消息（黄色，像真实玩家一样）
                    String quitMessage = org.bukkit.ChatColor.YELLOW + name + " 退出了游戏";
                    org.bukkit.Bukkit.broadcastMessage(quitMessage);
                    BotPlugin.getInstance().getLogger().info(quitMessage);
                    
                    // 5. 保存背包到 savedInventory（用于右键查看）
                    try {
                        int displaySize = 45; // 5行，必须是9的倍数
                        savedInventory = Bukkit.createInventory(null, displaySize, name + "的背包");
                        
                        // 复制主背包（0-35）到 0-35
                        for (int i = 0; i < 36 && i < contents.length; i++) {
                            if (contents[i] != null) {
                                savedInventory.setItem(i, contents[i].clone());
                            }
                        }
                        
                        // 复制装备到 36-39（第5行）
                        for (int i = 0; i < armor.length && i < 4; i++) {
                            if (armor[i] != null) {
                                savedInventory.setItem(36 + i, armor[i].clone());
                            }
                        }
                        
                        // 复制副手到 40
                        if (offHand != null) {
                            savedInventory.setItem(40, offHand.clone());
                        }
                    } catch (Exception e) {
                        BotPlugin.getInstance().getLogger().fine("保存背包失败: " + e.getMessage());
                    }
                }
                
                // 强制从玩家列表移除（这是唯一能确保假人完全移除的方法）
                BotPlugin.getInstance().getLogger().info("正在移除假人: " + name);
                forceRemovePlayer();
                
                online = false;
                nmsPlayer = null;
                bukkitPlayer = null;
                
                BotPlugin.getInstance().getLogger().info("假人 " + name + " 已移除");
            } catch (Exception e) {
                BotPlugin.getInstance().getLogger().severe("移除假人时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void forceRemovePlayer() {
        try {
            // 获取玩家列表
            Method getServerMethod = craftServerClass.getMethod("getServer");
            Object minecraftServer = getServerMethod.invoke(craftServerClass.cast(Bukkit.getServer()));
            Method getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");
            Object playerList = getPlayerListMethod.invoke(minecraftServer);
            
            // 尝试从玩家列表移除
            try {
                // 方法1: 使用 remove 方法
                Method removeMethod = playerList.getClass().getMethod("remove", serverPlayerClass);
                removeMethod.invoke(playerList, nmsPlayer);
                BotPlugin.getInstance().getLogger().fine("已从玩家列表移除");
            } catch (Exception e1) {
                BotPlugin.getInstance().getLogger().fine("remove 方法失败，尝试 disconnect");
                
                // 方法2: 使用 disconnect 方法
                try {
                    Method disconnectMethod = serverPlayerClass.getMethod("disconnect");
                    disconnectMethod.invoke(nmsPlayer);
                    BotPlugin.getInstance().getLogger().fine("已 disconnect");
                } catch (Exception e2) {
                    BotPlugin.getInstance().getLogger().fine("disconnect 失败，尝试 discard");
                    
                    // 方法3: 使用 discard 方法
                    try {
                        Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                        Method discardMethod = entityClass.getMethod("discard");
                        discardMethod.invoke(nmsPlayer);
                        BotPlugin.getInstance().getLogger().fine("已 discard");
                    } catch (Exception e3) {
                        BotPlugin.getInstance().getLogger().warning("所有移除方法都失败: " + e3.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            BotPlugin.getInstance().getLogger().severe("无法获取玩家列表: " + e.getMessage());
        }
    }
    
    private void removeFromPlayerList() {
        try {
            Method getServerMethod = craftServerClass.getMethod("getServer");
            Object minecraftServer = getServerMethod.invoke(craftServerClass.cast(Bukkit.getServer()));
            Method getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");
            Object playerList = getPlayerListMethod.invoke(minecraftServer);
            Method removePlayerMethod = playerList.getClass().getMethod("remove", serverPlayerClass);
            removePlayerMethod.invoke(playerList, nmsPlayer);
            BotPlugin.getInstance().getLogger().fine("已从玩家列表移除: " + name);
        } catch (Exception e) {
            BotPlugin.getInstance().getLogger().warning("无法从玩家列表移除: " + e.getMessage());
            // 最后的备选：直接标记为已移除
            try {
                Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                Method discardMethod = entityClass.getMethod("discard");
                discardMethod.invoke(nmsPlayer);
                BotPlugin.getInstance().getLogger().fine("已使用 discard 移除: " + name);
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * 传送假人
     */
    public void teleport(Location location) {
        if (nmsPlayer != null && online) {
            try {
                this.location = location.clone();
                
                Object craftWorld = craftWorldClass.cast(location.getWorld());
                Method getHandleMethod = craftWorldClass.getMethod("getHandle");
                Object serverLevel = getHandleMethod.invoke(craftWorld);
                
                Method teleportMethod = serverPlayerClass.getMethod("teleportTo", 
                    serverLevelClass, double.class, double.class, double.class, float.class, float.class);
                teleportMethod.invoke(nmsPlayer, serverLevel, location.getX(), location.getY(), 
                    location.getZ(), location.getYaw(), location.getPitch());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 设置视角
     */
    public void setRotation(float yaw, float pitch) {
        if (nmsPlayer != null) {
            try {
                Method setYRotMethod = serverPlayerClass.getMethod("setYRot", float.class);
                setYRotMethod.invoke(nmsPlayer, yaw);
                
                Method setXRotMethod = serverPlayerClass.getMethod("setXRot", float.class);
                setXRotMethod.invoke(nmsPlayer, pitch);
                
                location.setYaw(yaw);
                location.setPitch(pitch);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取假人名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取假人UUID
     */
    public UUID getUniqueId() {
        return uuid;
    }
    
    /**
     * 获取Bukkit玩家对象
     */
    public Player getBukkitPlayer() {
        return bukkitPlayer;
    }
    
    /**
     * 获取当前位置
     */
    public Location getLocation() {
        if (nmsPlayer != null) {
            try {
                Method getXMethod = serverPlayerClass.getMethod("getX");
                Method getYMethod = serverPlayerClass.getMethod("getY");
                Method getZMethod = serverPlayerClass.getMethod("getZ");
                Method getYRotMethod = serverPlayerClass.getMethod("getYRot");
                Method getXRotMethod = serverPlayerClass.getMethod("getXRot");
                
                double x = (double) getXMethod.invoke(nmsPlayer);
                double y = (double) getYMethod.invoke(nmsPlayer);
                double z = (double) getZMethod.invoke(nmsPlayer);
                float yaw = (float) getYRotMethod.invoke(nmsPlayer);
                float pitch = (float) getXRotMethod.invoke(nmsPlayer);
                
                return new Location(location.getWorld(), x, y, z, yaw, pitch);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return location.clone();
    }
    
    /**
     * 检查假人是否在线
     */
    public boolean isOnline() {
        if (!online || nmsPlayer == null) {
            return false;
        }
        // 优先使用 Bukkit API
        if (bukkitPlayer != null) {
            return bukkitPlayer.isOnline() && !bukkitPlayer.isDead();
        }
        // 备用：使用 NMS 反射
        try {
            Method isRemovedMethod = serverPlayerClass.getMethod("isRemoved");
            if ((boolean) isRemovedMethod.invoke(nmsPlayer)) {
                return false;
            }
            Method isDeadMethod = serverPlayerClass.getMethod("isDeadOrDying");
            return !((boolean) isDeadMethod.invoke(nmsPlayer));
        } catch (Exception e) {
            return online;
        }
    }
    
    /**
     * 获取背包
     */
    public Inventory getInventory() {
        if (bukkitPlayer != null) {
            return bukkitPlayer.getInventory();
        }
        return null;
    }
    
    /**
     * 是否有皮肤
     */
    public boolean hasSkin() {
        return skinData != null && skinData.hasSkin();
    }
    
    /**
     * 创建45格的展示背包，从真实背包复制内容
     */
    public Inventory createDisplayInventory() {
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            // 创建45格的展示背包
            displayInventory = Bukkit.createInventory(null, 45, name + "的背包");
            org.bukkit.inventory.PlayerInventory inv = bukkitPlayer.getInventory();
            
            // 复制主背包（0-35）
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < 36 && i < contents.length; i++) {
                if (contents[i] != null) {
                    displayInventory.setItem(i, contents[i].clone());
                }
            }
            
            // 复制装备到36-39
            ItemStack[] armor = inv.getArmorContents();
            for (int i = 0; i < armor.length && i < 4; i++) {
                if (armor[i] != null) {
                    displayInventory.setItem(36 + i, armor[i].clone());
                }
            }
            
            // 复制副手到40
            ItemStack offHand = inv.getItemInOffHand();
            if (offHand != null) {
                displayInventory.setItem(40, offHand.clone());
            }
            
            return displayInventory;
        } else if (savedInventory != null) {
            return savedInventory;
        }
        return null;
    }
    
    /**
     * 将展示背包的物品同步回真实背包
     */
    public void syncDisplayToRealInventory() {
        if (displayInventory == null || bukkitPlayer == null || !bukkitPlayer.isOnline()) {
            return;
        }
        
        org.bukkit.inventory.PlayerInventory inv = bukkitPlayer.getInventory();
        
        // 同步主背包（0-35）
        for (int i = 0; i < 36; i++) {
            ItemStack item = displayInventory.getItem(i);
            inv.setItem(i, item != null ? item.clone() : null);
        }
        
        // 同步装备（36-39）
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            ItemStack item = displayInventory.getItem(36 + i);
            armor[i] = item != null ? item.clone() : null;
        }
        inv.setArmorContents(armor);
        
        // 同步副手（40）
        ItemStack offHand = displayInventory.getItem(40);
        inv.setItemInOffHand(offHand != null ? offHand.clone() : null);
    }
    
    /**
     * 获取当前打开的展示背包
     */
    public Inventory getDisplayInventory() {
        return displayInventory;
    }
    
    /**
     * 获取保存的背包（假人被移除后）
     */
    public Inventory getSavedInventory() {
        return savedInventory;
    }
    
    /**
     * 设置保存的背包
     */
    public void setSavedInventory(Inventory inventory) {
        this.savedInventory = inventory;
    }
    
    /**
     * 是否可被推动
     */
    public boolean isPushable() {
        return pushable;
    }
    
    /**
     * 设置是否可被推动
     */
    public void setPushable(boolean pushable) {
        this.pushable = pushable;
        // 更新NMS实体的推动属性
        if (nmsPlayer != null && online) {
            try {
                // 不要设置 noPhysics，因为它会阻止所有物理效果（重力、移动等）
                // 改用其他方式控制推动
                
                // 方法1: 尝试使用 setDiscardFriction (true 表示忽略摩擦力，可用于防止推动)
                try {
                    Method setDiscardFrictionMethod = serverPlayerClass.getMethod("setDiscardFriction", boolean.class);
                    setDiscardFrictionMethod.invoke(nmsPlayer, !pushable);
                } catch (Exception e1) {
                    // 方法2: 尝试设置标记
                    try {
                        Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                        // 使用 setSharedFlag 或类似方法
                        Method setFlagMethod = entityClass.getMethod("setSharedFlag", int.class, boolean.class);
                        // flag 2 通常是 "INVISIBLE"，我们尝试找到正确的推动相关标记
                        // 在 1.21 中可能需要使用其他方式
                    } catch (Exception ignored) {}
                }
                
                // 记录日志
                BotPlugin.getInstance().getLogger().fine("假人 " + name + " 推动设置: " + pushable);
            } catch (Exception e) {
                BotPlugin.getInstance().getLogger().fine("无法设置推动属性: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理假人死亡
     */
    public void onDeath() {
        if (online) {
            // 保存背包 - 使用45格（5行，必须是9的倍数）
            if (bukkitPlayer != null) {
                int displaySize = 45; // 必须是9的倍数
                savedInventory = Bukkit.createInventory(null, displaySize, name + "的背包");
                ItemStack[] contents = bukkitPlayer.getInventory().getContents();
                for (int i = 0; i < contents.length && i < displaySize; i++) {
                    if (contents[i] != null) {
                        savedInventory.setItem(i, contents[i].clone());
                    }
                }
            }
            // 标记为离线
            online = false;
        }
    }
    
    /**
     * 恢复背包到假人
     */
    public void restoreInventory() {
        if (bukkitPlayer != null && savedInventory != null) {
            ItemStack[] contents = savedInventory.getContents();
            for (int i = 0; i < contents.length && i < bukkitPlayer.getInventory().getSize(); i++) {
                if (contents[i] != null) {
                    bukkitPlayer.getInventory().setItem(i, contents[i].clone());
                }
            }
        }
    }
    
    /**
     * 获取NMS玩家对象
     */
    public Object getNMSPlayer() {
        return nmsPlayer;
    }
    
    /**
     * 检查假人是否死亡（生命值 <= 0）
     * @return 如果假人死亡返回 true
     */
    public boolean isDead() {
        // 如果完全不在线且没有NMS对象，不算死亡（可能还没生成）
        if (!online && nmsPlayer == null) {
            return false;
        }
        // 优先使用 Bukkit API（如果可用）
        if (bukkitPlayer != null) {
            try {
                // 检查是否死亡或正在死亡
                boolean isDead = bukkitPlayer.isDead();
                double health = bukkitPlayer.getHealth();
                boolean result = isDead || health <= 0;
                if (result) {
                    BotPlugin.getInstance().getLogger().fine("假人 " + name + " 死亡检测: isDead=" + isDead + ", health=" + health);
                }
                return result;
            } catch (Exception e) {
                BotPlugin.getInstance().getLogger().fine("检查假人 " + name + " 死亡状态时出错: " + e.getMessage());
            }
        }
        // 备用：使用反射获取 NMS 生命值
        if (nmsPlayer != null) {
            try {
                Method getHealthMethod = serverPlayerClass.getMethod("getHealth");
                float health = (float) getHealthMethod.invoke(nmsPlayer);
                boolean result = health <= 0;
                if (result) {
                    BotPlugin.getInstance().getLogger().fine("假人 " + name + " NMS 死亡检测: health=" + health);
                }
                return result;
            } catch (Exception e) {
                BotPlugin.getInstance().getLogger().fine("NMS 检查假人 " + name + " 死亡状态时出错: " + e.getMessage());
            }
        }
        return false;
    }
    
    /**
     * 获取假人生命值
     * @return 生命值，如果获取失败返回 -1
     */
    public float getHealth() {
        // 优先使用 Bukkit API
        if (bukkitPlayer != null) {
            try {
                return (float) bukkitPlayer.getHealth();
            } catch (Exception e) {
                // 如果失败，尝试NMS
            }
        }
        // 使用NMS反射
        if (nmsPlayer == null) {
            return -1;
        }
        try {
            Method getHealthMethod = serverPlayerClass.getMethod("getHealth");
            return (float) getHealthMethod.invoke(nmsPlayer);
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * 设置假人生命值
     * @param health 生命值
     */
    public void setHealth(float health) {
        if (nmsPlayer == null || !online) {
            return;
        }
        try {
            Method setHealthMethod = serverPlayerClass.getMethod("setHealth", float.class);
            setHealthMethod.invoke(nmsPlayer, health);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 检查物理状态（调试用）
     */
    private void checkPhysicsStatus() {
        if (nmsPlayer == null || !online) {
            return;
        }
        try {
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            
            // 检查 noPhysics
            boolean noPhysics = false;
            try {
                Field noPhysicsField = entityClass.getDeclaredField("noPhysics");
                noPhysicsField.setAccessible(true);
                noPhysics = noPhysicsField.getBoolean(nmsPlayer);
            } catch (Exception e) {
                // 尝试方法
                try {
                    Method isNoPhysicsMethod = entityClass.getMethod("isNoPhysics");
                    noPhysics = (boolean) isNoPhysicsMethod.invoke(nmsPlayer);
                } catch (Exception ignored) {}
            }
            
            // 检查 invulnerable
            boolean invulnerable = true;
            try {
                Method isInvulnerableMethod = entityClass.getMethod("isInvulnerable");
                invulnerable = (boolean) isInvulnerableMethod.invoke(nmsPlayer);
            } catch (Exception ignored) {}
            
            // 检查是否在地面
            boolean onGround = false;
            try {
                Field onGroundField = entityClass.getDeclaredField("onGround");
                onGroundField.setAccessible(true);
                onGround = onGroundField.getBoolean(nmsPlayer);
            } catch (Exception ignored) {}
            
            BotPlugin.getInstance().getLogger().info("假人 " + name + " 物理状态:");
            BotPlugin.getInstance().getLogger().info("  noPhysics: " + noPhysics);
            BotPlugin.getInstance().getLogger().info("  invulnerable: " + invulnerable);
            BotPlugin.getInstance().getLogger().info("  onGround: " + onGround);
            
        } catch (Exception e) {
            BotPlugin.getInstance().getLogger().warning("无法检查物理状态: " + e.getMessage());
        }
    }
}
