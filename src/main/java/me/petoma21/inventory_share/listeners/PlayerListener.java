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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final Inventory_Share plugin;

    public PlayerListener(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーがサーバーに参加したときのイベント
     * 他のサーバーで保存されたインベントリを非同期でロードし、メインスレッドで適用
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();

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

                player.sendMessage("§2[AIS] §aプレイヤーデータを同期中...");

                // 非同期でデータを読み込む
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    // データ格納用のマップ
                    final Map<String, Object> playerData = new HashMap<>();

                    try {
                        // インベントリデータを読み込み
                        playerData.put("inventory", plugin.getInventoryManager().loadPlayerInventoryData(playerUUID));

                        // エンダーチェストデータを読み込み（設定で有効な場合のみ）
                        if (plugin.getConfig().getBoolean("sync.enderchest", true)) {
                            playerData.put("enderchest", plugin.getEnderChestManager().loadPlayerEnderChestData(playerUUID));
                        }

                        // 所持金データを読み込み
                        if (plugin.getEconomyManager().isEconomyEnabled()) {
                            playerData.put("economy", plugin.getEconomyManager().loadPlayerBalanceData(playerUUID));
                        }

                        // メインスレッドに戻ってデータを適用
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) {
                                return; // プレイヤーがすでにオフラインの場合は処理しない
                            }

                            try {
                                // インベントリを適用
                                if (playerData.containsKey("inventory")) {
                                    plugin.getInventoryManager().applyInventoryToPlayer(player, playerData.get("inventory"));
                                }

                                // エンダーチェストを適用
                                if (playerData.containsKey("enderchest")) {
                                    plugin.getEnderChestManager().applyEnderChestToPlayer(player, playerData.get("enderchest"));
                                }

                                // 所持金を適用
                                if (playerData.containsKey("economy")) {
                                    plugin.getEconomyManager().applyBalanceToPlayer(player, playerData.get("economy"));
                                }

                                player.sendMessage("§2[AIS] §aデータ同期完了!");
                            } catch (Exception e) {
                                player.sendMessage("§4[AIS] §cデータ同期中にエラーが発生しました");
                                plugin.getLogger().warning("Error applying player data for " + player.getName() + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    } catch (Exception e) {
                        // エラーが発生した場合、メインスレッドでエラーメッセージを表示
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage("§4[AIS] §cデータ同期中にエラーが発生しました");
                            }
                            plugin.getLogger().warning("Error loading player data for " + player.getName() + ": " + e.getMessage());
                            e.printStackTrace();
                        });
                    }
                });
            }
        }.runTaskLater(plugin, 20L);
    }

    /**
     * プレイヤーがサーバーから退出したときのイベント
     * インベントリ状態を非同期で保存
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final String playerName = player.getName();
        final UUID playerUUID = player.getUniqueId();

        try {
            // インベントリデータを退出前に取得
            final ItemStack[] inventoryContents = player.getInventory().getContents().clone();
            final ItemStack[] armorContents = player.getInventory().getArmorContents().clone();
            final ItemStack offHandItem = player.getInventory().getItemInOffHand().clone();

            // エンダーチェストデータを退出前に取得
            final ItemStack[] enderChestContents = plugin.getConfig().getBoolean("sync.enderchest", true)
                    ? player.getEnderChest().getContents().clone() : null;

            // 所持金データを退出前に取得
            final double balance = plugin.getEconomyManager().isEconomyEnabled()
                    ? plugin.getEconomyManager().getPlayerBalance(player) : 0.0;

            // プレイヤーがサーバーから退出する時点でインベントリをクリア
            clearPlayerInventory(player);

            // 非同期でデータを保存
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // インベントリの保存
                    plugin.getInventoryManager().savePlayerInventoryData(playerUUID, inventoryContents, armorContents, offHandItem);

                    // エンダーチェストの保存（設定で有効な場合のみ）
                    if (plugin.getConfig().getBoolean("sync.enderchest", true) && enderChestContents != null) {
                        plugin.getEnderChestManager().savePlayerEnderChestData(playerUUID, enderChestContents);
                    }

                    // 所持金の保存
                    if (plugin.getEconomyManager().isEconomyEnabled()) {
                        plugin.getEconomyManager().savePlayerBalanceData(playerUUID, balance);
                    }

                    // ログに記録
                    plugin.getLogger().info(playerName + " player data save was successful!");
                } catch (Exception e) {
                    plugin.getLogger().warning("An error occurred while saving " + playerName + " player data.: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("An error occurred while preparing " + playerName + " player data for save: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * プレイヤーのインベントリとエンダーチェストをクリアする
     * 設定に基づいてエンダーチェストのクリアを制御
     */
    private void clearPlayerInventory(Player player) {
        // メインインベントリをクリア
        player.getInventory().clear();
        // 防具スロットをクリア
        player.getInventory().setArmorContents(new ItemStack[4]);
        // オフハンドをクリア
        player.getInventory().setItemInOffHand(null);
        // エンダーチェストをクリア（設定で有効な場合のみ）
        if (plugin.getConfig().getBoolean("sync.enderchest", true)) {
            player.getEnderChest().clear();
        }
        // インベントリの更新を強制
        player.updateInventory();
    }
}