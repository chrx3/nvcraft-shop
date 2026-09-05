package cl.nvcraft.lootshop;

/**
 * Una entrada de la tienda.
 *
 * @param id   id namespaced de ItemsAdder, p.ej. "mob_loots_expansion:wool_ball"
 * @param buy  precio de compra; <= 0 desactiva la compra
 * @param sell precio de venta;  <= 0 desactiva la venta
 */
public record ShopItem(String id, double buy, double sell) {

    public boolean canBuy()  { return buy  > 0; }
    public boolean canSell() { return sell > 0; }
}
