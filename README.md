# GhostBot - Minecraft 假人插件

一个能让假人被服务器当作正常玩家对待的 Bukkit/Paper 插件。假人拥有独立的 UUID，能够正常加载区块，支持正版玩家皮肤。

## 功能特性

- ✅ 假人被服务器识别为真实玩家
- ✅ 分配独立 UUID（使用正版玩家UUID如果有的话）
- ✅ **支持正版玩家皮肤**（自动从Mojang API获取）
- ✅ 自动加载周围区块（10格视距）
- ✅ 支持传送、视角控制、移动
- ✅ 支持查看假人背包
- ✅ 支持多假人管理
- ✅ **Tab命令补全**

## 命令

所有命令支持Tab补全！

| 命令 | 描述 |
|------|------|
| `/bot spawn <名称>` | 生成假人（使用正版玩家皮肤） |
| `/bot remove <名称>` | 移除假人 |
| `/bot list` | 列出所有假人 |
| `/bot tp <名称>` | 将假人传送到你身边 |
| `/bot look <名称> <yaw> <pitch>` | 设置假人视角 |
| `/bot move <名称> <x> <y> <z>` | 移动假人到指定坐标 |
| `/bot stop <名称>` | 停止假人并移除 |
| `/bot inv <名称>` | 查看假人背包 |

### Tab补全示例

```
/bot sp<Tab>          -> /bot spawn
/bot spawn <Tab>      -> 显示在线玩家名称建议
/bot remove <Tab>     -> 显示所有假人名称
/bot look TestBot <Tab> -> 显示yaw值建议(0, 90, 180...)
/bot move TestBot <Tab> -> 显示当前X坐标
```

## 权限

- `bot.admin` - 所有假人管理权限（默认OP）
- `bot.spawn` - 生成假人
- `bot.remove` - 移除假人
- `bot.list` - 列出假人
- `bot.tp` - 传送假人
- `bot.look` - 控制假人视角
- `bot.move` - 移动假人
- `bot.stop` - 停止假人
- `bot.inv` - 查看假人背包

## 编译说明

### 环境要求
- Java 17+
- Maven 3.6+ 或 Gradle 7.0+
- Paper 1.21 服务端

### Maven编译

```bash
mvn clean package
```

编译后的插件位于 `target/bot-1.0.0.jar`

### Gradle编译

```bash
gradle build
```

编译后的插件位于 `build/libs/bot-1.0.0.jar`

### 安装插件

1. 将 jar 文件放入服务器的 `plugins` 文件夹
2. 重启服务器或使用 `/reload` 命令

## 使用示例

```
# 生成一个名为"Notch"的假人（会使用Notch的正版皮肤）
/bot spawn Notch

# 生成普通假人
/bot spawn TestBot

# 查看所有假人
/bot list

# 将假人传送到你身边
/bot tp Notch

# 设置假人视角
/bot look Notch 90 0

# 移动假人
/bot move Notch 100 64 200

# 查看假人背包
/bot inv Notch

# 移除假人
/bot remove Notch
```

## 皮肤系统

插件会自动从Mojang API获取正版玩家皮肤：

1. 生成假人时，先查询该名称对应的正版UUID
2. 如果有正版UUID，获取对应的皮肤数据（texture和signature）
3. 将皮肤数据应用到假人的GameProfile
4. 假人显示时会使用正版玩家的皮肤

**注意**：
- 获取皮肤需要访问Mojang API，需要网络连接
- 如果名称不是正版玩家，假人会使用默认皮肤（Steve/Alex）
- 皮肤数据会被缓存，提高重复生成时的速度

## 技术实现

本插件使用NMS（Net Minecraft Server）反射技术创建真实的玩家实体：

1. 创建 `ServerPlayer` NMS实例
2. 分配唯一的 `GameProfile` 和 UUID（或正版UUID）
3. 从Mojang API获取皮肤并应用到GameProfile
4. 模拟网络连接，使服务器将其识别为真实玩家
5. 将玩家加入世界并启用区块加载

## 注意事项

- 假人会占用服务器玩家槽位
- 假人会像真实玩家一样消耗服务器资源
- 某些反作弊插件可能会误判假人
- 获取皮肤需要访问Mojang API（api.mojang.com和sessionserver.mojang.com）
- 如果Mojang API限流，皮肤获取可能会失败

## 支持的版本

- Paper 1.20.1
- 可能需要修改以支持其他版本（NMS类路径不同）

## 许可证

MIT License
