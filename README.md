# NVLootShop

Plugin para **NVcraft** (Purpur 1.21.11) que permite comprar y vender los items
custom de ItemsAdder — los 27 drops de `mob_loots_expansion` — usando la misma
economia que el resto del servidor.

Existe porque EconomyShopGUI **7.2.0 free** no soporta items de ItemsAdder: el
prefijo `ia:` y la clave `CustomModelData` son exclusivos de la version Premium.

## Que hace

| Comando | Efecto |
|---|---|
| `/nvshop` | Abre el menu de compra/venta |
| `/nvshop sellall` | Vende de golpe todo el loot custom del inventario |
| `/nvshop reload` | Recarga `config.yml` (requiere `nvlootshop.admin`) |

Alias: `/mobshop`, `/lootshop`

Dentro del menu:

- **Click izquierdo** — comprar 1
- **Shift + izquierdo** — comprar 64
- **Click derecho** — vender 1
- **Shift + derecho** — vender todos los que lleves

## Requisitos

- Java **21** (JDK, no solo el runtime)
- Maven 3.9+
- En el servidor: Vault + un proveedor de economia (aqui EssentialsX Economy) e ItemsAdder

## Compilar

```bash
cd nvcraft-shop
mvn clean package
```

El jar queda en `target/NVLootShop-1.0.0.jar`.

## Instalar

1. Copiar el jar a `plugins/`
2. Reiniciar el servidor (o `/reload` — mejor no, ver abajo)
3. Editar `plugins/NVLootShop/config.yml` si quieres retocar precios
4. `/nvshop reload`

> No uses `/reload confirm` en este servidor: con ProtocolLib, ItemsAdder y
> MythicMobs cargados deja listeners duplicados y estado corrupto.

## Decisiones de diseno

**ItemsAdder por reflexion.** `ItemsAdderBridge` llama a la API de ItemsAdder
con `Class.forName` en vez de compilar contra su jar. Cuesta unas lineas mas,
pero el plugin no queda atado a una version concreta de su API y compila sin
necesidad de resolver su artefacto en Maven.

**El menu se construye al abrirlo, no al arrancar.** ItemsAdder carga sus items
de forma asincrona; al arrancar el servidor todavia no existen y las texturas
saldrian vacias.

**Al vender se retira primero y se paga despues.** `removeMatching()` devuelve
cuantas unidades saco de verdad, y solo se paga por esas. El orden inverso —
pagar y luego intentar retirar — es exactamente como los plugins de tienda
caseros terminan duplicando dinero.

**El GUI se identifica por `InventoryHolder`, no por titulo.** Comparar titulos
se rompe en cuanto alguien traduce o cambia el nombre del menu.

**`InventoryClickEvent` se cancela al principio de todo**, antes de cualquier
rama que pueda retornar temprano. Si una sola ruta se escapara sin cancelar, el
menu se podria vaciar a mano.

## Precios

Calibrados contra la seccion `Mobs` de EconomyShopGUI para no desbalancear la
economia existente (ahi Gunpowder vende a 93.88 y Blaze Rod a 121.25, con una
relacion compra/venta de unas 32 veces):

| Nivel | Venta | Compra | Items |
|---|---|---|---|
| Comun | 20 | 640 | 8 |
| Poco comun | 45 | 1.440 | 8 |
| Raro | 110 | 3.520 | 9 |
| Epico | 450 | 14.400 | 2 |

Se ajustan en `config.yml` sin recompilar.

## Colaborar

Repo publico. Hace falta JDK 21 + Maven 3.9+.

```bash
git clone https://github.com/chrx3/nvcraft-shop.git
cd nvcraft-shop
mvn clean package
```

La guia de operacion del servidor (Crafty, API, despliegue) no va en este
repo: tiene datos internos del panel. Quien despliegue al servidor la recibe
aparte, como `AGENTS.md` en local.

Flujo habitual: rama → cambios → `mvn clean package` → PR o push a `main` →
subir el jar al servidor y reiniciar si hace falta.

## Añadir otro plugin

Hoy este repo es un solo artefacto Maven (`nvloot-shop`). Para un plugin
nuevo hay dos caminos, en este orden de preferencia:

1. **Modulo hermano en este mismo repo.** Convertir el POM raiz en `pom`
   padre y dejar `nvloot-shop/` como primer modulo. El segundo plugin vive
   en su propia carpeta (`nv-lo-que-sea/`) con su `pom.xml`. Compilas todo
   con `mvn -pl nv-lo-que-sea package` o el reactor entero.
2. **Repo aparte** (`nvcraft-<nombre>`) solo si el plugin no comparte nada
   con este (ni codigo ni ciclo de despliegue).

No copies clases de aqui a un plugin nuevo: extrae un modulo `nvcraft-common`
si hace falta compartir el puente de ItemsAdder, Vault o el formateo de texto.

## Limitaciones conocidas

- No hay precios dinamicos ni limites de stock. Si los necesitas, ESGUI Premium
  ya los trae.
- El menu es de una sola pagina: con `rows: 4` caben 36 items y hay 27.
  Pasando de 54 haria falta paginacion.
- Si ItemsAdder renombra `CustomStack`, el puente deja de enlazar y el plugin
  se desactiva solo con un mensaje claro en consola en vez de fallar a medias.
