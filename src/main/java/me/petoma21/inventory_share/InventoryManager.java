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

    public void savePlayerInventory(Player player) {
        final UUID playerUUID = player.getUniqueId();
        final ItemStack[] inventoryContents = player.getInventory().getContents();
        final ItemStack[] armorContents = player.getInventory().getArmorContents();
        final ItemStack offHandItem = player.getInventory().getItemInOffHand();

        savePlayerInventoryData(playerUUID, inventoryContents, armorContents, offHandItem);
    }

    public void savePlayerInventoryData(UUID playerUUID, ItemStack[] inventoryContents, ItemStack[] armorContents, ItemStack offHandItem) {
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。インベントリは保存されません。");
            return;
        }

        // 各グループに対してインベントリを保存
        for (String group : groups) {
            plugin.getDatabaseManager().saveInventory(playerUUID, group, inventoryContents);
            plugin.getLogger().fine(playerUUID + " のインベントリをグループ " + group + " に保存しました。");

        }
    }

    public boolean loadPlayerInventory(Player player) {
        Object inventoryData = loadPlayerInventoryData(player.getUniqueId());
        if (inventoryData == null) {
            return false;
        }

        return applyInventoryToPlayer(player, inventoryData);
    }

    public Object loadPlayerInventoryData(UUID playerUUID) {
        final String serverId = plugin.getPluginConfig().getServerId();

        // このサーバーが属するグループを取得
        List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

        if (groups.isEmpty()) {
            plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。インベントリはロードされません。");
            return null;
        }

        // 最初のグループからインベントリをロード（複数グループの場合は最初のグループが優先）
        String primaryGroup = groups.get(0);
        ItemStack[] inventoryContents = plugin.getDatabaseManager().loadInventory(playerUUID, primaryGroup);

        if (inventoryContents == null) {
            // インベントリが見つからなかった場合
            plugin.getLogger().info(playerUUID + " のインベントリデータがグループ " + primaryGroup + " に見つかりませんでした。");
            return null;
        }

//        plugin.getLogger().info(playerUUID + " のインベントリをグループ " + primaryGroup + " からロードしました。");
        return inventoryContents;
    }

    public boolean applyInventoryToPlayer(Player player, Object inventoryData) {
        if (!(inventoryData instanceof ItemStack[])) {
            plugin.getLogger().warning("無効なインベントリデータ形式です: " + inventoryData.getClass().getName());
            return false;
        }

        try {
            // インベントリをプレイヤーに適用
            player.getInventory().setContents((ItemStack[]) inventoryData);
            player.updateInventory();

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "プレイヤー " + player.getName() + " のインベントリ適用中にエラーが発生しました: " + e.getMessage(), e);
            return false;
        }
    }

    private void sendSyncNotification(Player player) {
        FileConfiguration config = plugin.getConfig();
        String message = config.getString("messages.inventory-synchronized", "&aインベントリが他のサーバーと同期されました。");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

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

    public void saveAllPlayerInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerInventory(player);
        }
    }
}