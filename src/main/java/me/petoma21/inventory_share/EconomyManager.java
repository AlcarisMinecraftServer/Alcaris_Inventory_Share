package me.petoma21.inventory_share;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
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

        double balance = getPlayerBalance(player);
        savePlayerBalanceData(player.getUniqueId(), balance);
    }

    /**
     * プレイヤーの所持金を取得します
     * @param player 対象のプレイヤー
     * @return プレイヤーの所持金
     */
    public double getPlayerBalance(Player player) {
        if (!economyEnabled) {
            return 0.0;
        }
        return economy.getBalance(player);
    }

    /**
     * プレイヤーの所持金データを保存します (非同期処理用)
     * @param playerUUID プレイヤーのUUID
     * @param balance 所持金
     */
    public void savePlayerBalanceData(UUID playerUUID, double balance) {
        if (!economyEnabled) {
            return;
        }

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

        // 各グループに対して所持金を保存
        for (String group : groups) {
            plugin.getDatabaseManager().saveEconomy(playerUUID, group, balance);
            plugin.getLogger().fine(playerUUID + " の所持金 " + balance + " をグループ " + group + " に保存しました。");
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

        Object balanceData = loadPlayerBalanceData(player.getUniqueId());
        if (balanceData == null) {
            return false;
        }

        return applyBalanceToPlayer(player, balanceData);
    }

    /**
     * プレイヤーの所持金データをロードします (非同期処理用)
     * @param playerUUID ロード対象のプレイヤーUUID
     * @return ロードされた所持金データ (Double型)、見つからない場合はnull
     */
    public Object loadPlayerBalanceData(UUID playerUUID) {
        if (!economyEnabled) {
            return null;
        }

        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーの経済同期設定を取得
        Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
        if (!serverConfig.isSyncEconomy()) {
            // このサーバーでは経済同期が無効
            return null;
        }

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。経済データはロードされません。");
            return null;
        }

        // 最初のグループから所持金をロード（複数グループの場合は最初のグループが優先）
        String primaryGroup = groups.get(0);
        double balance = plugin.getDatabaseManager().loadEconomy(playerUUID, primaryGroup);

        plugin.getLogger().info(playerUUID + " の所持金 " + balance + " をグループ " + primaryGroup + " からロードしました。");
        return balance;
    }

    /**
     * ロードされた所持金データをプレイヤーに適用します (非同期処理用)
     * @param player データを適用するプレイヤー
     * @param balanceData ロードされた所持金データ (Double型)
     * @return 適用が成功した場合はtrue、失敗した場合はfalse
     */
    public boolean applyBalanceToPlayer(Player player, Object balanceData) {
        if (!economyEnabled) {
            return false;
        }

        if (!(balanceData instanceof Double)) {
            plugin.getLogger().warning("無効な所持金データ形式です: " + balanceData.getClass().getName());
            return false;
        }

        try {
            double balance = (Double) balanceData;
            // 現在の所持金を取得
            double currentBalance = economy.getBalance(player);

            // 所持金を設定（差額を追加または削除）
            if (balance > currentBalance) {
                economy.depositPlayer(player, balance - currentBalance);
            } else if (balance < currentBalance) {
                economy.withdrawPlayer(player, currentBalance - balance);
            } else {
                // 残高が同じなら何もしない
                plugin.getLogger().fine(player.getName() + " の所持金は既に " + balance + " です。");
                return true;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "プレイヤー " + player.getName() + " の所持金適用中にエラーが発生しました: " + e.getMessage(), e);
            return false;
        }
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
     * プレイヤーの所持金を直接設定する
     * @param player 対象プレイヤー
     * @param amount 設定する金額
     * @return 設定に成功したかどうか
     */
    public boolean setPlayerBalance(Player player, double amount) {
        if (!isEconomyEnabled() || player == null) {
            return false;
        }

        try {
            // 最小金額は0に制限
            double finalAmount = Math.max(0, amount);

            // Vaultを通じて所持金を設定
            EconomyResponse response = economy.withdrawPlayer(player, economy.getBalance(player));
            if (response.transactionSuccess()) {
                response = economy.depositPlayer(player, finalAmount);
                return response.transactionSuccess();
            }
            return false;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[InventoryShare] Failed to set player balance for " + player.getName() + ": " + e.getMessage());
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