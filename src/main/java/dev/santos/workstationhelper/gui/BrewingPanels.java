package dev.santos.workstationhelper.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Две панели у зельеварки. Слева — поиск и сетка зелий (как в творческом инвентаре, без прокрутки),
 * справа — шаги-кнопки выбранного зелья (клик кладёт нужный ингредиент в верхний слот) и кнопки-модификаторы:
 * время (редстоун), усиление (светокамень), взрывное (порох), оседающее (драконье дыхание).
 * Кнопка «?» в правой панели меняет её на схему модификаторов.
 * Мини-кнопка в правом верхнем углу окна прячет и показывает панели.
 */
public final class BrewingPanels {
	private static final int PAD = 7;
	private static final int CELL = 18;
	private static final int STEP_ROW = 18;
	private static final int MOD = 20;
	/** верхний слот зельеварки — ингредиент */
	private static final int INGREDIENT_SLOT = 3;
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");

	/** Шаг: ингредиент в верхний слот и зелье, которое после него получится. */
	private record Step(String item, String potion) {
	}

	/** Зелье: шаги от бутылки воды, его удлинённая и усиленная версии (null — такой нет). Взято из рецептов игры 26.3. */
	private record Brew(String id, List<Step> steps, String longId, String strongId) {
	}

	private static final String WART = "nether_wart", EYE = "fermented_spider_eye";

	private static final List<Brew> BREWS = List.of(
		brew("night_vision", "long_night_vision", null, WART, "awkward", "golden_carrot", "night_vision"),
		brew("invisibility", "long_invisibility", null, WART, "awkward", "golden_carrot", "night_vision", EYE, "invisibility"),
		brew("leaping", "long_leaping", "strong_leaping", WART, "awkward", "rabbit_foot", "leaping"),
		brew("fire_resistance", "long_fire_resistance", null, WART, "awkward", "magma_cream", "fire_resistance"),
		brew("swiftness", "long_swiftness", "strong_swiftness", WART, "awkward", "sugar", "swiftness"),
		brew("slowness", "long_slowness", "strong_slowness", WART, "awkward", "sugar", "swiftness", EYE, "slowness"),
		brew("turtle_master", "long_turtle_master", "strong_turtle_master", WART, "awkward", "turtle_helmet", "turtle_master"),
		brew("water_breathing", "long_water_breathing", null, WART, "awkward", "pufferfish", "water_breathing"),
		brew("healing", null, "strong_healing", WART, "awkward", "glistering_melon_slice", "healing"),
		brew("harming", null, "strong_harming", WART, "awkward", "glistering_melon_slice", "healing", EYE, "harming"),
		brew("poison", "long_poison", "strong_poison", WART, "awkward", "spider_eye", "poison"),
		brew("regeneration", "long_regeneration", "strong_regeneration", WART, "awkward", "ghast_tear", "regeneration"),
		brew("strength", "long_strength", "strong_strength", WART, "awkward", "blaze_powder", "strength"),
		brew("weakness", "long_weakness", null, EYE, "weakness"),
		brew("slow_falling", "long_slow_falling", null, WART, "awkward", "phantom_membrane", "slow_falling"),
		brew("wind_charged", null, null, WART, "awkward", "breeze_rod", "wind_charged"),
		brew("weaving", null, null, WART, "awkward", "cobweb", "weaving"),
		brew("oozing", null, null, WART, "awkward", "slime_block", "oozing"),
		brew("infested", null, null, WART, "awkward", "stone", "infested"));

	private static Brew brew(String id, String longId, String strongId, String... pairs) {
		List<Step> steps = new ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2) steps.add(new Step(pairs[i], pairs[i + 1]));
		return new Brew(id, steps, longId, strongId);
	}

	/** Модификатор: предмет и ключ подсказки. */
	private enum Mod {
		LONG("redstone", "workstationhelper.brew.mod.long"),
		STRONG("glowstone_dust", "workstationhelper.brew.mod.strong"),
		SPLASH("gunpowder", "workstationhelper.brew.mod.splash"),
		LINGERING("dragon_breath", "workstationhelper.brew.mod.lingering");

		final String item, key;

		Mod(String item, String key) {
			this.item = item;
			this.key = key;
		}
	}

	/** Выбранное зелье и модификаторы — помнятся, пока запущена игра. */
	private static String selectedId;
	private static final EnumSet<Mod> mods = EnumSet.noneOf(Mod.class);
	/** правая панель показывает схему модификаторов вместо шагов */
	private static boolean hintMode;

	private final BrewingStandScreen screen;
	private final Minecraft mc;
	private final Font font;
	private boolean fits;
	private int lx, rx, py, lpw, pw, ph, lph;
	private int gridTop, gridX, cols, rows;
	private final List<Brew> visible = new ArrayList<>();
	private EditBox search;

	private BrewingPanels(BrewingStandScreen screen) {
		this.screen = screen;
		this.mc = Minecraft.getInstance();
		this.font = mc.font;
	}

	public static void attach(BrewingStandScreen screen) {
		BrewingPanels p = new BrewingPanels(screen);
		p.layout();
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) -> p.extract(g, mx, my));
		// подсказка не запоминается: стол закрыли — в следующий раз откроется обычный вид
		ScreenEvents.remove(screen).register(s -> hintMode = false);
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !p.click(e.x(), e.y()));
		// отпускание кнопки над панелями тоже наше: иначе игра решит, что мышь «вне окна», и выбросит вещь из руки
		ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !(p.shown() && p.overPanels(e.x(), e.y())));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !p.scroll(x, y, v));
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !p.key(e));
	}

	private ContainerScreenAccessor acc() {
		return (ContainerScreenAccessor) screen;
	}

	private BrewingStandMenu menu() {
		return screen.getMenu();
	}

	private boolean shown() {
		return fits && Config.get().brewPanels;
	}

	private Brew selected() {
		for (Brew b : BREWS) {
			if (b.id().equals(selectedId)) return b;
		}
		return null;
	}

	// ======================== раскладка ========================

	private void layout() {
		int left = acc().workstationhelper$leftPos(), top = acc().workstationhelper$topPos();
		int iw = acc().workstationhelper$imageWidth();
		lpw = Math.min(140, left - 4);
		pw = Math.min(150, screen.width - left - iw - 4);
		fits = lpw >= 100 && pw >= 100;
		ph = acc().workstationhelper$imageHeight();
		py = top;
		lx = left - lpw - 2;
		rx = left + iw + 2;
		gridTop = py + 22;
		// сетка как в творческом инвентаре: все зелья сразу, недостающие в последнем ряду — пустые ячейки
		cols = Math.max(1, (lpw - 2 * PAD) / CELL);
		rows = (BREWS.size() + cols - 1) / cols;
		gridX = lx + (lpw - cols * CELL) / 2;
		lph = gridTop - py + rows * CELL + PAD;
		search = new EditBox(font, lx + PAD, py + 6, lpw - 2 * PAD, 13, Component.translatable("workstationhelper.loom.search"));
		search.setHint(Component.translatable("workstationhelper.loom.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setMaxLength(50);
		search.setResponder(t -> filter());
		Screens.getWidgets(screen).add(search);
		filter();
	}

	private void filter() {
		visible.clear();
		String q = search.getValue().trim().toLowerCase(Locale.ROOT);
		for (Brew b : BREWS) {
			if (q.isEmpty() || potion(Items.POTION, b.id()).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) visible.add(b);
		}
	}

	/** Мини-кнопка «−/+» в правом верхнем углу окна. */
	private int toggleX() {
		return Vanilla.toggleRightX(acc().workstationhelper$leftPos(), acc().workstationhelper$imageWidth());
	}

	private int toggleY() {
		return Vanilla.toggleY(acc().workstationhelper$topPos());
	}

	private boolean onToggle(double mx, double my) {
		return inside(mx, my, toggleX(), toggleY(), Vanilla.TOGGLE, Vanilla.TOGGLE);
	}

	// ======================== предметы ========================

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}

	private static ItemStack potion(Item bottle, String id) {
		return BuiltInRegistries.POTION.get(Identifier.withDefaultNamespace(id))
			.map(h -> PotionContents.createItemStack(bottle, h))
			.orElse(new ItemStack(bottle));
	}

	/** Можно ли включить модификатор у этого зелья (не у всех есть удлинённая или усиленная версия). */
	private static boolean allowed(Brew b, Mod m) {
		return switch (m) {
			case LONG -> b.longId() != null;
			case STRONG -> b.strongId() != null;
			default -> true;
		};
	}

	/** Все шаги выбранного зелья с включёнными модификаторами: ингредиент → зелье после него. */
	private List<Object[]> plan(Brew b) {
		List<Object[]> out = new ArrayList<>();
		for (Step s : b.steps()) out.add(new Object[] {item(s.item()), potion(Items.POTION, s.potion())});
		String id = b.id();
		if (mods.contains(Mod.LONG) && b.longId() != null) {
			id = b.longId();
			out.add(new Object[] {Items.REDSTONE, potion(Items.POTION, id)});
		}
		if (mods.contains(Mod.STRONG) && b.strongId() != null) {
			id = b.strongId();
			out.add(new Object[] {Items.GLOWSTONE_DUST, potion(Items.POTION, id)});
		}
		if (mods.contains(Mod.SPLASH)) out.add(new Object[] {Items.GUNPOWDER, potion(Items.SPLASH_POTION, id)});
		if (mods.contains(Mod.LINGERING)) out.add(new Object[] {Items.DRAGON_BREATH, potion(Items.LINGERING_POTION, id)});
		return out;
	}

	// ======================== отрисовка ========================

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		search.visible = shown();
		if (!search.visible) search.setFocused(false);
		extractToggle(g, mx, my);
		if (!shown()) return;
		extractLeft(g, mx, my);
		// правая панель — только когда выбрано зелье («Сброс» её убирает)
		Brew b = selected();
		if (b != null) extractRight(g, mx, my, b);
	}

	private void extractToggle(GuiGraphicsExtractor g, int mx, int my) {
		if (!fits) return;
		int x = toggleX(), y = toggleY();
		boolean hover = onToggle(mx, my);
		Vanilla.toggle(g, x, y, hover, Config.get().brewPanels);
		if (hover) g.setTooltipForNextFrame(font, Component.translatable(Config.get().brewPanels ? "workstationhelper.loom.hide" : "workstationhelper.loom.show"), mx, my);
	}

	private int cellX(int i) {
		return gridX + (i % cols) * CELL;
	}

	private int cellY(int i) {
		return gridTop + (i / cols) * CELL;
	}

	private void extractLeft(GuiGraphicsExtractor g, int mx, int my) {
		Vanilla.panel(g, lx, py, lpw, lph);
		ItemStack tip = null;
		for (int i = 0; i < rows * cols; i++) {
			int x = cellX(i), y = cellY(i);
			if (i >= visible.size()) {
				// пустая ячейка, чтобы сетка не обрывалась
				Vanilla.inset(g, x, y, CELL, CELL, Vanilla.SLOT);
				continue;
			}
			Brew b = visible.get(i);
			ItemStack stack = potion(Items.POTION, b.id());
			boolean hover = inside(mx, my, x, y, CELL, CELL);
			Vanilla.inset(g, x, y, CELL, CELL, b.id().equals(selectedId) ? 0xFFB0B0B0 : Vanilla.SLOT);
			g.item(stack, x + 1, y + 1);
			// выбранная ячейка — просто светлее фон, без рамки
			if (hover && !b.id().equals(selectedId)) g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x80FFFFFF);
			if (hover) tip = stack;
		}
		if (tip != null) g.setTooltipForNextFrame(font, tip.getHoverName(), mx, my);
	}

	/**
	 * Схема модификаторов в правой панели (по кнопке «?»):
	 * колба → светокамень → «II»; колба → редстоун → часы с «+»; колба → порох → взрывная; взрывная → драконье дыхание → оседающая.
	 */
	private void extractScheme(GuiGraphicsExtractor g, int mx, int my) {
		ItemStack water = potion(Items.POTION, "water"), splash = potion(Items.SPLASH_POTION, "water");
		Object[][] rows = {
			{water, new ItemStack(Items.GLOWSTONE_DUST), "II"},
			{water, new ItemStack(Items.REDSTONE), new ItemStack(Items.CLOCK)},
			{water, new ItemStack(Items.GUNPOWDER), splash},
			{splash, new ItemStack(Items.DRAGON_BREATH), potion(Items.LINGERING_POTION, "water")}};
		String[] results = {"workstationhelper.brew.mod.strong", "workstationhelper.brew.mod.long", "workstationhelper.brew.mod.splash", "workstationhelper.brew.mod.lingering"};
		String[] captions = {"workstationhelper.brew.cap.strong", "workstationhelper.brew.cap.long", "workstationhelper.brew.cap.splash", "workstationhelper.brew.cap.lingering"};
		int x0 = rx + PAD, rowH = CELL + 9;
		Component tip = null;
		for (int r = 0; r < rows.length; r++) {
			int y = stepsTop() + 2 + r * rowH;
			// подпись справа от ряда: что даёт этот предмет
			g.text(font, Component.translatable(captions[r]), x0 + 3 * CELL + 2 * 12 + 6, y + 5, Vanilla.TEXT, false);
			for (int c = 0; c < 3; c++) {
				int x = x0 + c * (CELL + 12);
				Vanilla.inset(g, x, y, CELL, CELL, Vanilla.SLOT);
				Object o = rows[r][c];
				if (o instanceof ItemStack s) g.item(s, x + 1, y + 1);
				else g.text(font, (String) o, x + (CELL - font.width((String) o)) / 2, y + 5, 0xFFFFFFFF, true);
				// часы с «+» — удлинённое
				if (r == 1 && c == 2) g.text(font, "+", x + CELL - 6, y + 1, 0xFF80FF80, true);
				if (c < 2) g.text(font, "→", x + CELL + 2, y + 5, Vanilla.TEXT, false);
				if (inside(mx, my, x, y, CELL, CELL)) {
					tip = c == 2 ? Component.translatable(results[r]) : o instanceof ItemStack s ? s.getHoverName() : null;
				}
			}
		}
		if (tip != null) g.setTooltipForNextFrame(font, tip, mx, my);
	}

	/** Высота правой панели: со схемой — ровно до нижнего ряда, с шагами — как окно зельеварки. */
	private int rph() {
		return hintMode ? stepsTop() + 2 + 3 * (CELL + 9) + CELL + PAD - py : ph;
	}

	private int hintX() {
		return rx + pw - PAD - 14;
	}

	private void extractHintButton(GuiGraphicsExtractor g, int mx, int my) {
		int x = hintX(), y = py + 4;
		boolean hover = inside(mx, my, x, y, 14, 14);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HOVER : BUTTON, x, y, 14, 14);
		g.text(font, "?", x + 5, y + 3, hintMode ? 0xFFFFFF55 : 0xFFFFFFFF, true);
		if (hover) g.setTooltipForNextFrame(font, Component.translatable(hintMode ? "workstationhelper.brew.hint_off" : "workstationhelper.loom.hint"), mx, my);
	}

	private int stepsTop() {
		return py + 19;
	}

	private int modY() {
		return py + ph - 5 - MOD;
	}

	/** Модификаторы стоят вплотную слева, справа от них — «Сброс» до края панели. */
	private int modX(int i) {
		return rx + PAD + i * (MOD + 2);
	}

	private int resetX() {
		return modX(4) + 2;
	}

	private int resetW() {
		return rx + pw - PAD - resetX();
	}

	private void extractRight(GuiGraphicsExtractor g, int mx, int my, Brew b) {
		Vanilla.panel(g, rx, py, pw, rph());
		g.text(font, Component.translatable(hintMode ? "workstationhelper.brew.mods" : "workstationhelper.brew.steps"), rx + PAD, py + 7, Vanilla.TEXT, false);
		extractHintButton(g, mx, my);
		if (hintMode) {
			extractScheme(g, mx, my);
			return;
		}
		List<Object[]> plan = plan(b);

		int rowW = pw - 2 * PAD, top = stepsTop();
		List<Component> tip = null;
		for (int i = 0; i < plan.size(); i++) {
			int y = top + i * STEP_ROW;
			Item ing = (Item) plan.get(i)[0];
			ItemStack after = (ItemStack) plan.get(i)[1];
			boolean hover = inside(mx, my, rx + PAD, y, rowW, STEP_ROW);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HOVER : BUTTON, rx + PAD, y, rowW, STEP_ROW);
			int x = rx + PAD + 4;
			g.text(font, (i + 1) + ".", x, y + 5, 0xFFE0E0E0, true);
			x += font.width((i + 1) + ".") + 3;
			ItemStack ingStack = new ItemStack(ing);
			g.item(ingStack, x, y + 1);
			int rx2 = rx + PAD + rowW - 20;
			g.text(font, "→", rx2 - 9, y + 5, 0xFFFFFFFF, true);
			g.item(after, rx2, y + 1);
			if (hover) {
				// лесенкой, как в подсказках наковальни: ингредиент → что получится
				tip = new ArrayList<>();
				tip.add(ingStack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
				tip.add(Component.literal("  → ").append(after.getHoverName()).withStyle(ChatFormatting.GRAY));
			}
		}

		// модификаторы: квадратные кнопки с предметами; включённая — зелёная, недоступная — серая
		Mod[] all = Mod.values();
		for (int i = 0; i < all.length; i++) {
			Mod m = all[i];
			int x = modX(i), y = modY();
			boolean ok = allowed(b, m), on = ok && mods.contains(m);
			boolean hover = inside(mx, my, x, y, MOD, MOD);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, hover && ok ? BUTTON_HOVER : BUTTON, x, y, MOD, MOD);
			if (on) {
				g.fill(x + 1, y + 1, x + MOD - 1, y + MOD - 1, 0x6055FF55);
				g.outline(x, y, MOD, MOD, 0xFF55FF55);
			}
			g.item(new ItemStack(item(m.item)), x + 2, y + 2);
			if (!ok) g.fill(x + 1, y + 1, x + MOD - 1, y + MOD - 1, 0xA0555555);
			if (hover) {
				tip = new ArrayList<>();
				tip.add(Component.translatable(m.key).withStyle(ChatFormatting.WHITE));
				if (!ok) tip.add(Component.translatable("workstationhelper.brew.mod.none").withStyle(ChatFormatting.GRAY));
			}
		}
		// «Сброс»: убрать выбор зелья и модификаторы
		int resX = resetX(), resY = modY();
		boolean overReset = inside(mx, my, resX, resY, resetW(), MOD);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, overReset ? BUTTON_HOVER : BUTTON, resX, resY, resetW(), MOD);
		Component reset = Component.translatable("workstationhelper.brew.reset");
		g.text(font, reset, resX + (resetW() - font.width(reset)) / 2, resY + 6, 0xFFFFFFFF, true);
		if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mx, my);
	}

	// ======================== клики ========================

	private boolean overPanels(double mx, double my) {
		return inside(mx, my, lx, py, lpw, lph) || selected() != null && inside(mx, my, rx, py, pw, rph());
	}

	private boolean click(double mx, double my) {
		// по полю поиска — пусть игра сама поставит в него курсор; клик в любое другое место убирает курсор
		if (search.visible && inside(mx, my, search.getX(), search.getY(), search.getWidth(), search.getHeight())) return false;
		search.setFocused(false);
		if (screen.getFocused() == search) screen.setFocused(null);
		if (fits && onToggle(mx, my)) {
			Config.get().brewPanels = !Config.get().brewPanels;
			Vanilla.clickSound();
			Config.save();
			return true;
		}
		if (!shown()) return false;
		if (inside(mx, my, lx, py, lpw, lph)) {
			for (int i = 0; i < visible.size(); i++) {
				if (inside(mx, my, cellX(i), cellY(i), CELL, CELL)) {
					selectedId = visible.get(i).id();
					hintMode = false;
					Vanilla.clickSound();
					break;
				}
			}
			return true;
		}
		Brew b = selected();
		if (b == null) return false;
		if (inside(mx, my, hintX(), py + 4, 14, 14)) {
			hintMode = !hintMode;
			Vanilla.clickSound();
			return true;
		}
		if (hintMode) return inside(mx, my, rx, py, pw, rph());
		if (inside(mx, my, resetX(), modY(), resetW(), MOD)) {
			selectedId = null;
			Vanilla.clickSound();
			mods.clear();
			search.setValue("");
			return true;
		}
		if (inside(mx, my, rx, py, pw, rph())) {
			Mod[] all = Mod.values();
			for (int i = 0; i < all.length; i++) {
				if (inside(mx, my, modX(i), modY(), MOD, MOD)) {
					Vanilla.clickSound();
					toggle(b, all[i]);
				}
			}
			List<Object[]> plan = plan(b);
			for (int i = 0; i < plan.size(); i++) {
				if (inside(mx, my, rx + PAD, stepsTop() + i * STEP_ROW, pw - 2 * PAD, STEP_ROW)) {
					Vanilla.clickSound();
					fill((Item) plan.get(i)[0]);
				}
			}
			return true;
		}
		return false;
	}

	/** Время и усиление взаимно исключают друг друга; оседающее бывает только из взрывного. */
	private void toggle(Brew b, Mod m) {
		if (!allowed(b, m)) return;
		if (mods.remove(m)) {
			if (m == Mod.SPLASH) mods.remove(Mod.LINGERING);
			return;
		}
		mods.add(m);
		if (m == Mod.LONG) mods.remove(Mod.STRONG);
		if (m == Mod.STRONG) mods.remove(Mod.LONG);
		if (m == Mod.LINGERING) mods.add(Mod.SPLASH);
	}

	private boolean scroll(double mx, double my, double v) {
		return shown() && overPanels(mx, my);
	}

	/** Пока курсор в поиске, клавиши — ему (иначе «E» закроет зельеварку, а цифры переложат вещи). */
	private boolean key(KeyEvent e) {
		if (!search.isFocused()) return false;
		if (e.key() == InputConstants.KEY_ESCAPE) {
			search.setFocused(false);
			screen.setFocused(null);
			return true;
		}
		search.keyPressed(e);
		return true;
	}

	// ======================== перекладывание ========================

	/** Кладёт 1 штуку ингредиента шага в верхний слот; то, что там лежало, — обратно в инвентарь. */
	private void fill(Item ing) {
		if (mc.gameMode == null || mc.player == null) return;
		BrewingStandMenu m = menu();
		Slot top = m.getSlot(INGREDIENT_SLOT);
		if (top.getItem().is(ing)) return;
		if (!m.getCarried().isEmpty()) {
			error(Component.translatable("workstationhelper.loom.carried"));
			return;
		}
		int from = -1;
		for (Slot s : m.slots) {
			if (s.container instanceof Inventory && s.getItem().is(ing)) {
				from = s.index;
				break;
			}
		}
		if (from < 0) {
			error(Component.translatable("workstationhelper.brew.missing", new ItemStack(ing).getHoverName()));
			return;
		}
		int id = m.containerId;
		if (!top.getItem().isEmpty()) {
			mc.gameMode.handleContainerInput(id, INGREDIENT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player);
			if (!top.getItem().isEmpty()) {
				error(Component.translatable("workstationhelper.brew.full"));
				return;
			}
		}
		// как мышкой: взять стопку, правым кликом положить 1 штуку, остаток вернуть в ту же ячейку
		mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, mc.player);
		mc.gameMode.handleContainerInput(id, INGREDIENT_SLOT, 1, ContainerInput.PICKUP, mc.player);
		if (!m.getCarried().isEmpty()) mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, mc.player);
	}

	private void error(Component text) {
		mc.gui.chatListener().handleOverlay(text.copy().withStyle(ChatFormatting.RED));
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 1.0f));
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
