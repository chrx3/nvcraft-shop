package cl.nvcraft.lootshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final NVLootShop plugin;

    public ShopCommand(NVLootShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("nvlootshop.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadShop();
            sender.sendMessage(plugin.msg("reloaded",
                    "%amount%", String.valueOf(plugin.getItems().size())));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un jugador puede usar este comando (salvo /" + label + " reload).");
            return true;
        }
        if (!player.hasPermission("nvlootshop.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("sellall")) {
            plugin.sellAll(player);
            return true;
        }

        ShopGui.open(plugin, player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("sellall".startsWith(prefix)) out.add("sellall");
            if ("reload".startsWith(prefix) && sender.hasPermission("nvlootshop.admin")) out.add("reload");
        }
        return out;
    }
}
