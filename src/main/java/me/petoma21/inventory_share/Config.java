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
    private String dbType;
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
        dbType = config.getString("database.type", "mysql").toLowerCase();
        dbHost = config.getString("database.host", "localhost");
        dbPort = config.getInt("database.port", 3306);
        dbName = config.getString("database.name", "minecraft");
        dbUser = config.getString("database.user", "root");
        dbPassword = config.getString("database.password", "");
        dbUseSSL = config.getBoolean("database.useSSL", false);

        // データベースタイプの妥当性チェック
        if (!dbType.equals("mysql") && !dbType.equals("mariadb")) {
            plugin.getLogger().warning("無効なデータベースタイプが指定されました: " + dbType +
                    ". デフォルトのMySQLを使用します。");
            dbType = "mysql";
        }

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

        // 設定の読み込み完了ログ
        plugin.getLogger().info("設定を読み込みました: データベースタイプ=" + dbType +
                ", サーバーID=" + serverId);
    }

    // データベース設定取得メソッド
    public String getDbType() {
        return dbType;
    }

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

    /**
     * 設定の妥当性をチェックします
     * @return 設定が有効な場合はtrue、無効な場合はfalse
     */
    public boolean validateConfig() {
        List<String> errors = new ArrayList<>();

        // データベース設定のチェック
        if (dbHost == null || dbHost.trim().isEmpty()) {
            errors.add("データベースホストが設定されていません");
        }
        if (dbName == null || dbName.trim().isEmpty()) {
            errors.add("データベース名が設定されていません");
        }
        if (dbUser == null || dbUser.trim().isEmpty()) {
            errors.add("データベースユーザーが設定されていません");
        }
        if (dbPort < 1 || dbPort > 65535) {
            errors.add("無効なデータベースポート: " + dbPort);
        }

        // サーバーID設定のチェック
        if (serverId == null || serverId.trim().isEmpty()) {
            errors.add("サーバーIDが設定されていません");
        }

        // エラーがある場合はログに出力
        if (!errors.isEmpty()) {
            plugin.getLogger().severe("設定にエラーがあります:");
            for (String error : errors) {
                plugin.getLogger().severe("  - " + error);
            }
            return false;
        }

        return true;
    }

    /**
     * 設定情報を文字列として取得します（デバッグ用）
     * @return 設定情報の文字列
     */
    public String getConfigInfo() {
        return String.format(
                "Config Info:\n" +
                        "  Database Type: %s\n" +
                        "  Database Host: %s:%d\n" +
                        "  Database Name: %s\n" +
                        "  Database User: %s\n" +
                        "  Database SSL: %s\n" +
                        "  Server ID: %s\n" +
                        "  Sharing Groups: %d\n" +
                        "  Server Configs: %d",
                dbType, dbHost, dbPort, dbName, dbUser, dbUseSSL, serverId,
                sharingGroups.size(), serverConfigs.size()
        );
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

        @Override
        public String toString() {
            return "ServerConfig{syncEnderChest=" + syncEnderChest +
                    ", syncEconomy=" + syncEconomy + "}";
        }
    }
}