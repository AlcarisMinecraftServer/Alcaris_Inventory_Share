package me.petoma21.inventory_share.listeners;

import me.petoma21.inventory_share.Inventory_Share;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // ログイン時は少し遅らせてインベントリを同期
        // 他のプラグインによるインベントリクリアなどの影響を避けるため
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }

                // インベントリのロード
                boolean inventoryLoaded = plugin.getInventoryManager().loadPlayerInventory(player);

                // エンダーチェストのロード
                plugin.getEnderChestManager().loadPlayerEnderChest(player);

                // 所持金のロード
                if (plugin.getEconomyManager().isEconomyEnabled()) {
                    plugin.getEconomyManager().loadPlayerBalance(player);
                }
            }
        }.runTaskLater(plugin, 20L); // 1秒後に実行
    }

    /**
     * プレイヤーがサーバーから退出したときのイベント
     * インベントリ状態を保存
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // インベントリの保存
        plugin.getInventoryManager().savePlayerInventory(player);

        // エンダーチェストの保存
        plugin.getEnderChestManager().savePlayerEnderChest(player);

        // 所持金の保存
        if (plugin.getEconomyManager().isEconomyEnabled()) {
            plugin.getEconomyManager().savePlayerBalance(player);
        }
    }
}