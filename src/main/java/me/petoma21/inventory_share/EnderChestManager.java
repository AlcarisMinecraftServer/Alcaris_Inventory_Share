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
        final ItemStack[] enderChestContents = player.getEnderChest().getContents();
        final UUID playerUUID = player.getUniqueId();

        savePlayerEnderChestData(playerUUID, enderChestContents);
    }

    /**
     * プレイヤーのエンダーチェストデータを保存します (非同期処理用)
     * @param playerUUID プレイヤーのUUID
     * @param enderChestContents エンダーチェストの内容
     */
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

    /**
     * プレイヤーのエンダーチェストをロードします
     * @param player ロード対象のプレイヤー
     * @return エンダーチェストが見つかってロードされた場合はtrue、それ以外はfalse
     */
    public boolean loadPlayerEnderChest(Player player) {
        Object enderChestData = loadPlayerEnderChestData(player.getUniqueId());
        if (enderChestData == null) {
            return false;
        }

        return applyEnderChestToPlayer(player, enderChestData);
    }

    /**
     * プレイヤーのエンダーチェストデータをロードします (非同期処理用)
     * @param playerUUID ロード対象のプレイヤーUUID
     * @return ロードされたエンダーチェストデータ、見つからない場合はnull
     */
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

        plugin.getLogger().info(playerUUID + " のエンダーチェストをグループ " + primaryGroup + " からロードしました。");
        return enderChestContents;
    }

    /**
     * ロードされたエンダーチェストデータをプレイヤーに適用します (非同期処理用)
     * @param player データを適用するプレイヤー
     * @param enderChestData ロードされたエンダーチェストデータ
     * @return 適用が成功した場合はtrue、失敗した場合はfalse
     */
    public boolean applyEnderChestToPlayer(Player player, Object enderChestData) {
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