package cl.nvcraft.lootshop;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Puente hacia la API de ItemsAdder mediante reflexion.
 *
 * Se usa reflexion a proposito: asi el plugin compila sin depender del jar de
 * ItemsAdder y sigue funcionando aunque ellos publiquen una version nueva de su
 * API, mientras no renombren estos cuatro metodos.
 */
public final class ItemsAdderBridge {

    private static boolean ready = false;
    private static Method byItemStack;
    private static Method getInstance;
    private static Method getNamespacedID;
    private static Method getItemStack;

    private ItemsAdderBridge() {}

    public static boolean init(Logger log) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            log.severe("ItemsAdder no esta instalado.");
            return false;
        }
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            byItemStack     = customStack.getMethod("byItemStack", ItemStack.class);
            getInstance     = customStack.getMethod("getInstance", String.class);
            getNamespacedID = customStack.getMethod("getNamespacedID");
            getItemStack    = customStack.getMethod("getItemStack");
            ready = true;
            return true;
        } catch (Throwable t) {
            log.log(Level.SEVERE, "No se pudo enlazar la API de ItemsAdder.", t);
            return false;
        }
    }

    public static boolean isReady() {
        return ready;
    }

    /** Id namespaced si el item es de ItemsAdder, o null si es vanilla/desconocido. */
    public static String idOf(ItemStack item) {
        if (!ready || item == null || item.getType().isAir()) return null;
        try {
            Object stack = byItemStack.invoke(null, item);
            if (stack == null) return null;
            return (String) getNamespacedID.invoke(stack);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Crea un ItemStack del item custom indicado.
     * Devuelve null si el id no existe o si ItemsAdder aun no termino de cargar.
     */
    public static ItemStack create(String namespacedId, int amount) {
        if (!ready) return null;
        try {
            Object stack = getInstance.invoke(null, namespacedId);
            if (stack == null) return null;
            ItemStack item = (ItemStack) getItemStack.invoke(stack);
            if (item == null) return null;
            item = item.clone();
            item.setAmount(Math.max(1, amount));
            return item;
        } catch (Throwable t) {
            return null;
        }
    }
}
