package me.petoma21.inventory_share;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class EnderChestManager {

    private final Inventory_Share plugin;

    public EnderChestManager(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    public void savePlayerEnderChest(Player player) {
        final ItemStack[] enderChestContents = player.getEnderChest().getContents();
        final UUID playerUUID = player.getUniqueId();

        savePlayerEnderChestData(playerUUID, enderChestContents);
    }

    public void savePlayerEnderChestData(UUID playerUUID, ItemStack[] enderChestContents) {
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーのエンダーチェスト同期設定を取得
        Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
        if (!serverConfig.isSyncEnderChest()) {
            // このサーバーではエンダーチェスト同期が無効
            return;
        }

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。エンダーチェストは保存されません。");
            return;
        }

        // 各グループに対してエンダーチェストを保存
        for (String group : groups) {
            plugin.getDatabaseManager().saveEnderChest(playerUUID, group, enderChestContents);
            plugin.getLogger().fine(playerUUID + " のエンダーチェストをグループ " + group + " に保存しました。");
        }
    }

    public boolean loadPlayerEnderChest(Player player) {
        Object enderChestData = loadPlayerEnderChestData(player.getUniqueId());
        if (enderChestData == null) {
            return false;
        }

        return applyEnderChestToPlayer(player, enderChestData);
    }

    public Object loadPlayerEnderChestData(UUID playerUUID) {
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーのエンダーチェスト同期設定を取得
        Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
        if (!serverConfig.isSyncEnderChest()) {
            // このサーバーではエンダーチェスト同期が無効
            return null;
        }

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。エンダーチェストはロードされません。");
            return null;
        }

        // 最初のグループからエンダーチェストをロード（複数グループの場合は最初のグループが優先）
        String primaryGroup = groups.get(0);
        ItemStack[] enderChestContents = plugin.getDatabaseManager().loadEnderChest(playerUUID, primaryGroup);

        if (enderChestContents == null) {
            // エンダーチェストが見つからなかった場合
            plugin.getLogger().info(playerUUID + " のエンダーチェストデータがグループ " + primaryGroup + " に見つかりませんでした。");
            return null;
        }

//        plugin.getLogger().info(playerUUID + " のエンダーチェストをグループ " + primaryGroup + " からロードしました。");
        return enderChestContents;
    }

    public boolean applyEnderChestToPlayer(Player player, Object enderChestData) {
        // 最初にnullチェックを行う
        if (enderChestData == null) {
            plugin.getLogger().warning("エンダーチェストデータがnullです。プレイヤー: " + player.getName());
            return false;
        }

        if (!(enderChestData instanceof ItemStack[])) {
            plugin.getLogger().warning("無効なエンダーチェストデータ形式です: " + enderChestData.getClass().getName());
            return false;
        }

        try {
            // エンダーチェストをプレイヤーに適用
            player.getEnderChest().setContents((ItemStack[]) enderChestData);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "プレイヤー " + player.getName() + " のエンダーチェスト適用中にエラーが発生しました: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean updatePlayerEnderChest(Player player, String groupName) {
        final UUID playerUUID = player.getUniqueId();
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーのエンダーチェスト同期設定を取得
        Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
        if (!serverConfig.isSyncEnderChest()) {
            // このサーバーではエンダーチェスト同期が無効
            return false;
        }

        try {
            // グループからエンダーチェストをロード
            ItemStack[] enderChestContents = plugin.getDatabaseManager().loadEnderChest(playerUUID, groupName);

            if (enderChestContents == null) {
                return false;
            }

            // エンダーチェストをプレイヤーに適用
            player.getEnderChest().setContents(enderChestContents);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "プレイヤー " + player.getName() + " のエンダーチェスト更新中にエラーが発生しました: " + e.getMessage(), e);
            return false;
        }
    }

    public void saveAllPlayerEnderChests() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerEnderChest(player);
        }
    }
}