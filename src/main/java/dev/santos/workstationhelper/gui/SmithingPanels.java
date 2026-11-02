package dev.santos.workstationhelper.gui;

import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Панели у кузнечного стола. Слева — «Предпросмотр»: медленно крутящийся манекен в собранном наряде.
 * Справа — «Кастомизатор»: шлем/нагрудник/поножи/ботинки → материал → цвет (для кожи) → отделка → цвет отделки.
 * В выживании кнопка «?» показывает, где добыть шаблоны отделки. Мини-кнопка в правом верхнем углу окна прячет панели.
 */
public final class SmithingPanels {
	private static final int PAD = 7;
	private static final int CELL = 18;
	private static final int SWATCH = 12;
	private static final int DONE_FRAME = 0xFF008000;
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/villager/scroller");
	private static final Identifier SCROLLER_OFF = Identifier.withDefaultNamespace("container/villager/scroller_disabled");

	private static final String[] SLOTS = {"head", "chest", "legs", "feet"};
	private static final String[] SUFFIX = {"helmet", "chestplate", "leggings", "boots"};
	/** материалы брони по порядку; черепаший — только шлем */
	private static final String[] MATERIALS = {"leather", "chainmail", "copper", "iron", "golden", "diamond", "netherite", "turtle"};
	/** чем красится отделка — какой предмет показывать на кнопке */
	private static final Map<String, String> TRIM_ITEMS = Map.ofEntries(
		Map.entry("quartz", "quartz"), Map.entry("iron", "iron_ingot"), Map.entry("gold", "gold_ingot"),
		Map.entry("copper", "copper_ingot"), Map.entry("netherite", "netherite_ingot"), Map.entry("redstone", "redstone"),
		Map.entry("emerald", "emerald"), Map.entry("diamond", "diamond"), Map.entry("lapis", "lapis_lazuli"),
		Map.entry("amethyst", "amethyst_shard"), Map.entry("resin", "resin_brick"));
	/** «цвет» кнопки «без краски» — вместо заливки рисуется значок как у барьера */
	private static final int NO_DYE = 0;
	/** значок «без краски» 10×10: красный перечёркнутый круг */
	private static final String[] NO_DYE_ICON = {
		"...####...", "..######..", ".###...##.", "#####...##", "##.###..##",
		"##..###.##", "##...#####", ".##...###.", "..######..", "...####..."};

	private static final Vector3f TRANSLATION = new Vector3f(0, 1, 0);
	/** контуры пустых слотов брони, как в инвентаре персонажа */
	private static final Identifier[] EMPTY_SLOT = {
		Identifier.withDefaultNamespace("container/slot/helmet"), Identifier.withDefaultNamespace("container/slot/chestplate"),
		Identifier.withDefaultNamespace("container/slot/leggings"), Identifier.withDefaultNamespace("container/slot/boots")};

	/** Поворот манекена: yaw — вокруг себя (градусы), pitch — наклон к зрителю (радианы). Сам крутится, мышкой — куда хочешь. */
	private static float yaw;
	private static float pitch = 0.2f;
	/** манекен сам вращается; кнопка пуск/пауза в углу окошка */
	private static boolean spinning = true;
	private long lastFrame;
	private boolean dragging;
	/** окошко предпросмотра (для захвата мышкой) */
	private int boxX, boxY, boxW, boxH;

	/** открытая вкладка (0 — шлем … 3 — ботинки); помнится, пока запущена игра */
	private static int tab;
	/** правая панель показывает подсказку «где добыть шаблоны отделки» (только в выживании) */
	private static boolean hintMode;
	private int hintScroll;

	/** Шаблон отделки и где его найти (сверено с таблицами добычи игры 26.3). */
	private record TrimHint(String pattern, String where) {
	}

	private static final String NETHERITE = "netherite_upgrade";

	private static final List<TrimHint> TRIM_HINTS = List.of(
		new TrimHint("sentry", "outpost"), new TrimHint("dune", "desert"), new TrimHint("coast", "shipwreck"),
		new TrimHint("wild", "jungle"), new TrimHint("tide", "elder"), new TrimHint("vex", "mansion"),
		new TrimHint("ward", "ancient"), new TrimHint("silence", "ancient"), new TrimHint("snout", "bastion"),
		new TrimHint("rib", "fortress"), new TrimHint("eye", "stronghold"), new TrimHint("spire", "end_city"),
		new TrimHint("wayfinder", "trail"), new TrimHint("raiser", "trail"), new TrimHint("shaper", "trail"),
		new TrimHint("host", "trail"), new TrimHint("flow", "ominous_vault"), new TrimHint("bolt", "vault"),
		// в самом конце — шаблон незеритового улучшения (это не отделка, но тоже кузнечный шаблон)
		new TrimHint(NETHERITE, "bastion"));

	/** Кнопка на экране: где нарисована, что делает по клику, подсказка. */
	private record Hit(int x, int y, int w, int h, Runnable action, Component tip) {
	}

	private final SmithingScreen screen;
	private final Minecraft mc;
	private final Font font;
	private final List<Hit> hits = new ArrayList<>();
	private boolean fits;
	private int lx, rx, py, lpw, rpw, ph;
	private int scroll;
	/** кнопка «Получить» (только в творческом режиме) */
	private net.minecraft.client.gui.components.Button giveBtn;

	private SmithingPanels(SmithingScreen screen) {
		this.screen = screen;
		this.mc = Minecraft.getInstance();
		this.font = mc.font;
	}

	public static void attach(SmithingScreen screen) {
		SmithingPanels p = new SmithingPanels(screen);
		p.layout();
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) -> p.extract(g, mx, my));
		// подсказка не запоминается: стол закрыли — в следующий раз откроется обычный вид
		ScreenEvents.remove(screen).register(s -> hintMode = false);
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !p.click(e.x(), e.y()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !p.scroll(x, y, v));
		ScreenMouseEvents.allowMouseDrag(screen).register((s, e, dx, dy) -> !p.drag(dx, dy));
		ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !p.release(e.x(), e.y()));
	}

	private ContainerScreenAccessor acc() {
		return (ContainerScreenAccessor) screen;
	}

	private Config cfg() {
		return Config.get();
	}

	private boolean shown() {
		return fits && cfg().smithPanels;
	}

	private void layout() {
		int left = acc().workstationhelper$leftPos(), top = acc().workstationhelper$topPos();
		int iw = acc().workstationhelper$imageWidth(), ih = acc().workstationhelper$imageHeight();
		rpw = Math.min(150, screen.width - left - iw - 4);
		lpw = Math.min(120, left - 4);
		fits = rpw >= 100 && lpw >= 90;
		ph = ih;
		py = top;
		lx = left - lpw - 2;
		rx = left + iw + 2;
		// «Получить» — в правом верхнем углу кастомизатора, справа от вкладок
		int gx = rx + PAD + 4 * (CELL + 2) + 2;
		giveBtn = net.minecraft.client.gui.components.Button.builder(Component.translatable("workstationhelper.smith.give"), b -> give())
			.bounds(gx, py + 18, rx + rpw - PAD - gx, CELL).build();
		net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(giveBtn);
		updateGive();
	}

	private boolean creative() {
		return mc.player != null && mc.player.hasInfiniteMaterials();
	}

	private void updateGive() {
		giveBtn.visible = shown() && creative();
		giveBtn.active = !stack(tab).isEmpty();
	}

	/** Творческий режим: кладёт в инвентарь предмет открытой вкладки — как выдаёт творческая вкладка. */
	private void give() {
		ItemStack out = stack(tab);
		if (!creative() || mc.gameMode == null || out.isEmpty()) return;
		var inv = mc.player.getInventory();
		int free = inv.getFreeSlot();
		if (free < 0) {
			mc.gui.chatListener().handleOverlay(Component.translatable("workstationhelper.create.full").withStyle(net.minecraft.ChatFormatting.RED));
			return;
		}
		inv.setItem(free, out);
		// номер слота в окне инвентаря: хотбар — 36..44, остальное — 9..35
		int menuSlot = free < 9 ? net.minecraft.world.inventory.InventoryMenu.USE_ROW_SLOT_START + free : free;
		// серверу — отдельная копия: пакет уходит не сразу, а предмет в инвентаре могут сразу взять мышкой
		mc.gameMode.handleCreativeModeItemAdd(out.copy(), menuSlot);
		mc.gui.chatListener().handleOverlay(Component.translatable("workstationhelper.smith.given").withStyle(net.minecraft.ChatFormatting.GREEN));
	}

	/** Мини-кнопка «−/+» в правом верхнем углу окна стола: прячет и показывает панели. */
	private int toggleX() {
		return Vanilla.toggleRightX(acc().workstationhelper$leftPos(), acc().workstationhelper$imageWidth());
	}

	private boolean onStand(double mx, double my) {
		return inside(mx, my, toggleX(), Vanilla.toggleY(acc().workstationhelper$topPos()), Vanilla.TOGGLE, Vanilla.TOGGLE);
	}

	// ======================== наряд ========================

	private Config.OutfitPiece piece(int slot) {
		return cfg().outfit.computeIfAbsent(SLOTS[slot], k -> new Config.OutfitPiece());
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}

	private static Item armorItem(String material, int slot) {
		if ("turtle".equals(material)) return slot == 0 ? Items.TURTLE_HELMET : Items.AIR;
		return item(material + "_" + SUFFIX[slot]);
	}

	@SuppressWarnings("unchecked")
	private <T> Holder<T> holder(net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> reg, String id) {
		if (id == null || mc.level == null) return null;
		return mc.level.registryAccess().lookupOrThrow(reg).get(net.minecraft.resources.ResourceKey.create(reg, Identifier.parse(id)))
			.map(h -> (Holder<T>) h).orElse(null);
	}

	/** Предмет для слота по выбору в кастомизаторе (с краской и отделкой). */
	private ItemStack stack(int slot) {
		Config.OutfitPiece p = piece(slot);
		if (p.material == null) return ItemStack.EMPTY;
		Item it = armorItem(p.material, slot);
		if (it == Items.AIR) return ItemStack.EMPTY;
		ItemStack s = new ItemStack(it);
		if ("leather".equals(p.material) && p.dye != null) {
			DyeColor dye = DyeColor.byName(p.dye, null);
			if (dye != null) s.set(DataComponents.DYED_COLOR, new DyedItemColor(dye.getTextureDiffuseColor()));
		}
		Holder<TrimPattern> pat = holder(Registries.TRIM_PATTERN, p.pattern);
		Holder<TrimMaterial> mat = holder(Registries.TRIM_MATERIAL, p.trim);
		if (pat != null && mat != null) s.set(DataComponents.TRIM, new ArmorTrim(mat, pat));
		return s;
	}

	private void select(Runnable change) {
		change.run();
		Config.save();
	}

	// ======================== отрисовка ========================

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		if (fits) {
			boolean hover = onStand(mx, my);
			Vanilla.toggle(g, toggleX(), Vanilla.toggleY(acc().workstationhelper$topPos()), hover, cfg().smithPanels);
			if (hover) g.setTooltipForNextFrame(font, Component.translatable(cfg().smithPanels ? "workstationhelper.hide" : "workstationhelper.show"), mx, my);
		}
		hits.clear();
		updateGive();
		if (!shown()) return;
		extractPreview(g, mx, my);
		extractCustomizer(g, mx, my);
	}

	/** Левая панель: крутящийся манекен в наряде — окошко до самого низа панели. */
	private void extractPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Vanilla.panel(g, lx, py, lpw, ph);
		g.text(font, Component.translatable("workstationhelper.smith.preview"), lx + PAD, py + 7, Vanilla.TEXT, false);
		int bx = lx + PAD, by = py + 18, bw = lpw - 2 * PAD, bh = py + ph - PAD - by;
		Vanilla.inset(g, bx, by, bw, bh, Vanilla.SLOT);
		boxX = bx;
		boxY = by;
		boxW = bw;
		boxH = bh;

		// сам медленно крутится (примерно 15 секунд на круг), пока его не держат мышкой
		long now = System.currentTimeMillis();
		if (lastFrame != 0 && spinning && !dragging) yaw = (yaw + (now - lastFrame) * 360f / 15000f) % 360f;
		lastFrame = now;

		ArmorStandRenderState st = new ArmorStandRenderState();
		st.entityType = EntityTypes.ARMOR_STAND;
		st.showArms = true;
		st.bodyRot = yaw;
		st.headEquipment = stack(0);
		st.chestEquipment = stack(1);
		st.legsEquipment = stack(2);
		st.feetEquipment = stack(3);
		// в ботинках подставка не нужна
		st.showBasePlate = st.feetEquipment.isEmpty();
		// чуть крупнее, чем раньше, и по центру окошка
		float scale = bh * 27f / 70f;
		Quaternionf angle = new Quaternionf().rotationXYZ(pitch, 0, (float) Math.PI);
		g.entity(st, scale, TRANSLATION, angle, null, bx + 1, by + 1, bx + bw - 1, by + bh - 1);
		extractPlayPause(g, mouseX, mouseY);
		// «Сброс» в левом верхнем углу окошка — снимает весь наряд, как при первом открытии
		Component reset = Component.translatable("workstationhelper.smith.reset");
		boolean overReset = inside(mouseX, mouseY, resetX() - 1, playY() - 1, font.width(reset) + 2, 10);
		g.text(font, reset, resetX(), playY(), overReset ? 0xFFE0E0E0 : 0xFFB0B0B0, false);
	}

	/** Кнопка в правом верхнем углу окошка: крутится — две палочки (пауза), стоит — треугольник (пуск). Без рамки. */
	private int resetX() {
		return boxX + 4;
	}

	private int playX() {
		return boxX + boxW - 12;
	}

	private int playY() {
		return boxY + 4;
	}

	private void extractPlayPause(GuiGraphicsExtractor g, int mx, int my) {
		int x = playX(), y = playY();
		boolean hover = inside(mx, my, x - 1, y - 1, 10, 10);
		int col = hover ? 0xFFE0E0E0 : 0xFFB0B0B0;
		if (spinning) {
			g.fill(x + 1, y, x + 3, y + 8, col);
			g.fill(x + 5, y, x + 7, y + 8, col);
		} else {
			// треугольник остриём вправо: ширина строки растёт к середине и уменьшается к низу
			for (int r = 0; r < 8; r++) {
				int w = r < 4 ? r + 1 : 8 - r;
				g.fill(x + 1, y + r, x + 1 + w * 3 / 2, y + r + 1, col);
			}
		}
	}

	private int hintX() {
		return rx + rpw - PAD - 14;
	}

	/** Короткое название отделки: «Часовой» из «Отделка «Часовой»». */
	private static String shortName(Component c) {
		String full = c.getString();
		int a = full.indexOf('«'), b = full.lastIndexOf('»');
		if (a >= 0 && b > a) return full.substring(a + 1, b);
		// английский: «Sentry Armor Trim» → «Sentry»
		return full.replace("Armor Trim", "").trim();
	}

	private int hintW() {
		return rpw - 2 * PAD - 8 - 22;
	}

	private int hintH(TrimHint h) {
		return font.split(Component.translatable("workstationhelper.smith.where." + h.where()), hintW() - 6).size() * 9 + 5;
	}

	private int zoneH(TrimHint h) {
		return Math.max(20, hintH(h));
	}

	private int hintRowH(TrimHint h) {
		return 10 + zoneH(h) + 6;
	}

	/** Подсказка: у каждого шаблона — название по центру, под ним вдавленная зона: слева где найти, справа сам шаблон. */
	private void extractTrimHints(GuiGraphicsExtractor g, int mx, int my) {
		int top = py + 19, bottom = py + ph - PAD, x = rx + PAD, inner = rpw - 2 * PAD - 8;
		int total = 0;
		for (TrimHint h : TRIM_HINTS) total += hintRowH(h);
		int max = Math.max(0, total - (bottom - top));
		hintScroll = Math.clamp(hintScroll, 0, max);
		ItemStack tip = null;
		g.enableScissor(x, top, rx + rpw - PAD, bottom);
		int y = top - hintScroll;
		for (TrimHint h : TRIM_HINTS) {
			int rh = hintRowH(h);
			if (y + rh >= top && y <= bottom) {
				boolean upgrade = NETHERITE.equals(h.pattern());
				Holder<TrimPattern> pat = upgrade ? null : holder(Registries.TRIM_PATTERN, "minecraft:" + h.pattern());
				ItemStack stack = new ItemStack(item(upgrade ? "netherite_upgrade_smithing_template" : h.pattern() + "_armor_trim_smithing_template"));
				String title = upgrade ? Component.translatable("workstationhelper.smith.netherite").getString()
					: pat != null ? shortName(pat.value().description()) : stack.getHoverName().getString();
				g.text(font, title, x + (inner - font.width(title)) / 2, y, Vanilla.TEXT, false);
				int sy = y + 10, zh = zoneH(h), sx = x + inner - 18 - 4, slotY = sy + (zh - 18) / 2;
				var lines = font.split(Component.translatable("workstationhelper.smith.where." + h.where()), hintW() - 6);
				// отступ внутри рамки — по 3 пикселя слева (текст) и справа (шаблон)
				Vanilla.inset(g, x, sy, inner - 2, zh, Vanilla.SLOT);
				int ty = sy + (zh - lines.size() * 9) / 2 + 1;
				for (var line : lines) {
					g.text(font, line, x + 3, ty, 0xFFE0E0E0, true);
					ty += 9;
				}
				g.item(stack, sx + 1, slotY + 1);
				if (my >= top && my < bottom && inside(mx, my, sx, slotY, 18, 18)) tip = stack;
			}
			y += rh;
		}
		g.disableScissor();
		scroller(g, rx + rpw - PAD - 6, top, bottom - top, hintScroll, max);
		if (tip != null) g.setTooltipForNextFrame(font, tip.getHoverName(), mx, my);
	}

	/** Правая панель: вкладки слотов сверху, ниже — выбор материала, цвета, отделки (листается колёсиком). */
	private void extractCustomizer(GuiGraphicsExtractor g, int mx, int my) {
		current = g;
		Vanilla.panel(g, rx, py, rpw, ph);
		boolean hint = hintMode && !creative();
		g.text(font, Component.translatable(hint ? "workstationhelper.smith.trims" : "workstationhelper.smith.custom"), rx + PAD, py + 7, Vanilla.TEXT, false);
		// «?» — только в выживании (в творческом всё и так можно взять)
		if (!creative()) {
			int hx = hintX(), hy = py + 4;
			boolean over = inside(mx, my, hx, hy, 14, 14);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace(over ? "widget/button_highlighted" : "widget/button"), hx, hy, 14, 14);
			g.text(font, "?", hx + 5, hy + 3, hint ? 0xFFFFFF55 : 0xFFFFFFFF, true);
			if (over) g.setTooltipForNextFrame(font, Component.translatable(hint ? "workstationhelper.smith.hint_off" : "workstationhelper.loom.hint"), mx, my);
		}
		if (hint) {
			extractTrimHints(g, mx, my);
			return;
		}

		// вкладки: шлем, нагрудник, поножи, ботинки
		int tx = rx + PAD;
		for (int i = 0; i < 4; i++) {
			final int slot = i;
			ItemStack icon = stack(i);
			int cx = tx + i * (CELL + 2);
			// пустой слот — серый контур брони, как в инвентаре персонажа
			if (icon.isEmpty()) {
				Vanilla.inset(g, cx, py + 18, CELL, CELL, Vanilla.SLOT);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SLOT[i], cx + 1, py + 19, 16, 16);
			}
			cell(g, mx, my, cx, py + 18, icon, tab == i, () -> {
				tab = slot;
				scroll = 0;
			}, Component.translatable("workstationhelper.smith.slot." + SLOTS[i]));
		}

		int top = py + 18 + CELL + 4, bottom = py + ph - PAD;
		int contentW = rpw - 2 * PAD - 8;
		int cols = Math.max(1, (contentW + 2) / (CELL + 2));
		Config.OutfitPiece p = piece(tab);

		// содержимое (y — относительно начала списка)
		List<Runnable> draws = new ArrayList<>();
		int[] y = {0};
		int x0 = rx + PAD;

		// материал
		section(draws, y, Component.translatable("workstationhelper.smith.material"));
		List<Object[]> mats = new ArrayList<>();
		mats.add(new Object[] {null, new ItemStack(Items.BARRIER), Component.translatable("workstationhelper.smith.none_armor")});
		for (String m : MATERIALS) {
			Item it = armorItem(m, tab);
			if (it != Items.AIR) mats.add(new Object[] {m, new ItemStack(it), new ItemStack(it).getHoverName()});
		}
		grid(draws, y, mats.size(), cols, CELL, (i, cx, cy) -> {
			Object[] o = mats.get(i);
			String m = (String) o[0];
			cellDeferred(g, mx, my, cx, cy, top, bottom, (ItemStack) o[1], java.util.Objects.equals(p.material, m), () -> select(() -> {
				p.material = m;
				if (!"leather".equals(m)) p.dye = null;
			}), (Component) o[2]);
		}, x0);

		// цвет кожи
		if ("leather".equals(p.material)) {
			section(draws, y, Component.translatable("workstationhelper.smith.color"));
			DyeColor[] dyes = DyeColor.values();
			int swCols = Math.max(1, (contentW + 2) / (SWATCH + 2));
			grid(draws, y, dyes.length + 1, swCols, SWATCH, (i, cx, cy) -> {
				String name = i == 0 ? null : dyes[i - 1].getName();
				int color = i == 0 ? NO_DYE : 0xFF000000 | dyes[i - 1].getTextureDiffuseColor();
				Component tip = i == 0 ? Component.translatable("workstationhelper.smith.no_dye") : Component.translatable("color.minecraft." + name);
				swatch(g, mx, my, cx, cy, top, bottom, color, java.util.Objects.equals(p.dye, name), () -> select(() -> p.dye = name), tip);
			}, x0);
		}

		// отделка
		if (p.material != null && mc.level != null) {
			section(draws, y, Component.translatable("workstationhelper.smith.trim"));
			List<Holder.Reference<TrimPattern>> patterns = mc.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).listElements().toList();
			grid(draws, y, patterns.size() + 1, cols, CELL, (i, cx, cy) -> {
				if (i == 0) {
					cellDeferred(g, mx, my, cx, cy, top, bottom, new ItemStack(Items.BARRIER), p.pattern == null,
						() -> select(() -> p.pattern = null), Component.translatable("workstationhelper.smith.no_trim"));
					return;
				}
				Holder.Reference<TrimPattern> h = patterns.get(i - 1);
				String id = h.key().identifier().toString();
				ItemStack icon = new ItemStack(item(h.key().identifier().getPath() + "_armor_trim_smithing_template"));
				cellDeferred(g, mx, my, cx, cy, top, bottom, icon, id.equals(p.pattern), () -> select(() -> {
					p.pattern = id;
					if (p.trim == null) p.trim = "minecraft:gold";
				}), h.value().description());
			}, x0);

			// цвет отделки
			if (p.pattern != null) {
				section(draws, y, Component.translatable("workstationhelper.smith.trim_color"));
				List<Holder.Reference<TrimMaterial>> mats2 = mc.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).listElements().toList();
				grid(draws, y, mats2.size(), cols, CELL, (i, cx, cy) -> {
					Holder.Reference<TrimMaterial> h = mats2.get(i);
					String path = h.key().identifier().getPath();
					String id = h.key().identifier().toString();
					ItemStack icon = new ItemStack(item(TRIM_ITEMS.getOrDefault(path, path)));
					cellDeferred(g, mx, my, cx, cy, top, bottom, icon, id.equals(p.trim), () -> select(() -> p.trim = id), h.value().description());
				}, x0);
			}
		}

		int total = y[0];
		int max = Math.max(0, total - (bottom - top));
		scroll = Math.clamp(scroll, 0, max);
		offset = top - scroll;
		g.enableScissor(rx + PAD, top, rx + rpw - PAD, bottom);
		for (Runnable d : draws) d.run();
		g.disableScissor();
		scroller(g, rx + rpw - PAD - 6, top, bottom - top, scroll, max);
	}

	/** Сдвиг содержимого правой панели на экране (начало списка минус прокрутка). */
	private int offset;

	private interface CellDraw {
		void draw(int i, int x, int y);
	}

	private void section(List<Runnable> draws, int[] y, Component title) {
		int yy = y[0];
		draws.add(() -> extractSectionTitle(title, yy));
		y[0] += 11;
	}

	private GuiGraphicsExtractor current;

	private void extractSectionTitle(Component title, int yy) {
		current.text(font, title, rx + PAD, offset + yy + 1, Vanilla.TEXT, false);
	}

	private void grid(List<Runnable> draws, int[] y, int count, int cols, int size, CellDraw draw, int x0) {
		int start = y[0];
		for (int i = 0; i < count; i++) {
			final int idx = i;
			int cx = x0 + (i % cols) * (size + 2);
			int cy = start + (i / cols) * (size + 2);
			draws.add(() -> draw.draw(idx, cx, offset + cy));
		}
		y[0] += ((count + cols - 1) / cols) * (size + 2) + 3;
	}

	/** Ячейка-слот с предметом: вдавленная, при наведении светлеет, выбранная — светлый фон и зелёная рамка. */
	private void cell(GuiGraphicsExtractor g, int mx, int my, int x, int y, ItemStack icon, boolean selected, Runnable action, Component tip) {
		if (!icon.isEmpty()) {
			// выбранная ячейка — светлее фон, чтобы её было лучше видно
			Vanilla.inset(g, x, y, CELL, CELL, selected ? 0xFFB0B0B0 : Vanilla.SLOT);
			g.item(icon, x + 1, y + 1);
		}
		boolean hover = mx >= x && my >= y && mx < x + CELL && my < y + CELL;
		if (hover) g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x80FFFFFF);
		if (selected) g.outline(x, y, CELL, CELL, DONE_FRAME);
		if (hover) g.setTooltipForNextFrame(font, tip, mx, my);
		hits.add(new Hit(x, y, CELL, CELL, action, tip));
	}

	/** То же, но в прокручиваемой части: кнопка работает, только если видна. */
	private void cellDeferred(GuiGraphicsExtractor g, int mx, int my, int x, int y, int top, int bottom, ItemStack icon, boolean selected,
		Runnable action, Component tip) {
		if (y + CELL <= top || y >= bottom) return;
		boolean visible = my >= top && my < bottom;
		cell(g, visible ? mx : -1, visible ? my : -1, x, y, icon, selected, action, tip);
		if (y < top || y + CELL > bottom) hits.removeLast();
	}

	/** Квадратик цвета кожи. */
	private void swatch(GuiGraphicsExtractor g, int mx, int my, int x, int y, int top, int bottom, int color, boolean selected, Runnable action,
		Component tip) {
		if (y + SWATCH <= top || y >= bottom) return;
		Vanilla.inset(g, x, y, SWATCH, SWATCH, color == NO_DYE ? Vanilla.SLOT : color);
		if (color == NO_DYE) {
			for (int r = 0; r < NO_DYE_ICON.length; r++) {
				for (int c2 = 0; c2 < NO_DYE_ICON[r].length(); c2++) {
					if (NO_DYE_ICON[r].charAt(c2) == '#') g.fill(x + 1 + c2, y + 1 + r, x + 2 + c2, y + 2 + r, 0xFFE02020);
				}
			}
		}
		boolean hover = my >= top && my < bottom && mx >= x && my >= y && mx < x + SWATCH && my < y + SWATCH;
		if (hover) g.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, 0x50FFFFFF);
		if (selected) g.outline(x, y, SWATCH, SWATCH, DONE_FRAME);
		if (hover) g.setTooltipForNextFrame(font, tip, mx, my);
		if (y >= top && y + SWATCH <= bottom) hits.add(new Hit(x, y, SWATCH, SWATCH, action, tip));
	}

	private static void scroller(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int max) {
		Vanilla.inset(g, x - 1, y, 8, h, 0xFF6F6F6F);
		if (max > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, x, y + 1 + (h - 2 - 27) * scroll / max, 6, 27);
		else g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_OFF, x, y + 1, 6, 27);
	}

	// ======================== мышь ========================

	private boolean click(double mx, double my) {
		if (fits && onStand(mx, my)) {
			cfg().smithPanels = !cfg().smithPanels;
			Vanilla.clickSound();
			Config.save();
			return true;
		}
		if (!shown()) return false;
		if (!creative() && inside(mx, my, hintX(), py + 4, 14, 14)) {
			hintMode = !hintMode;
			hintScroll = 0;
			Vanilla.clickSound();
			return true;
		}
		if (giveBtn.visible && giveBtn.isMouseOver(mx, my)) return false;
		if (inside(mx, my, resetX() - 1, playY() - 1, font.width(Component.translatable("workstationhelper.smith.reset")) + 2, 10)) {
			cfg().outfit.clear();
			Vanilla.clickSound();
			tab = 0;
			scroll = 0;
			Config.save();
			return true;
		}
		if (inside(mx, my, playX() - 1, playY() - 1, 10, 10)) {
			spinning = !spinning;
			Vanilla.clickSound();
			return true;
		}
		if (inside(mx, my, boxX, boxY, boxW, boxH)) {
			dragging = true;
			return true;
		}
		for (Hit h : hits) {
			if (mx >= h.x() && my >= h.y() && mx < h.x() + h.w() && my < h.y() + h.h()) {
				h.action().run();
				Vanilla.clickSound();
				return true;
			}
		}
		return inside(mx, my, lx, py, lpw, ph) || inside(mx, my, rx, py, rpw, ph);
	}

	/** Тянешь мышкой — манекен поворачивается: влево-вправо вокруг себя, вверх-вниз наклоняется. */
	private boolean drag(double dx, double dy) {
		if (!dragging) return false;
		yaw = (yaw - (float) dx * 2.5f) % 360f;
		pitch = Math.clamp(pitch + (float) dy * 0.03f, -1.4f, 1.4f);
		return true;
	}

	/** Отпускание кнопки: конец поворота манекена; над панелями — наше (иначе игра выбросит вещь из руки). */
	private boolean release(double mx, double my) {
		if (dragging) {
			dragging = false;
			return true;
		}
		return shown() && (inside(mx, my, lx, py, lpw, ph) || inside(mx, my, rx, py, rpw, ph));
	}

	private boolean scroll(double mx, double my, double v) {
		if (!shown() || !inside(mx, my, rx, py, rpw, ph)) return false;
		if (hintMode && !creative()) hintScroll -= (int) Math.signum(v) * 20;
		else scroll -= (int) Math.signum(v) * (CELL + 2);
		return true;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
