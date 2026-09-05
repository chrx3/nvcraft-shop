package cl.nvcraft.lootshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Acopla la venta de items custom a los comandos de venta de EconomyShopGUI.
 *
 * ESGUI no permite registrar items nuevos en su catalogo desde fuera: su API
 * solo consulta items que ya existen, y PreTransactionEvent ni siquiera se
 * dispara para un item que no reconoce. Asi que en vez de inyectarnos en su
 * transaccion, atendemos nosotros lo que a ella no le corresponde.
 *
 * Dos casos:
 *
 *   /sellall              -> ESGUI cobra lo vanilla y nosotros lo custom un
 *                            tick despues. El evento NO se cancela.
 *
 *   /sellall <item>       -> si el item es nuestro, cancelamos el evento y lo
 *                            vendemos aqui; asi ESGUI no llega a quejarse de
 *                            un material que no conoce. Si no es nuestro, no
 *                            tocamos nada y sigue su curso normal.
 */
public final class SellAllHook implements Listener {

    private final NVLootShop plugin;

    public SellAllHook(NVLootShop plugin) {
        this.plugin = plugin;
    }

    // HIGH y no MONITOR: en el caso por item necesitamos poder cancelar, y
    // MONITOR esta reservado para observar sin alterar el evento.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isSellAllHookEnabled()) return;

        String[] parts = event.getMessage().substring(1).trim().split("\\s+");
        if (parts.length < 1 || parts.length > 2) return;

        String command = parts[0].toLowerCase(Locale.ROOT);
        int colon = command.indexOf(':');            // p.ej. "economyshopgui:sellall"
        if (colon >= 0) command = command.substring(colon + 1);
        if (!plugin.getSellAllCommands().contains(command)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("nvlootshop.use")) return;

        if (parts.length == 1) {
            // Un tick despues, para que ESGUI cobre primero y los dos mensajes
            // salgan en orden en vez de pisarse.
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.sellAll(player, true));
            return;
        }

        ShopItem item = plugin.findItem(parts[1]);
        if (item == null) return;   // No es nuestro: que lo resuelva ESGUI.

        event.setCancelled(true);
        plugin.sell(player, item, Integer.MAX_VALUE);
    }

    /**
     * Ofrece los items custom al autocompletar "/sellall &lt;tab&gt;".
     *
     * Se usa TabCompleteEvent y no AsyncTabCompleteEvent a proposito: en el
     * asincrono, en cuanto un plugin deja sugerencias el servidor se salta las
     * del propio comando, y perderiamos toda la lista de materiales de ESGUI.
     * Aqui en cambio partimos de las suyas y les sumamos las nuestras.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onTabComplete(TabCompleteEvent event) {
        if (!plugin.isSellAllHookEnabled()) return;
        if (!(event.getSender() instanceof Player player)) return;
        if (!player.hasPermission("nvlootshop.use")) return;

        String buffer = event.getBuffer();
        if (!buffer.startsWith("/")) return;

        // El -1 conserva el token vacio de "/sellall ", para poder ofrecer la
        // lista completa en cuanto el jugador escribe el espacio.
        String[] parts = buffer.substring(1).split(" ", -1);
        if (parts.length != 2) return;

        String command = parts[0].toLowerCase(Locale.ROOT);
        int colon = command.indexOf(':');
        if (colon >= 0) command = command.substring(colon + 1);
        if (!plugin.getSellAllCommands().contains(command)) return;

        String prefix = parts[1].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>(event.getCompletions());

        for (ShopItem item : plugin.getItems()) {
            String id = item.id();
            String shortId = id.substring(id.indexOf(':') + 1);
            if (shortId.startsWith(prefix) && !completions.contains(shortId)) {
                completions.add(shortId);
            }
        }
        event.setCompletions(completions);
    }
}
