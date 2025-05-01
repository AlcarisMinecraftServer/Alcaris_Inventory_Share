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
     * @return 保存処理に成功した場合はtrue、それ以外はfalse
     */
    public boolean savePlayerEnderChest(Player player) {
        try {
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
                plugin.getLogger().warning("サーバー " + serverId + " は共有グループに属していません。エンダーチェストは保存されません。");
                return false;
            }

            // プレイヤーのエンダーチェストを取得
            ItemStack[] enderChestContents = player.getEnderChest().getContents();

            // 各グループに対してエンダーチェストを保存
            for (String group : groups) {
                final String groupName = group; // ラムダ式で使用するためのfinal変数
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        plugin.getDatabaseManager().saveEnderChest(playerUUID, groupName, enderChestContents);
                        plugin.getLogger().fine(player.getName() + " のエンダーチェストをグループ " + groupName + " に保存しました。");
                    } catch (Exception e) {
                        plugin.getLogger().severe("エンダーチェスト保存中にエラーが発生: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("エンダーチェスト保存中にエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * プレイヤーのエンダーチェストをロードします
     * @param player ロード対象のプレイヤー
     * @return エンダーチェストがロードされた場合はtrue、それ以外はfalse
     */
    public boolean loadPlayerEnderChest(Player player) {
        try {
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
        } catch (Exception e) {
            plugin.getLogger().severe("エンダーチェストロード中にエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * プレイヤーのエンダーチェストを特定のグループの最新データで更新します
     * @param player 更新対象のプレイヤー
     * @param groupName 更新元のグループ名
     * @return 更新成功時はtrue、失敗時はfalse
     */
    public boolean updatePlayerEnderChest(Player player, String groupName) {
        try {
            final UUID playerUUID = player.getUniqueId();
            final String serverId = plugin.getPluginConfig().getServerId();

            // このサーバーのエンダーチェスト同期設定を取得
            Config.ServerConfig serverConfig = plugin.getPluginConfig().getServerConfig(serverId);
            if (!serverConfig.isSyncEnderChest()) {
                // このサーバーではエンダーチェスト同期が無効
                return false;
            }

            // グループからエンダーチェストをロード
            ItemStack[] enderChestContents = plugin.getDatabaseManager().loadEnderChest(playerUUID, groupName);

            if (enderChestContents == null) {
                return false;
            }

            // エンダーチェストをプレイヤーに適用
            player.getEnderChest().setContents(enderChestContents);

            plugin.getLogger().info(player.getName() + " のエンダーチェストをグループ " + groupName + " から更新しました。");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("エンダーチェスト更新中にエラーが発生: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 全プレイヤーのエンダーチェストを保存します
     */
    public void saveAllPlayerEnderChests() {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                savePlayerEnderChest(player);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("全プレイヤーのエンダーチェスト保存中にエラーが発生: " + e.getMessage());
            e.printStackTrace();
        }
    }
}