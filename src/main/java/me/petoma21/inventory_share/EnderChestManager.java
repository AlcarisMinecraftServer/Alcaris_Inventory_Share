//package me.petoma21.inventory_share;
//
//public class EnderChestManager {
//}

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

    /**
     * プレイヤーのエンダーチェストを保存します
     * @param player 保存対象のプレイヤー
     */
    public void savePlayerEnderChest(Player player) {
        final UUID playerUUID = player.getUniqueId();
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

        // プレイヤーのエンダーチェストを取得
        ItemStack[] enderChestContents = player.getEnderChest().getContents();

        // 各グループに対してエンダーチェストを保存
        for (String group : groups) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabaseManager().saveEnderChest(playerUUID, group, enderChestContents);
                plugin.getLogger().fine(player.getName() + " のエンダーチェストをグループ " + group + " に保存しました。");
            });
        }
    }

    /**
     * プレイヤーのエンダーチェストをロードします
     * @param player ロード対象のプレイヤー
     * @return エンダーチェストが見つかってロードされた場合はtrue、それ以外はfalse
     */
    public boolean loadPlayerEnderChest(Player player) {
        final UUID playerUUID = player.getUniqueId();
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーのエンダーチェスト同期設定を取得
        Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
        if (!serverConfig.isSyncEnderChest()) {
            // このサーバーではエンダーチェスト同期が無効
            return false;
        }

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。エンダーチェストはロードされません。");
            return false;
        }

        // 最初のグループからエンダーチェストをロード（複数グループの場合は最初のグループが優先）
        String primaryGroup = groups.get(0);
        ItemStack[] enderChestContents = plugin.getDatabaseManager().loadEnderChest(playerUUID, primaryGroup);

        if (enderChestContents == null) {
            // エンダーチェストが見つからなかった場合
            plugin.getLogger().info(player.getName() + " のエンダーチェストデータがグループ " + primaryGroup + " に見つかりませんでした。");
            return false;
        }

        // エンダーチェストをプレイヤーに適用
        player.getEnderChest().setContents(enderChestContents);

        plugin.getLogger().info(player.getName() + " のエンダーチェストをグループ " + primaryGroup + " からロードしました。");
        return true;
    }

    /**
     * プレイヤーのエンダーチェストを特定のグループの最新データで更新します
     * @param player 更新対象のプレイヤー
     * @param groupName 更新元のグループ名
     * @return 更新成功時はtrue、失敗時はfalse
     */
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

    /**
     * 全プレイヤーのエンダーチェストを保存します
     */
    public void saveAllPlayerEnderChests() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerEnderChest(player);
        }
    }
}