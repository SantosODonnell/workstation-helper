package dev.santos.workstationhelper.gui;

import dev.santos.workstationhelper.mixin.RecipeBookComponentAccessor;
import dev.santos.workstationhelper.mixin.RecipeBookScreenAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Вкладка «Топливо» в книге рецептов печи, коптильни и плавильной печи — у нижнего края книги, отдельно от остальных.
 * Открытая вкладка показывает поверх книги все виды топлива (однотипные — в одной ячейке, сменяются, как в книге
 * верстака); при наведении — сколько предметов оно переплавит.
 */
public final class FuelTab {
	private static final int BOOK_W = 147, BOOK_H = 166, TAB_W = 35, TAB_H = 27, CELL = 25, PER_PAGE = 20;
	/** как часто в ячейке сменяется предмет из группы (как в книге верстака) */
	private static final long CYCLE_MS = 2000;
	private static final Identifier BOOK = Identifier.withDefaultNamespace("textures/gui/recipe_book.png");
	private static final Identifier TAB = Identifier.withDefaultNamespace("recipe_book/tab");
	private static final Identifier TAB_SELECTED = Identifier.withDefaultNamespace("recipe_book/tab_selected");
	private static final Identifier SLOT = Identifier.withDefaultNamespace("recipe_book/slot_craftable");
	private static final Identifier PAGE_FORWARD = Identifier.withDefaultNamespace("recipe_book/page_forward");
	private static final Identifier PAGE_FORWARD_HOVER = Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted");
	private static final Identifier PAGE_BACKWARD = Identifier.withDefaultNamespace("recipe_book/page_backward");
	private static final Identifier PAGE_BACKWARD_HOVER = Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted");

	/**
	 * Время горения обычной печи (в тиках) для каждого вида топлива — из данных игры 26.3
	 * (data/minecraft/context_int_provider/cooking/time_*.json, поле «left»). Один предмет плавится 200 тиков.
	 */
	private static final Map<String, Integer> BURN = Map.ofEntries(
		Map.entry("cooking/time_lava_bucket", 20000), Map.entry("cooking/time_coal_block", 16000),
		Map.entry("cooking/time_dried_kelp_block", 4001), Map.entry("cooking/time_blaze_rod", 2400),
		Map.entry("cooking/time_coal", 1600), Map.entry("cooking/time_boats", 1200),
		Map.entry("cooking/time_hanging_signs", 800), Map.entry("cooking/time_wood_blocks", 300),
		Map.entry("cooking/time_wood_items_small", 300), Map.entry("cooking/time_roots", 300),
		Map.entry("cooking/time_wood_items_large", 200), Map.entry("cooking/time_wood_slabs", 150),
		Map.entry("cooking/time_dry_plants", 100), Map.entry("cooking/time_wood_items_extra_small", 100),
		Map.entry("cooking/time_wool", 100), Map.entry("cooking/time_wool_carpets", 67),
		Map.entry("cooking/time_bamboo", 50), Map.entry("cooking/time_wool_slabs", 50));

	/** Приставки пород дерева и цветов: oak_planks и spruce_planks — одна ячейка «planks». */
	private static final String[] WOODS = {"dark_oak_", "pale_oak_", "oak_", "spruce_", "birch_", "jungle_", "acacia_", "mangrove_",
		"cherry_", "poplar_", "bamboo_", "crimson_", "warped_"};
	private static final String[] COLORS = {"light_blue_", "light_gray_", "white_", "orange_", "magenta_", "yellow_", "lime_", "pink_",
		"gray_", "cyan_", "purple_", "blue_", "brown_", "green_", "red_", "black_"};

	/** Группы по порядку показа (после ручного порядка FIRST). */
	private enum Kind { BEST, WOOD, WOOL, SMALL }

	/** Предметы, которые по виду относятся к другой группе: бамбуковый плот — к лодкам, азалия — к саженцам и т. п. */
	private static final Map<String, String> ALIAS = Map.ofEntries(
		Map.entry("raft", "boat"), Map.entry("chest_raft", "chest_boat"), Map.entry("mosaic", "planks"),
		Map.entry("block", "log"), Map.entry("stripped_block", "stripped_log"),
		Map.entry("mosaic_stairs", "stairs"), Map.entry("mosaic_slab", "slab"),
		Map.entry("propagule", "sapling"), Map.entry("azalea", "sapling"), Map.entry("flowering_azalea", "sapling"),
		Map.entry("dead_bush", "sapling"));

	/** Порядок первых ячеек — как попросил автор; остальные идут после, по группам. */
	private static final List<String> FIRST = List.of("lava_bucket", "coal_block", "dried_kelp_block", "blaze_rod", "coal", "charcoal",
		"boat", "chest_boat", "hanging_sign", "wood", "stripped_wood", "log", "stripped_log", "planks", "stairs", "shelf", "fence",
		"fence_gate", "trapdoor", "pressure_plate", "barrel", "crafting_table", "chest");

	/** Порядок пород при смене: у всех деревянных ячеек в один момент одна порода. */
	private static final String[] WOOD_ORDER = {"oak_", "spruce_", "birch_", "jungle_", "acacia_", "dark_oak_", "mangrove_", "cherry_",
		"pale_oak_", "poplar_", "bamboo_", "crimson_", "warped_"};

	/** Порода предмета по id (без «stripped_»), или null — не деревянный. */
	private static String woodOf(String id) {
		String rest = id.startsWith("stripped_") ? id.substring(9) : id;
		for (String w : WOODS) {
			if (rest.startsWith(w)) return w;
		}
		return null;
	}

	/** Ячейка: предметы группы, их породы (null — не дерево) и сколько тиков горит каждый. */
	private record Fuel(String key, List<ItemStack> stacks, List<String> woods, List<Integer> burns, Kind kind) {
		/** Какой предмет показать сейчас. */
		int index() {
			long tick = System.currentTimeMillis() / CYCLE_MS;
			// деревянная ячейка — порода как у всех; если такой нет (багровой лодки не бывает) — остаётся предыдущая
			if (!woods.contains(null)) {
				int now = (int) (tick % WOOD_ORDER.length);
				for (int back = 0; back < WOOD_ORDER.length; back++) {
					int i = woods.indexOf(WOOD_ORDER[Math.floorMod(now - back, WOOD_ORDER.length)]);
					if (i >= 0) return i;
				}
			}
			return (int) (tick % stacks.size());
		}

		int maxBurn() {
			return burns.stream().max(Integer::compare).orElse(0);
		}
	}

	/** Книга рецептов, над которой сейчас открыта вкладка «Топливо» (её подсказки и наведение гасит миксин). */
	private static Object covered;

	/** true — книга под нашим списком: ей нельзя «видеть» мышку и показывать подсказки. */
	public static boolean covers(Object book) {
		return book == covered;
	}

	private boolean active;
	private int page;

	private final AbstractFurnaceScreen<?> screen;
	private final Minecraft mc;
	private final Font font;
	private final List<Fuel> fuels = new ArrayList<>();

	private FuelTab(AbstractFurnaceScreen<?> screen) {
		this.screen = screen;
		this.mc = Minecraft.getInstance();
		this.font = mc.font;
		// однотипные предметы (разные породы, цвета) — в одну ячейку
		Map<String, Fuel> groups = new LinkedHashMap<>();
		for (Item item : BuiltInRegistries.ITEM) {
			CookingFuel fuel = item.components().get(DataComponents.COOKING_FUEL);
			if (fuel == null) continue;
			int burn = burnTime(fuel.burnTime());
			if (burn <= 0) continue;
			String id = BuiltInRegistries.ITEM.getKey(item).getPath();
			// обтёсанные брёвна и древесина — свои группы: «stripped_» остаётся, порода убирается
			String woodless = id.startsWith("stripped_") ? "stripped_" + strip(id.substring(9), WOODS) : strip(id, WOODS);
			String plain = strip(woodless, COLORS);
			String key = ALIAS.getOrDefault(plain, plain);
			Kind kind = burn >= 1600 ? Kind.BEST : !plain.equals(woodless) ? Kind.WOOL : burn <= 100 ? Kind.SMALL : Kind.WOOD;
			Fuel g = groups.computeIfAbsent(key, k -> new Fuel(k, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), kind));
			g.stacks().add(new ItemStack(item));
			g.woods().add(woodOf(id));
			g.burns().add(burn);
		}
		fuels.addAll(groups.values());
		// сначала ручной порядок, дальше по группам: лучшее → деревянное → шерсть → мелочь; внутри — от долгого к короткому
		fuels.sort(Comparator.comparingInt((Fuel f) -> FIRST.contains(f.key()) ? FIRST.indexOf(f.key()) : FIRST.size())
			.thenComparing(Fuel::kind).thenComparingInt(f -> -f.maxBurn())
			.thenComparing(f -> f.stacks().getFirst().getHoverName().getString()));
	}

	private static String strip(String id, String[] prefixes) {
		for (String p : prefixes) {
			if (id.startsWith(p)) return id.substring(p.length());
		}
		return id;
	}

	private static int burnTime(ResolvableInt r) {
		if (r instanceof ResolvableInt.Constant c) return c.value();
		if (r instanceof ResolvableInt.Reference ref) return BURN.getOrDefault(ref.key().identifier().getPath(), 0);
		return 0;
	}

	public static void attach(AbstractFurnaceScreen<?> screen) {
		FuelTab t = new FuelTab(screen);
		// после всего окна — чтобы список лёг поверх содержимого книги
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, a) -> t.extract(g, mx, my));
		ScreenEvents.remove(screen).register(s -> covered = null);
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !t.click(e.x(), e.y()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !t.scroll(x, y, v));
	}

	private RecipeBookComponent<?> book() {
		return ((RecipeBookScreenAccessor) screen).workstationhelper$recipeBook();
	}

	private RecipeBookComponentAccessor bookAcc() {
		return (RecipeBookComponentAccessor) book();
	}

	private int bookX() {
		return (bookAcc().workstationhelper$width() - BOOK_W) / 2 - bookAcc().workstationhelper$xOffset();
	}

	private int bookY() {
		return (bookAcc().workstationhelper$height() - BOOK_H) / 2;
	}

	/** Наша вкладка — у нижнего края книги, отдельно от вкладок игры. */
	private int tabY() {
		return bookY() + BOOK_H - TAB_H - 3;
	}

	private int tabX() {
		return bookX() - 30;
	}

	private int pages() {
		return Math.max(1, (fuels.size() + PER_PAGE - 1) / PER_PAGE);
	}

	/** Вкладка игры, которая была выбрана до «Топлива» — её вернём, когда «Топливо» закроют нашей вкладкой. */
	private net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton previous;

	private void setActive(boolean on) {
		active = on;
		covered = on ? book() : null;
		if (on) {
			// выбранная вкладка игры становится обычной (серой и меньше), пока открыто «Топливо»
			previous = bookAcc().workstationhelper$selectedTab();
			if (previous != null) previous.unselect();
		}
	}

	/** Закрыть «Топливо» своей вкладкой — вернуть выбор прежней вкладке игры. */
	private void closeRestoring() {
		setActive(false);
		if (previous != null) previous.select();
		previous = null;
	}

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		if (!book().isVisible()) {
			if (active) closeRestoring();
			return;
		}
		int tx = tabX(), ty = tabY();
		boolean overTab = inside(mx, my, tx, ty, TAB_W, TAB_H);
		if (!active) {
			// закрытая вкладка — под книгой, как вкладки игры
			drawTab(g, tx, ty, false);
			if (overTab) tooltipNow(g, List.of(Component.translatable("workstationhelper.fuel.tab")), mx, my);
			return;
		}

		// поверх содержимого книги: её же фон, заголовок, сетка топлива и листалка страниц
		int bx = bookX(), by = bookY();
		g.blit(RenderPipelines.GUI_TEXTURED, BOOK, bx, by, 1.0f, 1.0f, BOOK_W, BOOK_H, 256, 256);
		// открытая вкладка — поверх книги и чуть левее, как выбранная вкладка у верстака
		drawTab(g, tx, ty, true);
		g.fill(bx + 10, by + 10, bx + BOOK_W - 10, by + 28, 0xFFC6C6C6);
		Component title = Component.translatable("workstationhelper.fuel.title");
		g.text(font, title, bx + (BOOK_W - font.width(title)) / 2, by + 15, Vanilla.TEXT, false);
		page = Math.clamp(page, 0, pages() - 1);
		ItemStack tip = null;
		int tipBurn = 0;
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = page * PER_PAGE + i;
			if (idx >= fuels.size()) break;
			int cx = bx + 11 + CELL * (i % 5), cy = by + 31 + CELL * (i / 5);
			Fuel f = fuels.get(idx);
			int cur = f.index();
			ItemStack stack = f.stacks().get(cur);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, cx, cy, CELL, CELL);
			g.item(stack, cx + 4, cy + 4);
			if (inside(mx, my, cx, cy, CELL, CELL)) {
				tip = stack;
				tipBurn = f.burns().get(cur);
			}
		}
		if (pages() > 1) {
			String p = (page + 1) + "/" + pages();
			g.text(font, p, bx + 73 - font.width(p) / 2, by + 141, 0xFFFFFFFF, true);
			if (page > 0) {
				boolean h = inside(mx, my, bx + 38, by + 137, 12, 17);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, h ? PAGE_BACKWARD_HOVER : PAGE_BACKWARD, bx + 38, by + 137, 12, 17);
			}
			if (page < pages() - 1) {
				boolean h = inside(mx, my, bx + 93, by + 137, 12, 17);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, h ? PAGE_FORWARD_HOVER : PAGE_FORWARD, bx + 93, by + 137, 12, 17);
			}
		}
		if (tip != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(tip.getHoverName());
			lines.add(Component.translatable("workstationhelper.fuel.smelts", amount(tipBurn)).withStyle(ChatFormatting.GRAY));
			tooltipNow(g, lines, mx, my);
		} else if (overTab) {
			tooltipNow(g, List.of(Component.translatable("workstationhelper.fuel.tab")), mx, my);
		}
	}

	/** Подсказка сразу в этом кадре: мы рисуем после окна, и подсказка «на следующий кадр» уже не успевает. */
	private void tooltipNow(GuiGraphicsExtractor g, List<Component> lines, int mx, int my) {
		List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> parts = new ArrayList<>();
		for (Component c : lines) parts.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(c.getVisualOrderText()));
		g.tooltip(font, parts, mx, my, net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, null, false);
	}

	/** Вкладка как у книги игры: выбранная — на 2 пикселя левее и светлее; на ней ведро лавы. */
	private static void drawTab(GuiGraphicsExtractor g, int x, int y, boolean selected) {
		int sx = selected ? x - 2 : x;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, selected ? TAB_SELECTED : TAB, sx, y, TAB_W, TAB_H);
		g.item(new ItemStack(Items.LAVA_BUCKET), sx + 9, y + 5);
	}

	/** Сколько предметов переплавит: целое — как есть, дробное — до сотых (0,5 / 0,75 / 0,34). */
	private static String amount(int burn) {
		// блок сушёной ламинарии горит 4001 тик — это 20,005 предмета; показываем ровно 20
		if (burn == 4001) burn = 4000;
		if (burn % 200 == 0) return String.valueOf(burn / 200);
		String s = String.format(Locale.ROOT, "%.2f", burn / 200.0).replaceAll("0+$", "").replaceAll("\\.$", "");
		return Minecraft.getInstance().getLanguageManager().getSelected().startsWith("ru") ? s.replace('.', ',') : s;
	}

	private boolean click(double mx, double my) {
		if (!book().isVisible()) return false;
		if (inside(mx, my, tabX() - 2, tabY(), TAB_W + 2, TAB_H)) {
			if (active) closeRestoring();
			else setActive(true);
			Vanilla.clickSound();
			page = 0;
			return true;
		}
		if (!active) return false;
		int bx = bookX(), by = bookY();
		// клик по вкладке книги — вернуться к рецептам (саму вкладку обработает игра)
		if (mx >= tabX() && mx < bx && my >= by && my < tabY()) {
			closeRestoring();
			return false;
		}
		if (!inside(mx, my, bx, by, BOOK_W, BOOK_H)) return false;
		if (page > 0 && inside(mx, my, bx + 38, by + 137, 12, 17)) {
			page--;
			Vanilla.clickSound();
		} else if (page < pages() - 1 && inside(mx, my, bx + 93, by + 137, 12, 17)) {
			page++;
			Vanilla.clickSound();
		}
		return true;
	}

	private boolean scroll(double mx, double my, double v) {
		if (!active || !book().isVisible() || !inside(mx, my, bookX(), bookY(), BOOK_W, BOOK_H)) return false;
		page = Math.clamp(page - (int) Math.signum(v), 0, pages() - 1);
		return true;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
