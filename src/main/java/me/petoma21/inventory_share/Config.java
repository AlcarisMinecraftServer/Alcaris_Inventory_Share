package me.petoma21.inventory_share;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {

    private final Inventory_Share plugin;
    private FileConfiguration config;

    // データベース設定
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUser;
    private String dbPassword;
    private boolean dbUseSSL;

    // 現在のサーバーID
    private String serverId;

    // 共有グループ設定
    private final Map<String, List<String>> sharingGroups = new HashMap<>();

    // 各サーバーの設定
    private final Map<String, ServerConfig> serverConfigs = new HashMap<>();

    public Config(Inventory_Share plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadConfig();
    }

    private void loadConfig() {
        // データベース設定をロード
        dbHost = config.getString("database.host", "localhost");
        dbPort = config.getInt("database.port", 3306);
        dbName = config.getString("database.name", "minecraft");
        dbUser = config.getString("database.user", "root");
        dbPassword = config.getString("database.password", "");
        dbUseSSL = config.getBoolean("database.useSSL", false);

        // 現在のサーバーID
        serverId = config.getString("server-id", "server1");

        // 共有グループをロード
        sharingGroups.clear();
        ConfigurationSection groupsSection = config.getConfigurationSection("sharing-groups");
        if (groupsSection != null) {
            for (String groupName : groupsSection.getKeys(false)) {
                List<String> servers = groupsSection.getStringList(groupName);
                sharingGroups.put(groupName, servers);
            }
        }

        // 各サーバー設定をロード
        serverConfigs.clear();
        ConfigurationSection serversSection = config.getConfigurationSection("servers");
        if (serversSection != null) {
            for (String serverId : serversSection.getKeys(false)) {
                ConfigurationSection serverSection = serversSection.getConfigurationSection(serverId);
                if (serverSection != null) {
                    boolean syncEnderChest = serverSection.getBoolean("sync-enderchest", true);
                    boolean syncEconomy = serverSection.getBoolean("sync-economy", true);

                    ServerConfig serverConfig = new ServerConfig(syncEnderChest, syncEconomy);
                    serverConfigs.put(serverId, serverConfig);
                }
            }
        }
    }

    // データベース設定取得メソッド
    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public boolean isDbUseSSL() {
        return dbUseSSL;
    }

    // 現在のサーバーID取得
    public String getServerId() {
        return serverId;
    }

    // サーバーが属するグループ取得
    public List<String> getServerGroups(String serverId) {
        List<String> groups = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : sharingGroups.entrySet()) {
            if (entry.getValue().contains(serverId)) {
                groups.add(entry.getKey());
            }
        }

        return groups;
    }

    // 共有サーバーリスト取得
    public List<String> getSharedServers(String serverId) {
        List<String> sharedServers = new ArrayList<>();

        // サーバーが属するグループを取得
        List<String> groups = getServerGroups(serverId);

        // グループに属する他のサーバーを追加
        for (String group : groups) {
            List<String> servers = sharingGroups.get(group);
            if (servers != null) {
                for (String server : servers) {
                    if (!server.equals(serverId) && !sharedServers.contains(server)) {
                        sharedServers.add(server);
                    }
                }
            }
        }

        return sharedServers;
    }

    // サーバー設定取得
    public ServerConfig getServerConfig(String serverId) {
        return serverConfigs.getOrDefault(serverId, new ServerConfig(true, true));
    }

    // 現在のサーバーのエンダーチェスト同期設定取得
    public boolean isEnderChestEnabled() {
        return getServerConfig(serverId).isSyncEnderChest();
    }

    // 現在のサーバーの経済同期設定取得
    public boolean isEconomyEnabled() {
        return getServerConfig(serverId).isSyncEconomy();
    }

    // サーバー設定クラス
    public static class ServerConfig {
        private final boolean syncEnderChest;
        private final boolean syncEconomy;

        public ServerConfig(boolean syncEnderChest, boolean syncEconomy) {
            this.syncEnderChest = syncEnderChest;
            this.syncEconomy = syncEconomy;
        }

        public boolean isSyncEnderChest() {
            return syncEnderChest;
        }

        public boolean isSyncEconomy() {
            return syncEconomy;
        }
    }
}