//package me.petoma21.inventory_share.listeners;
//
//public class InventoryListener {
//}

package me.petoma21.inventory_share.listeners;

import me.petoma21.inventory_share.Inventory_Share;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

public class InventoryListener implements Listener {

    private final Inventory_Share plugin;

    public InventoryListener(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーがインベントリを閉じたときのイベント
     * プレイヤーの通常インベントリが変更された場合は保存
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // プレイヤーインベントリが更新された場合に保存
        plugin.getInventoryManager().savePlayerInventory(player);
    }

    /**
     * プレイヤーがエンダーチェストを閉じたときのイベント
     * エンダーチェストの中身を保存
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnderChestClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // エンダーチェストの中身を保存
        plugin.getEnderChestManager().savePlayerEnderChest(player);
    }
}