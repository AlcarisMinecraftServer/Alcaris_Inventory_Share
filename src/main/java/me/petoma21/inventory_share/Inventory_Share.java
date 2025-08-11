package me.petoma21.inventory_share;

import org.bukkit.plugin.java.JavaPlugin;
import me.petoma21.inventory_share.commands.ReloadCommand;
import me.petoma21.inventory_share.listeners.InventoryListener;
import me.petoma21.inventory_share.listeners.PlayerListener;

public class Inventory_Share extends JavaPlugin {

    private static Inventory_Share instance;
    private Config config;
    private DatabaseManager databaseManager;
    private InventoryManager inventoryManager;
    private EnderChestManager enderChestManager;
    private EconomyManager economyManager;
    private InventoryListener inventoryListener;

    @Override
    public void onEnable() {
        // インスタンスを保存
        instance = this;

        // 設定ファイルを読み込む
        saveDefaultConfig();
        config = new Config(this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        // データベース接続
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("===============================================");
            getLogger().severe("========== データベース接続に失敗しました ==========");
            getLogger().severe("==========　 サーバーの起動を停止します 　==========");
            getLogger().severe("===============================================");
            getServer().shutdown();
            return;
        }


        // 各マネージャーの初期化
        inventoryManager = new InventoryManager(this);
        enderChestManager = new EnderChestManager(this);
        economyManager = new EconomyManager(this);

        inventoryListener = new InventoryListener(this);
        getServer().getPluginManager().registerEvents(inventoryListener, this);

        // イベントリスナーの登録
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        // コマンドの登録
        getCommand("isreload").setExecutor(new ReloadCommand(this));

        getLogger().info("PetoInventoryShare プラグインが有効になりました。");
    }

    @Override
    public void onDisable() {
        // データベース接続を閉じる
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        if (inventoryListener != null) {
            inventoryListener.shutdown();
        }
        getLogger().info("PetoInventoryShare プラグインが無効になりました。");
    }

    public static Inventory_Share getInstance() {
        return instance;
    }

    public Config getPluginConfig() {
        return config;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public EnderChestManager getEnderChestManager() {
        return enderChestManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public void reload() {
        // コンフィグをリロード
        reloadConfig();
        config.reload();

        // 各マネージャーをリロード
        if (databaseManager != null) {
            databaseManager.reconnect();
        }

        getLogger().info("PIS の設定を再読み込みしました。");
    }

    @SuppressWarnings("unchecked")
    public <T> T getServerSpecificConfig(String path, T defaultValue) {
        String serverId = getConfig().getString("server-id", "");
        if (serverId.isEmpty()) {
            getLogger().warning("server-id が設定されていません。デフォルト値を使用します。");
            return defaultValue;
        }

        // サーバー固有の設定パスを構築
        String configPath = "servers." + serverId + "." + path;

        // グローバル設定のパス
        String globalPath = "sync." + path;

        // 優先順位：1. サーバー固有設定、2. グローバル設定、3. デフォルト値
        if (getConfig().isSet(configPath)) {
            Object value = getConfig().get(configPath);
            if (value != null && defaultValue != null && value.getClass().isInstance(defaultValue)) {
                return (T) value;
            } else if (value != null) {
                try {
                    // 数値型の変換を試みる
                    if (defaultValue instanceof Number) {
                        if (defaultValue instanceof Double) {
                            return (T) Double.valueOf(getConfig().getDouble(configPath));
                        } else if (defaultValue instanceof Float) {
                            return (T) Float.valueOf((float) getConfig().getDouble(configPath));
                        } else if (defaultValue instanceof Integer) {
                            return (T) Integer.valueOf(getConfig().getInt(configPath));
                        } else if (defaultValue instanceof Long) {
                            return (T) Long.valueOf(getConfig().getLong(configPath));
                        }
                    }
                    // 文字列型の場合
                    else if (defaultValue instanceof String) {
                        return (T) getConfig().getString(configPath);
                    }
                    // boolean型の場合
                    else if (defaultValue instanceof Boolean) {
                        return (T) Boolean.valueOf(getConfig().getBoolean(configPath));
                    }
                } catch (Exception e) {
                    getLogger().warning("設定値 " + configPath + " の型変換に失敗しました: " + e.getMessage());
                }
            }
        }

        // サーバー固有設定がない場合はグローバル設定を確認
        if (getConfig().isSet(globalPath)) {
            Object value = getConfig().get(globalPath);
            if (value != null && defaultValue != null && value.getClass().isInstance(defaultValue)) {
                return (T) value;
            } else if (value != null) {
                try {
                    // 数値型の変換を試みる
                    if (defaultValue instanceof Number) {
                        if (defaultValue instanceof Double) {
                            return (T) Double.valueOf(getConfig().getDouble(globalPath));
                        } else if (defaultValue instanceof Float) {
                            return (T) Float.valueOf((float) getConfig().getDouble(globalPath));
                        } else if (defaultValue instanceof Integer) {
                            return (T) Integer.valueOf(getConfig().getInt(globalPath));
                        } else if (defaultValue instanceof Long) {
                            return (T) Long.valueOf(getConfig().getLong(globalPath));
                        }
                    }
                    // 文字列型の場合
                    else if (defaultValue instanceof String) {
                        return (T) getConfig().getString(globalPath);
                    }
                    // boolean型の場合
                    else if (defaultValue instanceof Boolean) {
                        return (T) Boolean.valueOf(getConfig().getBoolean(globalPath));
                    }
                } catch (Exception e) {
                    getLogger().warning("設定値 " + globalPath + " の型変換に失敗しました: " + e.getMessage());
                }
            }
        }

        // どちらの設定も見つからない場合はデフォルト値を使用
        return defaultValue;
    }

    public boolean getServerSpecificConfig(String key, boolean defaultValue) {
        return getServerSpecificConfig(key, Boolean.valueOf(defaultValue));
    }
}