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

    public void savePlayerBalance(Player player) {
        if (!economyEnabled) {
            return;
        }

        double balance = getPlayerBalance(player);
        savePlayerBalanceData(player.getUniqueId(), balance);
    }

    public double getPlayerBalance(Player player) {
        if (!economyEnabled) {
            return 0.0;
        }
        return economy.getBalance(player);
    }

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

//        plugin.getLogger().info(playerUUID + " の所持金 " + balance + " をグループ " + primaryGroup + " からロードしました。");
        return balance;
    }

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
            Bukkit.getLogger().warning("[PIS] Failed to set player balance for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    public void saveAllPlayerBalances() {
        if (!economyEnabled) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerBalance(player);
        }
    }

    public boolean isEconomyEnabled() {
        return economyEnabled;
    }

    public String getEconomyName() {
        return economyEnabled ? economy.getName() : "None";
    }
}