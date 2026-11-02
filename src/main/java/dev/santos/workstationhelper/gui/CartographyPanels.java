package dev.santos.workstationhelper.gui;

import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Подсказка у стола картографа: что класть во второй слот (бумага, пустая карта, стеклянная панель) и что получится.
 * Мини-кнопка в правом верхнем углу окна стола прячет и показывает панель.
 */
public final class CartographyPanels {
	private static final int PAD = 7;
	private static final int ROW = 30;

	/** предмет во втором слоте → ключ подписи «что делает» */
	private static final Item[] ITEMS = {Items.PAPER, Items.MAP, Items.GLASS_PANE};
	private static final String[] WHAT = {"workstationhelper.carto.extend", "workstationhelper.carto.copy", "workstationhelper.carto.lock"};

	private final CartographyTableScreen screen;
	private final Font font;
	private boolean fits;
	private int lx, py, pw, ph;

	private CartographyPanels(CartographyTableScreen screen) {
		this.screen = screen;
		this.font = Minecraft.getInstance().font;
	}

	public static void attach(CartographyTableScreen screen) {
		CartographyPanels p = new CartographyPanels(screen);
		p.layout();
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) -> p.extract(g, mx, my));
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !p.click(e.x(), e.y()));
		// отпускание кнопки над панелью тоже наше: иначе игра решит, что мышь «вне окна», и выбросит вещь из руки
		ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !(p.shown() && p.overPanel(e.x(), e.y())));
	}

	private ContainerScreenAccessor acc() {
		return (ContainerScreenAccessor) screen;
	}

	private boolean shown() {
		return fits && Config.get().cartoPanel;
	}

	private void layout() {
		int left = acc().workstationhelper$leftPos();
		pw = Math.min(140, left - 4);
		fits = pw >= 100;
		// по высоте — ровно под строки с предметами (у последней описание в две строки)
		ph = 20 + ITEMS.length * ROW + 5;
		py = acc().workstationhelper$topPos();
		lx = left - pw - 2;
	}

	/** Мини-кнопка «−/+» в правом верхнем углу окна стола. */
	private int toggleX() {
		return Vanilla.toggleRightX(acc().workstationhelper$leftPos(), acc().workstationhelper$imageWidth());
	}

	private boolean onPlus(double mx, double my) {
		return mx >= toggleX() && mx < toggleX() + Vanilla.TOGGLE && my >= Vanilla.toggleY(acc().workstationhelper$topPos())
			&& my < Vanilla.toggleY(acc().workstationhelper$topPos()) + Vanilla.TOGGLE;
	}

	/** Ячейка с предметом в строке i панели. */
	private boolean onItem(int i, double mx, double my) {
		int x = lx + PAD, y = py + 20 + i * ROW + 2;
		return mx >= x && my >= y && mx < x + 18 && my < y + 18;
	}

	/**
	 * Творческий режим: 1 штука предмета сразу на курсор, как в творческом инвентаре. Предмет кладётся в свободный
	 * слот инвентаря (так же, как его выдаёт творческая вкладка) и тут же берётся оттуда мышкой.
	 */
	private void giveToCursor(Item item) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || !mc.player.hasInfiniteMaterials()) return;
		var menu = screen.getMenu();
		if (!menu.getCarried().isEmpty()) return;
		var inv = mc.player.getInventory();
		int free = inv.getFreeSlot();
		if (free < 0) return;
		ItemStack one = new ItemStack(item);
		inv.setItem(free, one);
		int invSlot = free < 9 ? net.minecraft.world.inventory.InventoryMenu.USE_ROW_SLOT_START + free : free;
		// серверу — отдельная копия: пакет уходит не сразу, а этот же предмет мы тут же берём на курсор (иначе кикает)
		mc.gameMode.handleCreativeModeItemAdd(one.copy(), invSlot);
		for (var slot : menu.slots) {
			if (slot.container == inv && slot.getContainerSlot() == free) {
				mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
				break;
			}
		}
	}

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		if (fits) {
			boolean hover = onPlus(mx, my);
			Vanilla.toggle(g, toggleX(), Vanilla.toggleY(acc().workstationhelper$topPos()), hover, Config.get().cartoPanel);
			if (hover) g.setTooltipForNextFrame(font, Component.translatable(Config.get().cartoPanel ? "workstationhelper.carto.hide" : "workstationhelper.carto.show"), mx, my);
		}
		if (!shown()) return;
		Vanilla.panel(g, lx, py, pw, ph);
		g.text(font, Component.translatable("workstationhelper.carto.title"), lx + PAD, py + 7, Vanilla.TEXT, false);

		// строки: ячейка с предметом, его название и что он делает во втором слоте
		int textW = pw - 2 * PAD - 22;
		for (int i = 0; i < ITEMS.length; i++) {
			int y = py + 20 + i * ROW, x = lx + PAD;
			ItemStack stack = new ItemStack(ITEMS[i]);
			Vanilla.inset(g, x, y + 2, 18, 18, Vanilla.SLOT);
			g.item(stack, x + 1, y + 3);
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && mc.player.hasInfiniteMaterials() && onItem(i, mx, my)) g.fill(x + 1, y + 3, x + 17, y + 19, 0x80FFFFFF);
			g.text(font, font.plainSubstrByWidth(stack.getHoverName().getString(), textW), x + 22, y + 2, Vanilla.TEXT, false);
			var lines = font.split(Component.translatable(WHAT[i]), textW);
			int ty = y + 12;
			for (int l = 0; l < Math.min(2, lines.size()); l++) {
				g.text(font, lines.get(l), x + 22, ty, 0xFF606060, false);
				ty += 9;
			}
		}

	}

	private boolean overPanel(double mx, double my) {
		return mx >= lx && my >= py && mx < lx + pw && my < py + ph;
	}

	private boolean click(double mx, double my) {
		if (fits && onPlus(mx, my)) {
			Config.get().cartoPanel = !Config.get().cartoPanel;
			Vanilla.clickSound();
			Config.save();
			return true;
		}
		if (!shown()) return false;
		for (int i = 0; i < ITEMS.length; i++) {
			if (onItem(i, mx, my)) {
				giveToCursor(ITEMS[i]);
				return true;
			}
		}
		// клики по самой панели не пропускаем в окно стола (иначе вещь в руке выпадет)
		return mx >= lx && my >= py && mx < lx + pw && my < py + ph;
	}
}
