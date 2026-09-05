package cl.nvcraft.lootshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu de la tienda.
 *
 * Implementa InventoryHolder para reconocer el GUI por identidad y no por el
 * titulo: comparar titulos se rompe en cuanto alguien los traduce o los cambia.
 */
public final class ShopGui implements InventoryHolder {

    private final NVLootShop plugin;
    private final Inventory inventory;
    private final Map<Integer, ShopItem> slots = new HashMap<>();

    public ShopGui(NVLootShop plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, plugin.getGuiRows() * 9,
                Text.of(plugin.getGuiTitle()));
        build();
    }

    private void build() {
        int slot = 0;

        for (ShopItem item : plugin.getItems()) {
            if (slot >= inventory.getSize()) break;

            // Se construye aqui y no al arrancar: ItemsAdder carga sus items de
            // forma asincrona y al inicio del servidor aun no estan listos.
            ItemStack display = ItemsAdderBridge.create(item.id(), 1);
            if (display == null) {
                plugin.getLogger().warning("Item desconocido en config.yml: " + item.id());
                continue;
            }

            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                lore.add(Text.of("&8" + item.id()));
                lore.add(Component.empty());
                if (item.canBuy()) {
                    lore.add(Text.of("&a▶ Click izq &7comprar 1 &8· &f$" + NVLootShop.money(item.buy())));
                    lore.add(Text.of("&a▶ Shift+izq &7comprar 64 &8· &f$" + NVLootShop.money(item.buy() * 64)));
                }
                if (item.canSell()) {
                    lore.add(Text.of("&c◀ Click der &7vender 1 &8· &f$" + NVLootShop.money(item.sell())));
                    lore.add(Text.of("&c◀ Shift+der &7vender todos"));
                }
                meta.lore(lore);
                display.setItemMeta(meta);
            }

            inventory.setItem(slot, display);
            slots.put(slot, item);
            slot++;
        }
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(NVLootShop plugin, Player player) {
        player.openInventory(new ShopGui(plugin).getInventory());
    }

    // ------------------------------------------------------------------

    /** Escucha los clicks dentro del menu. */
    public static final class GuiListener implements org.bukkit.event.Listener {

        private final NVLootShop plugin;

        public GuiListener(NVLootShop plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof ShopGui gui)) return;

            // Cancelar SIEMPRE y antes que nada: si alguna rama posterior
            // retornara sin cancelar, el menu se podria vaciar a mano.
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() != event.getInventory()) return;

            ShopItem item = gui.slots.get(event.getRawSlot());
            if (item == null) return;

            ClickType click = event.getClick();
            if (click == ClickType.LEFT) {
                plugin.buy(player, item, 1);
            } else if (click == ClickType.SHIFT_LEFT) {
                plugin.buy(player, item, 64);
            } else if (click == ClickType.RIGHT) {
                plugin.sell(player, item, 1);
            } else if (click == ClickType.SHIFT_RIGHT) {
                plugin.sell(player, item, Integer.MAX_VALUE);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder() instanceof ShopGui) {
                event.setCancelled(true);
            }
        }
    }
}
