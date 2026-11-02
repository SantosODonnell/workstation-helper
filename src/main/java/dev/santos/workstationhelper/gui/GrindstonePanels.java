package dev.santos.workstationhelper.gui;

import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Подсказка у точила, как у стола картографа: что умеет точило — снимает зачарования, чинит двумя предметами,
 * снимает зачарования с книг и не снимает проклятия. Мини-кнопка в правом верхнем углу окна прячет панель.
 */
public final class GrindstonePanels {
	private static final int PAD = 7;
	private static final String[] TITLES = {"workstationhelper.grind.disenchant", "workstationhelper.grind.repair",
		"workstationhelper.grind.books", "workstationhelper.grind.curses"};
	private static final String[] TEXTS = {"workstationhelper.grind.disenchant.text", "workstationhelper.grind.repair.text",
		"workstationhelper.grind.books.text", "workstationhelper.grind.curses.text"};

	private final GrindstoneScreen screen;
	private final Font font;
	private final ItemStack[] icons;
	private boolean fits;
	private int lx, py, pw, ph;

	private GrindstonePanels(GrindstoneScreen screen) {
		this.screen = screen;
		this.font = Minecraft.getInstance().font;
		// иконки строк: зачарованный железный меч, побитая золотая кирка, зачарованная книга, «проклятая» тыква
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		ItemStack pick = new ItemStack(Items.GOLDEN_PICKAXE);
		pick.setDamageValue(pick.getMaxDamage() * 2 / 3);
		ItemStack pumpkin = new ItemStack(Items.CARVED_PUMPKIN);
		pumpkin.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		icons = new ItemStack[] {sword, pick, new ItemStack(Items.ENCHANTED_BOOK), pumpkin};
	}

	public static void attach(GrindstoneScreen screen) {
		GrindstonePanels p = new GrindstonePanels(screen);
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
		return fits && Config.get().grindPanel;
	}

	private void layout() {
		int left = acc().workstationhelper$leftPos();
		pw = Math.min(150, left - 4);
		fits = pw >= 100;
		// по высоте — ровно под строки: описание переносится на сколько нужно строк
		ph = 20 + 3;
		for (int i = 0; i < TITLES.length; i++) ph += rowH(i);
		py = acc().workstationhelper$topPos();
		lx = left - pw - 2;
	}

	/** Ширина текста справа от иконки. */
	private int textW() {
		return pw - 2 * PAD - 22;
	}

	/** Высота строки: название и всё описание целиком, но не меньше ячейки с иконкой. */
	private int rowH(int i) {
		return Math.max(24, 12 + font.split(Component.translatable(TEXTS[i]), textW()).size() * 9 + 4);
	}

	/** Мини-кнопка «−/+» в правом верхнем углу окна точила. */
	private int toggleX() {
		return Vanilla.toggleRightX(acc().workstationhelper$leftPos(), acc().workstationhelper$imageWidth());
	}

	private boolean onToggle(double mx, double my) {
		int ty = Vanilla.toggleY(acc().workstationhelper$topPos());
		return mx >= toggleX() && mx < toggleX() + Vanilla.TOGGLE && my >= ty && my < ty + Vanilla.TOGGLE;
	}

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		if (fits) {
			boolean hover = onToggle(mx, my);
			Vanilla.toggle(g, toggleX(), Vanilla.toggleY(acc().workstationhelper$topPos()), hover, Config.get().grindPanel);
			if (hover) g.setTooltipForNextFrame(font, Component.translatable(Config.get().grindPanel ? "workstationhelper.carto.hide" : "workstationhelper.carto.show"), mx, my);
		}
		if (!shown()) return;
		Vanilla.panel(g, lx, py, pw, ph);
		g.text(font, Component.translatable("workstationhelper.carto.title"), lx + PAD, py + 7, Vanilla.TEXT, false);
		int textW = textW(), y = py + 20;
		for (int i = 0; i < TITLES.length; i++) {
			int x = lx + PAD;
			Vanilla.inset(g, x, y + 2, 18, 18, Vanilla.SLOT);
			g.item(icons[i], x + 1, y + 3);
			g.text(font, font.plainSubstrByWidth(Component.translatable(TITLES[i]).getString(), textW), x + 22, y + 2, Vanilla.TEXT, false);
			int ty = y + 12;
			for (var line : font.split(Component.translatable(TEXTS[i]), textW)) {
				g.text(font, line, x + 22, ty, 0xFF606060, false);
				ty += 9;
			}
			y += rowH(i);
		}
	}

	private boolean overPanel(double mx, double my) {
		return mx >= lx && my >= py && mx < lx + pw && my < py + ph;
	}

	private boolean click(double mx, double my) {
		if (fits && onToggle(mx, my)) {
			Config.get().grindPanel = !Config.get().grindPanel;
			Vanilla.clickSound();
			Config.save();
			return true;
		}
		// клики по самой панели не пропускаем в окно точила (иначе вещь в руке выпадет)
		return shown() && overPanel(mx, my);
	}
}
