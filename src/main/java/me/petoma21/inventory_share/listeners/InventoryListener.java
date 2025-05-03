package me.petoma21.inventory_share.listeners;

import me.petoma21.inventory_share.Inventory_Share;
import me.petoma21.inventory_share.listeners.PlayerListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class InventoryListener implements Listener {
    private final Inventory_Share plugin;
    private BukkitTask autoSaveTask;

    public InventoryListener(Inventory_Share plugin) {
        this.plugin = plugin;
        startAutoSaveTask();
    }

    /**
     * 5分ごとに全プレイヤーのインベントリとエンダーチェストを自動保存する非同期タスクを開始
     */
    private void startAutoSaveTask() {
        // 以前に実行されていたタスクがあればキャンセル
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        // 5分(6000 ticks)ごとに実行
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
//                plugin.getLogger().info("Executing automatic inventory save for all online players...");

                // 非同期で全プレイヤーのデータを保存
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Player player : plugin.getServer().getOnlinePlayers()) {
                            UUID playerUUID = player.getUniqueId();
                            String playerName = player.getName();

                            // 同期処理中であればデータの保存をスキップ
                            if (PlayerListener.syncingPlayers.contains(playerUUID)) {
                                plugin.getLogger().info(playerName + " is currently being synchronized. Skipping data save to prevent data loss.");
                                continue;
                            }

                            // プレイヤーインベントリの保存
                            plugin.getInventoryManager().savePlayerInventory(player);

                            // エンダーチェストの保存
                            plugin.getEnderChestManager().savePlayerEnderChest(player);
                        }
//                        plugin.getLogger().info("Automatic inventory save completed.");
                    }
                }.runTaskAsynchronously(plugin);
            }
        }.runTaskTimer(plugin, 6000, 6000); // 5分(6000 ticks)ごとに実行
    }

    /**
     * プラグイン無効化時にタスクをキャンセル
     */
    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
    }
}