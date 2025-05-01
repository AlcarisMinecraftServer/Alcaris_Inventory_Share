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
    public boolean savePlayerInventory(Player player) {
        final UUID playerUUID = player.getUniqueId();
        final String serverId = plugin.getPluginConfig().getServerId();
        final String playerName = player.getName();

        try {
            // データベース接続の確認
            if (!plugin.getDatabaseManager().ensureConnection()) {
                plugin.getLogger().warning(playerName + " のインベントリ保存に失敗: データベース接続を確立できません");
                return false;
            }

            // このサーバーが属するグループを取得
            List<String> groups = plugin.getPluginConfig().getServerGroups(serverId);

            if (groups.isEmpty()) {
                plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。インベントリは保存されません。");
                return false;
            }

            // プレイヤーのインベントリを取得
            ItemStack[] inventoryContents = player.getInventory().getContents();

            // 同期的に最初のグループに保存して結果を確認（重要なグループ）
            String primaryGroup = groups.get(0);
            plugin.getDatabaseManager().saveInventory(playerUUID, primaryGroup, inventoryContents);
            plugin.getLogger().info(playerName + " のインベントリをグループ " + primaryGroup + " に保存しました。");

            // 残りのグループには非同期で保存
            if (groups.size() > 1) {
                for (int i = 1; i < groups.size(); i++) {
                    final String group = groups.get(i);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            plugin.getDatabaseManager().saveInventory(playerUUID, group, inventoryContents);
                            plugin.getLogger().fine(playerName + " のインベントリをグループ " + group + " に保存しました。");
                        } catch (Exception e) {
                            plugin.getLogger().warning(playerName + " のインベントリをグループ " + group + " に保存中にエラー: " + e.getMessage());
                        }
                    });
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe(playerName + " のインベントリ保存中に重大なエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
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
        final String playerName = player.getName();

        try {
            // データベース接続の確認
            if (!plugin.getDatabaseManager().ensureConnection()) {
                plugin.getLogger().warning(playerName + " のインベントリ読み込みに失敗: データベース接続を確立できません");
                return false;
            }

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
                plugin.getLogger().info(playerName + " のインベントリデータがグループ " + primaryGroup + " に見つかりませんでした。");

                // 他のグループも試してみる
                for (int i = 1; i < groups.size(); i++) {
                    String fallbackGroup = groups.get(i);
                    ItemStack[] fallbackContents = plugin.getDatabaseManager().loadInventory(playerUUID, fallbackGroup);

                    if (fallbackContents != null) {
                        inventoryContents = fallbackContents;
                        plugin.getLogger().info(playerName + " のインベントリをフォールバックグループ " + fallbackGroup + " から取得しました。");
                        break;
                    }
                }

                // 全グループを試してもデータが見つからなかった
                if (inventoryContents == null) {
                    plugin.getLogger().warning(playerName + " のインベントリデータが全グループで見つかりませんでした。");
                    return false;
                }
            }

            // インベントリをプレイヤーに適用
            player.getInventory().setContents(inventoryContents);
            player.updateInventory();

            plugin.getLogger().info(playerName + " のインベントリをロードしました。");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe(playerName + " のインベントリ読み込み中に重大なエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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
        final String playerName = player.getName();

        try {
            // データベース接続の確認
            if (!plugin.getDatabaseManager().ensureConnection()) {
                plugin.getLogger().warning(playerName + " のインベントリ更新に失敗: データベース接続を確立できません");
                return false;
            }

            // グループからインベントリをロード
            ItemStack[] inventoryContents = plugin.getDatabaseManager().loadInventory(playerUUID, groupName);

            if (inventoryContents == null) {
                plugin.getLogger().warning(playerName + " のインベントリデータがグループ " + groupName + " に見つかりませんでした。");
                return false;
            }

            // インベントリをプレイヤーに適用
            player.getInventory().setContents(inventoryContents);
            player.updateInventory();

            plugin.getLogger().info(playerName + " のインベントリをグループ " + groupName + " から更新しました。");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe(playerName + " のインベントリ更新中に重大なエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * 全プレイヤーのインベントリを保存します
     */
    public boolean saveAllPlayerInventories() {
        boolean allSuccess = true;

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean success = savePlayerInventory(player);
            if (!success) {
                plugin.getLogger().warning(player.getName() + " のインベントリ保存に失敗しました。");
                allSuccess = false;
            }
        }

        return allSuccess;
    }
}