//package me.petoma21.inventory_share;
//
//public class InventoryManager {
//}

package me.petoma21.inventory_share;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class InventoryManager {

    private final Inventory_Share plugin;

    public InventoryManager(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーのインベントリを保存します
     * @param player 保存対象のプレイヤー
     */
    public void savePlayerInventory(Player player) {
        final UUID playerUUID = player.getUniqueId();
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。インベントリは保存されません。");
            return;
        }

        // プレイヤーのインベントリを取得
        ItemStack[] inventoryContents = player.getInventory().getContents();

        // 各グループに対してインベントリを保存
        for (String group : groups) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabaseManager().saveInventory(playerUUID, group, inventoryContents);
                plugin.getLogger().fine(player.getName() + " のインベントリをグループ " + group + " に保存しました。");
            });
        }
    }

    /**
     * プレイヤーのインベントリをロードします
     * @param player ロード対象のプレイヤー
     * @return インベントリが見つかってロードされた場合はtrue、それ以外はfalse
     */
    public boolean loadPlayerInventory(Player player) {
        final UUID playerUUID = player.getUniqueId();
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。インベントリはロードされません。");
            return false;
        }

        // 最初のグループからインベントリをロード（複数グループの場合は最初のグループが優先）
        String primaryGroup = groups.get(0);
        ItemStack[] inventoryContents = plugin.getDatabaseManager().loadInventory(playerUUID, primaryGroup);

        if (inventoryContents == null) {
            // インベントリが見つからなかった場合
            plugin.getLogger().info(player.getName() + " のインベントリデータがグループ " + primaryGroup + " に見つかりませんでした。");
            return false;
        }

        // インベントリをプレイヤーに適用
        player.getInventory().setContents(inventoryContents);
        player.updateInventory();

        // インベントリ同期メッセージを表示 ←二重に送られるので無効化中w
//        sendSyncNotification(player);

        plugin.getLogger().info(player.getName() + " のインベントリをグループ " + primaryGroup + " からロードしました。");
        return true;
    }

    /**
     * プレイヤーにインベントリ同期通知を送信します
     * @param player 通知を送るプレイヤー
     */
    private void sendSyncNotification(Player player) {
        FileConfiguration config = plugin.getConfig();
        String message = config.getString("messages.inventory-synchronized", "&aインベントリが他のサーバーと同期されました。");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * プレイヤーのインベントリを特定のグループの最新データで更新します
     * @param player 更新対象のプレイヤー
     * @param groupName 更新元のグループ名
     * @return 更新成功時はtrue、失敗時はfalse
     */
    public boolean updatePlayerInventory(Player player, String groupName) {
        final UUID playerUUID = player.getUniqueId();

        try {
            // グループからインベントリをロード
            ItemStack[] inventoryContents = plugin.getDatabaseManager().loadInventory(playerUUID, groupName);

            if (inventoryContents == null) {
                return false;
            }

            // インベントリをプレイヤーに適用
            player.getInventory().setContents(inventoryContents);
            player.updateInventory();

            // インベントリ同期メッセージを表示 同様停止
//            sendSyncNotification(player);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "プレイヤー " + player.getName() + " のインベントリ更新中にエラーが発生しました: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 全プレイヤーのインベントリを保存します
     */
    public void saveAllPlayerInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerInventory(player);
        }
    }
}