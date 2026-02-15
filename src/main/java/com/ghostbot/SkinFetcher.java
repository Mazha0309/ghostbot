package com.ghostbot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkinFetcher {
    
    private static final String MOJANG_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft/profile/";
    
    // 缓存皮肤数据
    private static final Map<String, SkinData> skinCache = new HashMap<>();
    
    /**
     * 皮肤数据类
     */
    public static class SkinData {
        private final String uuid;
        private final String name;
        private final String texture;
        private final String signature;
        
        public SkinData(String uuid, String name, String texture, String signature) {
            this.uuid = uuid;
            this.name = name;
            this.texture = texture;
            this.signature = signature;
        }
        
        public String getUuid() { return uuid; }
        public String getName() { return name; }
        public String getTexture() { return texture; }
        public String getSignature() { return signature; }
        public boolean hasSkin() { return texture != null && !texture.isEmpty(); }
    }
    
    /**
     * 获取正版玩家皮肤
     * @param name 玩家名称
     * @return 皮肤数据
     */
    public static SkinData fetchSkin(String name) {
        // 检查缓存
        if (skinCache.containsKey(name.toLowerCase())) {
            return skinCache.get(name.toLowerCase());
        }
        
        try {
            // 1. 获取UUID
            String uuid = fetchUUID(name);
            if (uuid == null) {
                return null;
            }
            
            // 2. 获取皮肤数据
            SkinData skinData = fetchSkinByUUID(uuid, name);
            if (skinData != null && skinData.hasSkin()) {
                skinCache.put(name.toLowerCase(), skinData);
            }
            return skinData;
            
        } catch (Exception e) {
            BotPlugin.getInstance().getLogger().warning("获取玩家 " + name + " 的皮肤失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 通过名称获取UUID
     */
    private static String fetchUUID(String name) throws Exception {
        URL url = new URL(MOJANG_API + name);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode == 204) {
            // 玩家不存在
            return null;
        }
        if (responseCode != 200) {
            throw new Exception("HTTP " + responseCode);
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            return json.get("id").getAsString();
        }
    }
    
    /**
     * 通过UUID获取皮肤
     */
    private static SkinData fetchSkinByUUID(String uuid, String name) throws Exception {
        URL url = new URL(SESSION_SERVER + uuid + "?unsigned=false");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        if (connection.getResponseCode() != 200) {
            return new SkinData(uuid, name, null, null);
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            
            if (json.has("properties") && json.get("properties").isJsonArray()) {
                for (var element : json.getAsJsonArray("properties")) {
                    JsonObject property = element.getAsJsonObject();
                    if ("textures".equals(property.get("name").getAsString())) {
                        String texture = property.get("value").getAsString();
                        String signature = property.has("signature") ? 
                            property.get("signature").getAsString() : null;
                        return new SkinData(uuid, name, texture, signature);
                    }
                }
            }
            
            return new SkinData(uuid, name, null, null);
        }
    }
    
    /**
     * 清除缓存
     */
    public static void clearCache() {
        skinCache.clear();
    }
}
