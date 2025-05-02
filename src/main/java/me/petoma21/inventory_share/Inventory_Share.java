package me.petoma21.inventory_share;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
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

    @Override
    public void onEnable() {
        // インスタンスを保存
        instance = this;

        // 設定ファイルを読み込む
        saveDefaultConfig();
        config = new Config(this);

        // データベース接続
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("===============================================");
            getLogger().severe("========== データベース接続に失敗しました ==========");
            getLogger().severe("==========　 サーバーの軌道を停止します 　==========");
            getLogger().severe("===============================================");
            getServer().shutdown();
            return;
        }


        // 各マネージャーの初期化
        inventoryManager = new InventoryManager(this);
        enderChestManager = new EnderChestManager(this);
        economyManager = new EconomyManager(this);

        // イベントリスナーの登録
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        // コマンドの登録
        getCommand("isreload").setExecutor(new ReloadCommand(this));

        getLogger().info("InventoryShare プラグインが有効になりました。");
    }

    @Override
    public void onDisable() {
        // データベース接続を閉じる
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        getLogger().info("InventoryShare プラグインが無効になりました。");
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

        getLogger().info("InventoryShare の設定を再読み込みしました。");
    }
}