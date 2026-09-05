package cl.nvcraft.lootshop;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class NVLootShop extends JavaPlugin {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private Economy economy;
    private final List<ShopItem> items = new ArrayList<>();
    private String guiTitle = "&8Mob Loot Shop";
    private int guiRows = 4;
    private boolean sellAllHook = true;
    private List<String> sellAllCommands = List.of("sellall");

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Falta Vault o un proveedor de economia. Desactivando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!ItemsAdderBridge.init(getLogger())) {
            getLogger().severe("No se pudo enlazar ItemsAdder. Desactivando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reloadShop();

        ShopCommand cmd = new ShopCommand(this);
        Objects.requireNonNull(getCommand("nvshop")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("nvshop")).setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new ShopGui.GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new SellAllHook(this), this);

        getLogger().info("Activado con " + items.size() + " items configurados."
                + (sellAllHook ? " Acoplado a /" + String.join(", /", sellAllCommands) + "." : ""));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    /** Relee config.yml y reconstruye la lista de items. */
    public void reloadShop() {
        reloadConfig();
        items.clear();
        guiTitle = getConfig().getString("gui.title", "&8Mob Loot Shop");
        guiRows  = Math.max(1, Math.min(6, getConfig().getInt("gui.rows", 4)));

        sellAllHook = getConfig().getBoolean("sellall-hook.enabled", true);
        sellAllCommands = getConfig().getStringList("sellall-hook.commands").stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        if (sellAllCommands.isEmpty()) sellAllCommands = List.of("sellall");

        for (Map<?, ?> entry : getConfig().getMapList("items")) {
            Object rawId = entry.get("id");
            if (rawId == null) continue;
            items.add(new ShopItem(
                    String.valueOf(rawId),
                    toDouble(entry.get("buy")),
                    toDouble(entry.get("sell"))));
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0D;
        }
    }

    // ------------------------------------------------------------------
    //  Compra
    // ------------------------------------------------------------------

    public void buy(Player player, ShopItem item, int amount) {
        if (!item.canBuy()) {
            player.sendMessage(msg("no-buy"));
            return;
        }

        ItemStack stack = ItemsAdderBridge.create(item.id(), amount);
        if (stack == null) {
            player.sendMessage(msg("unknown-item", "%item%", item.id()));
            return;
        }

        double cost = item.buy() * amount;
        if (economy.getBalance(player) < cost) {
            player.sendMessage(msg("no-money", "%price%", money(cost)));
            return;
        }

        // Cobrar ANTES de entregar: si el cobro falla, no se entrega nada.
        EconomyResponse response = economy.withdrawPlayer(player, cost);
        if (!response.transactionSuccess()) {
            player.sendMessage(msg("economy-error"));
            return;
        }

        // Lo que no quepa en el inventario cae al suelo; nunca se pierde.
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }

        player.sendMessage(msg("bought",
                "%amount%", String.valueOf(amount),
                "%item%", item.id(),
                "%price%", money(cost)));
    }

    // ------------------------------------------------------------------
    //  Venta
    // ------------------------------------------------------------------

    public void sell(Player player, ShopItem item, int max) {
        if (!item.canSell()) {
            player.sendMessage(msg("no-sell"));
            return;
        }

        // Se retiran los items PRIMERO y se paga solo por los que realmente se
        // pudieron retirar. Invertir este orden es como se duplica dinero.
        int removed = removeMatching(player, item.id(), max);
        if (removed <= 0) {
            player.sendMessage(msg("nothing-to-sell"));
            return;
        }

        double payout = item.sell() * removed;
        economy.depositPlayer(player, payout);

        player.sendMessage(msg("sold",
                "%amount%", String.valueOf(removed),
                "%item%", item.id(),
                "%price%", money(payout)));
    }

    /** Vende de una pasada todo el loot custom que traiga el jugador encima. */
    public void sellAll(Player player) {
        sellAll(player, false);
    }

    /**
     * @param quiet si es true, calla cuando no habia nada que vender. Lo usa el
     *              acople con /sellall: ahi el jugador puede estar vendiendo
     *              solo items vanilla y el aviso seria ruido en cada venta.
     */
    public void sellAll(Player player, boolean quiet) {
        double payout = 0D;
        int count = 0;

        for (ShopItem item : items) {
            if (!item.canSell()) continue;
            int removed = removeMatching(player, item.id(), Integer.MAX_VALUE);
            if (removed > 0) {
                payout += item.sell() * removed;
                count += removed;
            }
        }

        if (count == 0) {
            if (!quiet) player.sendMessage(msg("nothing-to-sell"));
            return;
        }

        economy.depositPlayer(player, payout);
        player.sendMessage(msg("sold-all",
                "%amount%", String.valueOf(count),
                "%price%", money(payout)));
    }

    /**
     * Retira hasta {@code max} unidades del item indicado y devuelve cuantas
     * retiro de verdad. Corre siempre en el hilo principal (click o comando),
     * asi que no hay condicion de carrera con el inventario.
     */
    private int removeMatching(Player player, String id, int max) {
        if (max <= 0) return 0;

        ItemStack[] contents = player.getInventory().getStorageContents();
        int removed = 0;

        for (int i = 0; i < contents.length && removed < max; i++) {
            ItemStack slot = contents[i];
            if (slot == null || slot.getType().isAir()) continue;
            if (!id.equals(ItemsAdderBridge.idOf(slot))) continue;

            int take = Math.min(slot.getAmount(), max - removed);
            if (take >= slot.getAmount()) {
                contents[i] = null;
            } else {
                slot.setAmount(slot.getAmount() - take);
            }
            removed += take;
        }

        if (removed > 0) player.getInventory().setStorageContents(contents);
        return removed;
    }

    // ------------------------------------------------------------------
    //  Utilidades
    // ------------------------------------------------------------------

    public Component msg(String key, String... replacements) {
        String raw = getConfig().getString("messages." + key, "&c[" + key + "]");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return Text.of(getConfig().getString("messages.prefix", "") + raw);
    }

    public static String money(double value) {
        return MONEY.format(value);
    }

    /**
     * Busca un item de la tienda por su id. Acepta tanto el id completo
     * ("mob_loots_expansion:wool_ball") como el corto ("wool_ball"), y no
     * distingue mayusculas: el jugador escribe /sellall WOOL_BALL igual que
     * escribiria /sellall ENDER_PEARL.
     *
     * @return el item, o null si no es nuestro (y entonces es de ESGUI).
     */
    public ShopItem findItem(String token) {
        String needle = token.toLowerCase(Locale.ROOT);
        for (ShopItem item : items) {
            String id = item.id().toLowerCase(Locale.ROOT);
            if (id.equals(needle)) return item;
            int colon = id.indexOf(':');
            if (colon >= 0 && id.substring(colon + 1).equals(needle)) return item;
        }
        return null;
    }

    public List<ShopItem> getItems() { return items; }

    public String getGuiTitle() { return guiTitle; }

    public int getGuiRows() { return guiRows; }

    public boolean isSellAllHookEnabled() { return sellAllHook; }

    public List<String> getSellAllCommands() { return sellAllCommands; }
}
