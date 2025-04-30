package me.petoma21.inventory_share.listeners;

import me.petoma21.inventory_share.Inventory_Share;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerListener implements Listener {

    private final Inventory_Share plugin;

    public PlayerListener(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーがサーバーに参加したときのイベント
     * 他のサーバーで保存されたインベントリをロード
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // 最初にプレイヤーのインベントリをクリア（無限増殖バグ防止）
        clearPlayerInventory(player);

        // ログイン時はわずかに遅らせてインベントリを同期
        // 他のプラグインによるインベントリ操作の影響を避けるため
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }

                // 念のためプレイヤーのアクションを制限するメカニズムを追加
                player.sendMessage("§2[AIS] §aプレイヤーデータを同期中...");

                // インベントリのロード
                plugin.getInventoryManager().loadPlayerInventory(player);

                // エンダーチェストのロード
                plugin.getEnderChestManager().loadPlayerEnderChest(player);

                // 所持金のロード
                if (plugin.getEconomyManager().isEconomyEnabled()) {
                    plugin.getEconomyManager().loadPlayerBalance(player);
                }

                player.sendMessage("§2[AIS] §aデータ同期完了!");
            }
        }.runTaskLater(plugin, 20L);
    }

    /**
     * プレイヤーがサーバーから退出したときのイベント
     * インベントリ状態を保存し、保存完了後にインベントリをクリア
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final String playerName = player.getName();

        try {
            // インベントリの保存
            plugin.getInventoryManager().savePlayerInventory(player);

            // エンダーチェストの保存
            plugin.getEnderChestManager().savePlayerEnderChest(player);

            // 所持金の保存
            if (plugin.getEconomyManager().isEconomyEnabled()) {
                plugin.getEconomyManager().savePlayerBalance(player);
            }

            // プレイヤーがサーバーから退出する時点でインベントリをクリア
            clearPlayerInventory(player);

            // ログに記録
            plugin.getLogger().info(playerName + " player data save was successful!");

        } catch (Exception e) {
            plugin.getLogger().warning("An error occurred while saving " + playerName + " player data.: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * プレイヤーのインベントリとエンダーチェストをクリアする
     */
    private void clearPlayerInventory(Player player) {
        // メインインベントリをクリア
        player.getInventory().clear();

        // 防具スロットをクリア
        player.getInventory().setArmorContents(new ItemStack[4]);

        // オフハンドをクリア
        player.getInventory().setItemInOffHand(null);

        // エンダーチェストをクリア
        player.getEnderChest().clear();

        // インベントリの更新を強制
        player.updateInventory();
    }
}