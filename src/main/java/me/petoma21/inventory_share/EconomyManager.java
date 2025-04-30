package me.petoma21.inventory_share;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class EconomyManager {

    private final Inventory_Share plugin;
    private Economy economy;
    private boolean economyEnabled = false;

    public EconomyManager(Inventory_Share plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    /**
     * Vaultを通じて経済プラグインを設定します
     */
    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault プラグインが見つかりません。経済機能は無効です。");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("経済プラグインが見つかりません。経済機能は無効です。");
            return;
        }

        economy = rsp.getProvider();
        economyEnabled = true;
        plugin.getLogger().info("Vault を通じて経済プラグインと連携しました: " + economy.getName());
    }

    /**
     * プレイヤーの所持金を保存します
     * @param player 保存対象のプレイヤー
     */
    public void savePlayerBalance(Player player) {
        if (!economyEnabled) {
            return;
        }

        final String playerName = player.getName();
        final UUID playerUUID = player.getUniqueId();
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーの経済同期設定を取得
        Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
        if (!serverConfig.isSyncEconomy()) {
            // このサーバーでは経済同期が無効
            return;
        }

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。経済データは保存されません。");
            return;
        }

        // プレイヤーの所持金を取得
        double balance = economy.getBalance(player);

        // 各グループに対して所持金を保存
        for (String group : groups) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabaseManager().saveEconomy(playerUUID, group, balance);
                plugin.getLogger().fine(playerName + " の所持金 " + balance + " をグループ " + group + " に保存しました。");
            });
        }
    }

    /**
     * プレイヤーの所持金をロードします
     * @param player ロード対象のプレイヤー
     * @return 所持金が見つかってロードされた場合はtrue、それ以外はfalse
     */
    public boolean loadPlayerBalance(Player player) {
        if (!economyEnabled) {
            return false;
        }

        final UUID playerUUID = player.getUniqueId();
        final String playerName = player.getName();
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーの経済同期設定を取得
        Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
        if (!serverConfig.isSyncEconomy()) {
            // このサーバーでは経済同期が無効
            return false;
        }

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。経済データはロードされません。");
            return false;
        }

        // 最初のグループから所持金をロード（複数グループの場合は最初のグループが優先）
        String primaryGroup = groups.get(0);
        double balance = plugin.getDatabaseManager().loadEconomy(playerUUID, primaryGroup);

        // 現在の所持金を取得
        double currentBalance = economy.getBalance(player);

        // 所持金を設定（差額を追加または削除）
        if (balance > currentBalance) {
            economy.depositPlayer(player, balance - currentBalance);
        } else if (balance < currentBalance) {
            economy.withdrawPlayer(player, currentBalance - balance);
        } else {
            // 残高が同じなら何もしない
            plugin.getLogger().fine(playerName + " の所持金は既に " + balance + " です。");
            return true;
        }

        plugin.getLogger().info(playerName + " の所持金 " + balance + " をグループ " + primaryGroup + " からロードしました。");
        return true;
    }

    /**
     * プレイヤーの所持金を特定のグループの最新データで更新します
     * @param player 更新対象のプレイヤー
     * @param groupName 更新元のグループ名
     * @return 更新成功時はtrue、失敗時はfalse
     */
    public boolean updatePlayerBalance(Player player, String groupName) {
        if (!economyEnabled) {
            return false;
        }

        final UUID playerUUID = player.getUniqueId();
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーの経済同期設定を取得
        Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
        if (!serverConfig.isSyncEconomy()) {
            // このサーバーでは経済同期が無効
            return false;
        }

        try {
            // グループから所持金をロード
            double balance = plugin.getDatabaseManager().loadEconomy(playerUUID, groupName);

            // 現在の所持金を取得
            double currentBalance = economy.getBalance(player);

            // 所持金を設定（差額を追加または削除）
            if (balance > currentBalance) {
                economy.depositPlayer(player, balance - currentBalance);
            } else if (balance < currentBalance) {
                economy.withdrawPlayer(player, currentBalance - balance);
            } else {
                // 残高が同じなら何もしない
                return true;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "プレイヤー " + player.getName() + " の所持金更新中にエラーが発生しました: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 全プレイヤーの所持金を保存します
     */
    public void saveAllPlayerBalances() {
        if (!economyEnabled) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerBalance(player);
        }
    }

    /**
     * 経済機能が有効か確認します
     * @return 経済機能が有効な場合はtrue
     */
    public boolean isEconomyEnabled() {
        return economyEnabled;
    }

    /**
     * 経済プラグイン名を取得します
     * @return 経済プラグイン名、無効な場合は"None"
     */
    public String getEconomyName() {
        return economyEnabled ? economy.getName() : "None";
    }
}