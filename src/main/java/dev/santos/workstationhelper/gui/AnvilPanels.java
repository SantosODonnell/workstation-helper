package dev.santos.workstationhelper.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.logic.AnvilMath;
import dev.santos.workstationhelper.logic.BookPicker;
import dev.santos.workstationhelper.logic.Piece;
import dev.santos.workstationhelper.logic.Planner;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Две панели по бокам окна наковальни. Слева — какие зачарования можно поставить на предмет
 * (клик по уровню добавляет в план), справа — порядок книг, итог и кнопки.
 */
public final class AnvilPanels {
	private static final int ROW = 20;
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/villager/scroller");
	private static final Identifier SCROLLER_OFF = Identifier.withDefaultNamespace("container/villager/scroller_disabled");
	private static final int STEP_ROW = 20;
	private static final int PAD = 7;

	/** Закреплённый расчёт: живёт, пока запущена игра, и не пропадает при закрытии наковальни. */
	private record Pinned(ItemStack item, Map<Holder<Enchantment>, Integer> selection, Planner.Plan plan) {
	}

	private static Pinned pinned;

	private record Row(Holder<Enchantment> ench, int max, boolean curse) {
	}

	/** Строка плана на экране: шаг и где он нарисован. */
	private record StepBox(Planner.Step step, int y) {
	}

	private final AnvilScreen screen;
	private final Minecraft mc;
	private final Font font;

	// раскладка
	private boolean fits;
	/** pw — ширина правой панели, lpw — левой (подстраивается под самое длинное название зачарования) */
	private int lx, rx, py, pw, lpw, ph;
	private int listTop, listBottom, stepsTop, stepsBottom;

	// левая панель
	private final List<Row> rows = new ArrayList<>();
	private final Map<Holder<Enchantment>, Integer> selection = new LinkedHashMap<>();
	private ItemStack lastItem = ItemStack.EMPTY;
	private Piece item;
	private int leftScroll;
	private int neededScroll;

	// правая панель
	private Planner.Plan plan;
	private boolean planned;
	private Component message;
	private List<BookPicker.Missing> missing = List.of();
	private int rightScroll;
	/** в выживании: план без лимита 39 уровней, дорогие шаги — красной ценой */
	private boolean forced;
	/** последний расчёт не уложился в 39 уровней на шаг */
	private boolean tooExpensive;
	/** где нарисована ссылка «Всё равно показать» (для клика); ширина 0 — ссылки нет */
	private int linkX, linkY, linkW;
	private final List<StepBox> stepBoxes = new ArrayList<>();
	private Planner.Step hoveredStep;
	/** какой шаг использует вещь (результат прошлого шага или книгу) */
	private final Map<Piece, Planner.Step> usedBy = new java.util.IdentityHashMap<>();
	/** выполненные шаги — кнопка с зелёной обводкой */
	private final java.util.Set<Planner.Step> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	private Button goBtn, createBtn;

	private AnvilPanels(AnvilScreen screen) {
		this.screen = screen;
		this.mc = Minecraft.getInstance();
		this.font = mc.font;
	}

	/** Вызывается после открытия (и каждого изменения размера) окна наковальни. */
	public static void attach(AnvilScreen screen) {
		AnvilPanels p = new AnvilPanels(screen);
		p.layout();
		p.restorePinned();
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) -> p.extractBack(g, mx, my));
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, a) -> p.extractFront(g, mx, my));
		ScreenEvents.afterTick(screen).register(s -> p.tick());
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !p.click(e.x(), e.y(), e.button()));
		// отпускание кнопки над панелями тоже наше: иначе игра решит, что мышь «вне окна», и выбросит вещь из руки
		ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !p.overPanel(e.x(), e.y()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !p.scroll(x, y, v));
		p.tick();
	}

	private ContainerScreenAccessor acc() {
		return (ContainerScreenAccessor) screen;
	}

	private AnvilMenu menu() {
		return screen.getMenu();
	}

	private Config cfg() {
		return Config.get();
	}

	private boolean shown() {
		return fits && cfg().sidePanels;
	}

	/** Левая панель: обычно — выбор зачарований, при закрепе — список нужных книг. */
	private boolean leftShown() {
		return shown();
	}

	/** Правая панель появляется только после «Рассчитать» (или когда расчёт закреплён). */
	private boolean rightShown() {
		return shown() && (planned || pinned != null);
	}

	/** Список зачарований виден, только если в левом слоте лежит сам предмет; пусто или книга — стартовая страница. */
	private boolean listShown() {
		ItemStack now = menu().getSlot(AnvilMenu.INPUT_SLOT).getItem();
		return item != null && !now.isEmpty() && now.is(lastItem.getItem());
	}

	// ======================== раскладка и кнопки ========================

	private void layout() {
		int left = acc().workstationhelper$leftPos(), top = acc().workstationhelper$topPos();
		int iw = acc().workstationhelper$imageWidth(), ih = acc().workstationhelper$imageHeight();
		pw = Math.min(150, screen.width - left - iw - 4);
		lpw = Math.min(neededLeftWidth(), left - 4);
		fits = pw >= 100 && lpw >= 100;
		// по высоте — как само окно наковальни
		ph = ih;
		py = top;
		lx = left - lpw - 2;
		rx = left + iw + 2;
		// внизу левой панели — «Рассчитать» во всю ширину; внизу правой — строка «Всего уровней»
		int fw = lpw - 2 * PAD;
		int by = py + ph - 6 - 18;
		listTop = py + 18;
		// в выживании влезает 6 строк, в творческом над «Рассчитать» ещё «Создать предмет» — 5 строк
		listBottom = creative() ? by - 22 : by - 3;
		goBtn = Button.builder(Component.translatable("workstationhelper.go"), b -> runPlan()).bounds(lx + PAD, by, fw, 18).build();
		createBtn = Button.builder(Component.translatable("workstationhelper.create"), b -> createItem()).bounds(lx + PAD, by - 20, fw, 18).build();
		Screens.getWidgets(screen).add(goBtn);
		Screens.getWidgets(screen).add(createBtn);
		stepsTop = py + 20;
		stepsBottom = py + ph - PAD - 12;
		updateButtons();
	}

	private void updateButtons() {
		// на стартовой странице (пустой слот) кнопок нет
		goBtn.visible = leftShown() && pinned == null && listShown();
		goBtn.active = listShown() && !selection.isEmpty();
		createBtn.visible = goBtn.visible && creative();
		createBtn.active = goBtn.active;
	}

	/** Кнопка «Пин» в правом верхнем углу правой панели. */
	private int pinX() {
		return rx + pw - PAD - 12;
	}

	private int pinY() {
		return py + 5;
	}

	private void restorePinned() {
		if (pinned == null) return;
		lastItem = pinned.item().copy();
		onItemChanged(false);
		selection.clear();
		selection.putAll(pinned.selection());
		plan = pinned.plan();
		planned = true;
	}

	private void togglePin() {
		if (pinned != null) pinned = null;
		else if (planned && plan != null && item != null) pinned = new Pinned(lastItem.copy(), new LinkedHashMap<>(selection), plan);
	}

	// ======================== предмет и список зачарований ========================

	private void tick() {
		ItemStack now = menu().getSlot(AnvilMenu.INPUT_SLOT).getItem();
		if (!ItemStack.isSameItemSameComponents(now, lastItem)) {
			boolean book = now.is(Items.ENCHANTED_BOOK) || now.is(Items.BOOK);
			// пока есть расчёт, книги в левом слоте (шаги «объединить книги») и пустой слот его не сбрасывают;
			// без расчёта пустой слот возвращает стартовую страницу
			boolean keep = item != null && !item.book && (planned || pinned != null) && (now.isEmpty() || book);
			// закреплённый расчёт не сбрасывается, если положить другой предмет — сначала открепи
			if (pinned != null && item != null && !now.is(lastItem.getItem())) keep = true;
			if (!keep) {
				if (item != null && !now.isEmpty() && now.is(lastItem.getItem())) {
					// тот же предмет, но с новыми зачарованиями (шаг плана выполнен) — план и выбор остаются
					lastItem = now.copy();
					item = Piece.of(lastItem, AnvilMenu.INPUT_SLOT);
				} else {
					lastItem = now.copy();
					onItemChanged(false);
				}
			}
		}
		updateAbsent();
		updateButtons();
	}

	private void onItemChanged(boolean sameType) {
		rows.clear();
		planned = false;
		plan = null;
		message = null;
		missing = List.of();
		if (lastItem.isEmpty() || mc.level == null) {
			item = null;
			selection.clear();
			return;
		}
		item = Piece.of(lastItem, AnvilMenu.INPUT_SLOT);
		boolean book = lastItem.is(Items.BOOK) || lastItem.is(Items.ENCHANTED_BOOK);
		mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(h -> {
			if (book || h.value().canEnchant(lastItem)) rows.add(new Row(h, h.value().getMaxLevel(), h.is(EnchantmentTags.CURSE)));
		});
		sortRows();
		if (!sameType) {
			leftScroll = 0;
			loadPicks();
		}
		selection.keySet().removeIf(h -> rows.stream().noneMatch(r -> r.ench().equals(h)));
	}

	/**
	 * Порядок списка: проклятия в конце; зачарования, которые нельзя наложить вместе (все Защиты, Острота/Кара/Бич,
	 * Удача/Шёлковое касание…), стоят рядом группой. Внутри группы — от дешёвых к дорогим, группы — по самому дешёвому.
	 */
	private void sortRows() {
		Comparator<Row> byCost = Comparator.<Row>comparingInt(r -> r.ench().value().getAnvilCost())
			.thenComparing(r -> r.ench().value().description().getString());
		// группы: несовместимые попадают в одну (и цепочкой — если A мешает B, а B мешает C)
		int n = rows.size();
		int[] group = new int[n];
		for (int i = 0; i < n; i++) group[i] = i;
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				if (!Enchantment.areCompatible(rows.get(i).ench(), rows.get(j).ench())) {
					int a = root(group, i), b = root(group, j);
					if (a != b) group[b] = a;
				}
			}
		}
		Map<Integer, List<Row>> groups = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) groups.computeIfAbsent(root(group, i), k -> new ArrayList<>()).add(rows.get(i));
		List<List<Row>> ordered = new ArrayList<>(groups.values());
		for (List<Row> gr : ordered) gr.sort(byCost);
		ordered.sort(Comparator.<List<Row>, Boolean>comparing(gr -> gr.getFirst().curse()).thenComparing(gr -> gr.getFirst(), byCost));
		rows.clear();
		for (List<Row> gr : ordered) rows.addAll(gr);
	}

	private static int root(int[] group, int i) {
		while (group[i] != i) i = group[i] = group[group[i]];
		return i;
	}

	private String itemKey() {
		return BuiltInRegistries.ITEM.getKey(lastItem.getItem()).toString();
	}

	private void loadPicks() {
		selection.clear();
		Map<String, Integer> saved = cfg().picks.get(itemKey());
		if (saved == null) return;
		for (Row r : rows) {
			Integer l = saved.get(r.ench().getRegisteredName());
			// сохранённый выбор, который теперь несовместим с предметом, не берём
			if (l != null && !blocked(r.ench())) selection.put(r.ench(), Math.clamp(l, 1, r.max()));
		}
	}

	private void savePicks() {
		Map<String, Integer> m = new LinkedHashMap<>();
		selection.forEach((h, l) -> m.put(h.getRegisteredName(), l));
		if (m.isEmpty()) cfg().picks.remove(itemKey());
		else cfg().picks.put(itemKey(), m);
		Config.save();
	}

	/** Выбор уровня: тот же уровень ещё раз — убрать; несовместимые зачарования убираются из плана. */
	private void pick(Row r, int level) {
		Integer cur = selection.get(r.ench());
		if (cur != null && cur == level) {
			selection.remove(r.ench());
		} else {
			selection.keySet().removeIf(h -> !h.equals(r.ench()) && !Enchantment.areCompatible(h, r.ench()));
			selection.put(r.ench(), level);
		}
		if (pinned == null) planned = false;
		savePicks();
	}

	/**
	 * Зачарование нельзя поставить: оно уже есть на предмете на максимальном уровне (Острота V)
	 * или несовместимо с тем, что уже есть (Острота → Небесная кара).
	 */
	private boolean blocked(Holder<Enchantment> e) {
		if (item == null) return false;
		if (item.ench.getOrDefault(e, 0) >= e.value().getMaxLevel()) return true;
		for (Holder<Enchantment> h : item.ench.keySet()) if (!h.equals(e) && !Enchantment.areCompatible(h, e)) return true;
		return false;
	}

	private boolean conflicts(Holder<Enchantment> e) {
		for (Holder<Enchantment> s : selection.keySet()) if (!s.equals(e) && !Enchantment.areCompatible(s, e)) return true;
		return false;
	}

	// ======================== расчёт плана ========================

	private void runPlan() {
		// повторное нажатие «Рассчитать», пока расчёт открыт, — закрыть правое окно (как повторный клик по флагу в станке)
		if (planned && pinned == null) {
			planned = false;
			plan = null;
			message = null;
			return;
		}
		forced = false;
		calculate();
	}

	/** Ссылка «Всё равно показать»: тот же расчёт, но без лимита 39 уровней. */
	private void showAnyway() {
		forced = true;
		calculate();
	}

	private void calculate() {
		planned = true;
		tooExpensive = false;
		plan = null;
		message = null;
		missing = List.of();
		rightScroll = 0;
		if (item == null || mc.player == null) return;
		Map<Holder<Enchantment>, Integer> targets = new LinkedHashMap<>();
		selection.forEach((h, l) -> {
			if (item.ench.getOrDefault(h, 0) < l) targets.put(h, l);
		});
		if (targets.isEmpty()) {
			message = Component.translatable("workstationhelper.already_applied");
			return;
		}
		// книги из инвентаря берутся сами; каких нет — в план идут как «нужно достать» (оранжевые)
		BookPicker.Result r = BookPicker.pick(targets, inventoryBooks());
		List<Piece> books = new ArrayList<>(r.books());
		missing = r.missing();
		for (BookPicker.Missing m : missing) books.add(Piece.book(m.ench(), m.level()));
		// при равной цене расчёт берёт первый найденный вариант — поэтому порядок книг должен быть всегда один и тот же
		books.sort(Comparator.comparing(AnvilPanels::bookKey));
		boolean creative = mc.player.hasInfiniteMaterials();
		// в творческом режиме лимита нет; в выживании — есть, пока не нажата «Всё равно показать»
		plan = new Planner(item, books, creative, !creative && !forced).solve();
		tooExpensive = plan == null;
		if (tooExpensive) message = Component.translatable("workstationhelper.too_expensive");
		// готовый предмет уже есть в инвентаре — все шаги сразу «выполнены», окно закрылось бы само; скажем почему
		if (plan != null) {
			updateAbsent();
			if (plan == null) {
				error(Component.translatable("workstationhelper.already_have"));
				return;
			}
		}
		// пересчёт при закрепе обновляет закреплённый расчёт
		if (pinned != null && plan != null) pinned = new Pinned(lastItem.copy(), new LinkedHashMap<>(selection), plan);
	}

	/** Зачарованные книги из инвентаря игрока и из правого слота наковальни (с номерами слотов меню). */
	/** Постоянный ключ книги: зачарования и уровни по алфавиту id. */
	private static String bookKey(Piece p) {
		List<String> parts = new ArrayList<>();
		p.ench.forEach((h, l) -> parts.add(h.getRegisteredName() + ":" + l));
		parts.sort(null);
		return String.join(",", parts);
	}

	private List<Piece> inventoryBooks() {
		List<Piece> out = new ArrayList<>();
		for (Slot s : menu().slots) {
			boolean mine = s.container instanceof Inventory || s.index == AnvilMenu.ADDITIONAL_SLOT;
			if (mine && s.getItem().is(Items.ENCHANTED_BOOK)) out.add(Piece.of(s.getItem(), s.index));
		}
		return out;
	}

	/**
	 * Обновляет, какие шаги уже выполнены. Вызывается 20 раз в секунду, только пока открыта наковальня.
	 * Шаг выполнен, если его результат есть в инвентаре (сделан на наковальне или найден готовым) или если выполнен
	 * следующий шаг, который этот результат уже израсходовал.
	 */
	private void updateAbsent() {
		done.clear();
		if (plan == null) return;
		List<Planner.Step> steps = plan.steps();
		// какой шаг использует результат шага (один и тот же объект Piece)
		usedBy.clear();
		for (Planner.Step st : steps) {
			usedBy.put(st.left(), st);
			usedBy.put(st.right(), st);
		}
		// с конца: шаг, который использует результат, всегда идёт позже
		for (int i = steps.size() - 1; i >= 0; i--) {
			Planner.Step st = steps.get(i);
			Planner.Step next = usedBy.get(st.result());
			if (has(st.result()) || (next != null && done.contains(next))) done.add(st);
		}
		// все шаги выполнены — правое окно закрывается само, закреп снимается
		if (!steps.isEmpty() && done.size() == steps.size()) {
			pinned = null;
			planned = false;
			plan = null;
			done.clear();
		}
	}

	// ======================== отрисовка ========================

	/** Мини-кнопка «−/+» в левом верхнем углу окна наковальни: прячет и показывает панели. */
	private int toggleX() {
		return acc().workstationhelper$leftPos() + 4;
	}

	private boolean onToggle(double mx, double my) {
		return inside(mx, my, toggleX(), Vanilla.toggleY(acc().workstationhelper$topPos()), Vanilla.TOGGLE, Vanilla.TOGGLE);
	}

	/** Стартовая страница (в левом слоте пусто): маленькое окно «Планировщик зачарований» под одну надпись. */
	private boolean startPage() {
		return pinned == null && !listShown();
	}

	private int startW() {
		return font.width(Component.translatable("workstationhelper.planner")) + 2 * PAD + 4;
	}

	private List<net.minecraft.util.FormattedCharSequence> startLines() {
		return font.split(Component.translatable("workstationhelper.place_item"), startW() - 2 * PAD - 6);
	}

	/** Высота вдавленного окна с надписью: строки по 10 пикселей и поля сверху/снизу. */
	private int startBoxH() {
		return startLines().size() * 10 + 6;
	}

	private int startH() {
		return 7 + 9 + 4 + startBoxH() + 6;
	}

	private int startX() {
		return acc().workstationhelper$leftPos() - startW() - 2;
	}

	/** Мышь над левой панелью (на стартовой странице — над маленьким окном). */
	private boolean overLeft(double mx, double my) {
		return startPage() ? inside(mx, my, startX(), py, startW(), startH()) : inside(mx, my, lx, py, lpw, ph);
	}

	private void extractBack(GuiGraphicsExtractor g, int mx, int my) {
		if (fits) {
			boolean hover = onToggle(mx, my);
			Vanilla.toggle(g, toggleX(), Vanilla.toggleY(acc().workstationhelper$topPos()), hover, cfg().sidePanels);
			if (hover) g.setTooltipForNextFrame(font, Component.translatable(cfg().sidePanels ? "workstationhelper.hide" : "workstationhelper.show"), mx, my);
		}
		// панели включаются ещё и в «Настройки → Специальные возможности → Помощник»
		if (leftShown()) {
			if (pinned != null) extractNeeded(g, mx, my);
			else extractLeft(g, mx, my);
		}
		if (rightShown()) extractRight(g, mx, my);
	}

	private void extractLeft(GuiGraphicsExtractor g, int mx, int my) {
		if (!listShown()) {
			// маленькое окно: заголовок и одна надпись во вдавленном окне, без кнопок
			int sx = startX(), boxY = py + 7 + 9 + 4;
			Vanilla.panel(g, sx, py, startW(), startH());
			g.text(font, Component.translatable("workstationhelper.planner"), sx + PAD, py + 7, Vanilla.TEXT, false);
			Vanilla.inset(g, sx + PAD - 1, boxY, startW() - 2 * PAD + 2, startBoxH(), Vanilla.SLOT);
			int ty = boxY + 4;
			for (var line : startLines()) {
				g.text(font, line, sx + PAD + 3, ty, 0xFFE0E0E0, true);
				ty += 10;
			}
			return;
		}
		Vanilla.panel(g, lx, py, lpw, ph);
		g.text(font, Component.translatable("workstationhelper.enchants"), lx + PAD, py + 7, Vanilla.TEXT, false);
		int visible = visibleRows();
		leftScroll = Math.clamp(leftScroll, 0, Math.max(0, rows.size() - visible));
		for (int i = 0; i < visible && leftScroll + i < rows.size(); i++) {
			Row r = rows.get(leftScroll + i);
			int y = listTop + i * ROW;
			int x = lx + PAD, w = rowWidth();
			int have = item.ench.getOrDefault(r.ench(), 0);
			Integer sel = selection.get(r.ench());
			// несовместимо с тем, что уже на предмете, — «вдавленная» неактивная кнопка, нажать нельзя
			boolean off = blocked(r.ench());
			boolean rowHover = !off && inside(mx, my, x, y, w, ROW);
			// кнопка как у предложений жителя: при наведении — белая рамка
			g.blitSprite(RenderPipelines.GUI_TEXTURED, off ? BUTTON_DISABLED : rowHover ? BUTTON_HOVER : BUTTON, x, y, w, ROW);

			// цифры уровней справа: наведённая — жёлтая, выбранная — зелёная
			int[] xs = levelXs(r, x + w);
			// строка несовместима с уже выбранным — цифры серые, но наведение и выбор работают
			boolean grey = sel == null && conflicts(r.ench());
			for (int l = 1; l <= r.max(); l++) {
				Component num = Component.translatable("enchantment.level." + l);
				int nx = xs[l], nw = font.width(num);
				boolean on = sel != null && sel == l;
				boolean owned = l <= have;
				boolean hover = !off && !owned && inside(mx, my, nx - 2, y, nw + 4, ROW);
				int col = off ? 0xFF707070 : on ? 0xFF55FF55 : owned ? 0xFF707070 : hover ? 0xFFFFFF55 : grey ? 0xFF8A8A8A : 0xFFE0E0E0;
				g.text(font, num, nx, y + 6, col, true);
			}
			// название
			int nameW = xs[1] - (x + 5) - 6;
			int col = off ? 0xFF707070 : r.curse() ? 0xFFFF5555 : sel != null ? 0xFF55FF55 : conflicts(r.ench()) ? 0xFF8A8A8A : 0xFFFFFFFF;
			g.text(font, font.plainSubstrByWidth(r.ench().value().description().getString(), nameW), x + 5, y + 6, col, true);
		}
		// ползунок прокрутки как в окне торговли
		int max = rows.size() - visible;
		scroller(g, lx + lpw - PAD - 6, listTop, visible * ROW, leftScroll, max);
	}

	private int visibleRows() {
		return Math.max(1, (listBottom - listTop) / ROW);
	}

	/** Ширина кнопки-строки: справа остаётся место под ползунок. */
	private int rowWidth() {
		return lpw - 2 * PAD - 8;
	}

	/** Ширина левой панели, при которой влезают название и все цифры уровней любого зачарования игры. */
	private int neededLeftWidth() {
		int widest = 0;
		if (mc.level != null) {
			for (var h : mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
				int w = font.width(h.value().description()) + 6;
				for (int l = 1; l <= h.value().getMaxLevel(); l++) w += font.width(Component.translatable("enchantment.level." + l)) + 5;
				widest = Math.max(widest, w);
			}
		}
		// поля кнопки (5 + 5), место под ползунок (8) и отступы панели
		return Math.max(150, widest + 10 + 8 + 2 * PAD);
	}

	/** x каждой цифры уровня (индекс = уровень), цифры прижаты к правому краю кнопки. */
	private int[] levelXs(Row r, int right) {
		int[] xs = new int[r.max() + 1];
		int x = right - 5;
		for (int l = r.max(); l >= 1; l--) {
			x -= font.width(Component.translatable("enchantment.level." + l));
			xs[l] = x;
			x -= 5;
		}
		return xs;
	}

	private void extractRight(GuiGraphicsExtractor g, int mx, int my) {
		Vanilla.panel(g, rx, py, pw, ph);
		g.text(font, Component.translatable("workstationhelper.order"), rx + PAD, py + 7, Vanilla.TEXT, false);
		extractPin(g, mx, my);
		stepBoxes.clear();
		hoveredStep = null;
		int tx = rx + PAD + 3, tw = pw - 2 * PAD - 6;
		boolean steps = planned && item != null && message == null && plan != null;
		if (!steps) Vanilla.inset(g, rx + PAD - 1, stepsTop - 1, pw - 2 * PAD + 2, stepsBottom - stepsTop + 2, Vanilla.SLOT);

		if (!planned || item == null) {
			wrap(g, Component.translatable("workstationhelper.hint"), tx, stepsTop + 4, tw, 0xFFE0E0E0);
		} else if (message != null) {
			wrap(g, message, tx, stepsTop + 4, tw, 0xFFFFE080);
		} else if (plan != null) {
			extractSteps(g, mx, my);
		}

		// итог
		if (steps) g.text(font, Component.translatable("workstationhelper.total_levels", plan.totalLevels()), rx + PAD, stepsBottom + 3, Vanilla.TEXT, false);

		// выживание, план не укладывается в 39 уровней — серая ссылка внизу
		linkW = 0;
		if (planned && tooExpensive && !forced) {
			Component link = Component.translatable("workstationhelper.show_anyway");
			// внутри серого окна, у самого низа; при наведении светлеет
			linkX = rx + PAD + 3;
			linkY = stepsBottom - 11;
			linkW = font.width(link);
			boolean hover = inside(mx, my, linkX, linkY - 1, linkW, 10);
			g.text(font, link, linkX, linkY, hover ? 0xFFFFFFFF : 0xFFB8B8B8, true);
		}
	}

	/** Кнопка «Пин» — галочка: серая, если расчёт не закреплён, зелёная — если закреплён. */
	private void extractPin(GuiGraphicsExtractor g, int mx, int my) {
		int x = pinX(), y = pinY();
		boolean hover = inside(mx, my, x, y, 12, 12);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HOVER : BUTTON, x, y, 12, 12);
		check(g, x, y, pinned != null ? 0xFF55FF55 : 0xFF8A8A8A);
		if (hover) g.setTooltipForNextFrame(font, Component.translatable("workstationhelper.pin"), mx, my);
	}

	/**
	 * Дорожка с ползунком, как в окне торговли. Дорожка ровно по высоте списка (от y до y + h, вровень с кнопками),
	 * ползунок — внутри её рамки.
	 */
	private static void scroller(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int max) {
		Vanilla.inset(g, x - 1, y, 8, h, 0xFF6F6F6F);
		if (max > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, x, y + 1 + (h - 2 - 27) * scroll / max, 6, 27);
		else g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_OFF, x, y + 1, 6, 27);
	}

	/** Галочка толщиной 2 пикселя в квадрате 12×12: короткий хвост вниз-вправо, длинный вверх-вправо. */
	private static void check(GuiGraphicsExtractor g, int x, int y, int col) {
		int[][] pts = {{2, 6}, {3, 7}, {4, 8}, {5, 7}, {6, 6}, {7, 5}, {8, 4}, {9, 3}};
		for (int[] pt : pts) g.fill(x + pt[0], y + pt[1], x + pt[0] + 1, y + pt[1] + 2, col);
	}

	/** Книги, которые нужно иметь для плана (не получаются на его шагах), без повторов. */
	private List<Piece> neededBooks() {
		List<Piece> out = new ArrayList<>();
		if (plan == null) return out;
		java.util.Set<Piece> made = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Planner.Step st : plan.steps()) made.add(st.result());
		for (Planner.Step st : plan.steps()) {
			for (Piece p : new Piece[] {st.left(), st.right()}) {
				if (p.book && !made.contains(p) && !out.contains(p)) out.add(p);
			}
		}
		return out;
	}

	/** Книга уже есть (в инвентаре, наковальне, на курсоре) или уже потрачена на выполненный шаг. */
	private boolean haveNeeded(Piece p) {
		Planner.Step st = usedBy.get(p);
		return has(p) || (st != null && done.contains(st));
	}

	/** Строка списка «Нужные книги»: книга и одно её зачарование. */
	private record NeededRow(Piece book, Component name) {
	}

	/** Левая панель при закрепе: иконка книги, зачарование и зелёная галочка, если книга есть. Каждое зачарование — своя строка. */
	private void extractNeeded(GuiGraphicsExtractor g, int mx, int my) {
		Vanilla.panel(g, lx, py, lpw, ph);
		g.text(font, Component.translatable("workstationhelper.needed"), lx + PAD, py + 7, Vanilla.TEXT, false);
		List<NeededRow> lines = new ArrayList<>();
		for (Piece p : neededBooks()) {
			for (var e : p.ench.entrySet()) lines.add(new NeededRow(p, Enchantment.getFullname(e.getKey(), e.getValue())));
		}
		// список почти до нижнего края окна — чтобы влезла ещё одна строка
		int top = py + 18, bottom = py + ph - 4;
		int visible = Math.max(1, (bottom - top) / ROW);
		int max = lines.size() - visible;
		neededScroll = Math.clamp(neededScroll, 0, Math.max(0, max));
		// если строки не помещаются — справа полоса прокрутки, текст становится уже
		int right = max > 0 ? lx + lpw - PAD - 8 : lx + lpw - PAD;
		int textW = right - (lx + PAD + 22) - 18;
		for (int i = 0; i < visible && neededScroll + i < lines.size(); i++) {
			NeededRow row = lines.get(neededScroll + i);
			int y = top + i * ROW, x = lx + PAD;
			// строка — вдавленная ячейка, как слот инвентаря, только растянутая
			Vanilla.inset(g, x, y, right - x, ROW - 1, Vanilla.SLOT);
			g.item(row.book().stack, x + 2, y + 2);
			g.text(font, font.plainSubstrByWidth(row.name().getString(), textW), x + 22, y + 6, 0xFFFFFFFF, true);
			if (haveNeeded(row.book())) check(g, right - 15, y + 3, 0xFF55FF55);
			if (inside(mx, my, x, y, right - x, ROW) && font.width(row.name()) > textW) g.setTooltipForNextFrame(font, row.name(), mx, my);
		}
		if (max > 0) {
			// ячейки строк на пиксель ниже ряда (зазор между ними) — дорожка вровень с последней ячейкой
			scroller(g, lx + lpw - PAD - 6, top, visible * ROW - 1, neededScroll, max);
		}
	}

	private void extractSteps(GuiGraphicsExtractor g, int mx, int my) {
		List<Planner.Step> steps = plan.steps();
		// строки: заголовок «Объединить книги», шаги, заголовок «На предмет», шаги
		List<Object> lines = new ArrayList<>();
		boolean mergesHeader = false, itemHeader = false;
		for (Planner.Step s : steps) {
			if (!s.onItem() && !mergesHeader) {
				lines.add(Component.translatable("workstationhelper.section_merge"));
				mergesHeader = true;
			}
			if (s.onItem() && !itemHeader) {
				lines.add(Component.translatable("workstationhelper.section_item"));
				itemHeader = true;
			}
			lines.add(s);
		}
		int visibleH = stepsBottom - stepsTop - 2;
		int totalH = 0;
		for (Object o : lines) totalH += o instanceof Component ? 11 : STEP_ROW;
		int maxScroll = Math.max(0, totalH - visibleH);
		rightScroll = Math.clamp(rightScroll, 0, maxScroll);
		int rowW = pw - 2 * PAD - 8;

		g.enableScissor(rx + PAD, stepsTop, rx + pw - PAD, stepsBottom);
		int y = stepsTop + 2 - rightScroll;
		int n = 0;
		for (Object o : lines) {
			if (o instanceof Component c) {
				g.text(font, c, rx + PAD + 1, y + 2, Vanilla.TEXT, false);
				y += 11;
				continue;
			}
			Planner.Step s = (Planner.Step) o;
			n++;
			boolean hover = inside(mx, my, rx + PAD, y, rowW, STEP_ROW) && my >= stepsTop && my < stepsBottom;
			if (hover) hoveredStep = s;
			boolean stepDone = done.contains(s);
			// кнопка как у предложений жителя; выполненный шаг — «вдавленный», как неактивная кнопка меню
			g.blitSprite(RenderPipelines.GUI_TEXTURED, stepDone ? BUTTON_DISABLED : hover ? BUTTON_HOVER : BUTTON, rx + PAD, y, rowW, STEP_ROW);
			int x = rx + PAD + 4;
			g.text(font, n + ".", x, y + 6, stepDone ? 0xFFA0A0A0 : 0xFFE0E0E0, true);
			x += font.width(n + ".") + 2;
			g.item(s.left().stack, x, y + 2);
			g.text(font, "+", x + 18, y + 6, 0xFFFFFFFF, true);
			g.item(s.right().stack, x + 25, y + 2);
			Component cost = Component.translatable("workstationhelper.step_cost", s.cost());
			boolean tooMuch = forced && s.cost() >= AnvilMath.TOO_EXPENSIVE;
			int costCol = tooMuch ? 0xFFFF5555 : s.onItem() ? 0xFF80FF80 : 0xFFC0A0FF;
			g.text(font, cost, rx + PAD + rowW - 5 - font.width(cost), y + 6, stepDone ? 0xFFA0A0A0 : costCol, true);
			stepBoxes.add(new StepBox(s, y));
			y += STEP_ROW;
		}
		g.disableScissor();
		// ползунок как в окне торговли: от первой строки списка до нижней границы видимой области
		scroller(g, rx + pw - PAD - 6, stepsTop + 2, visibleH, rightScroll, maxScroll);
		if (hoveredStep != null) g.setComponentTooltipForNextFrame(font, stepTooltip(hoveredStep), mx, my);
	}

	private List<Component> stepTooltip(Planner.Step s) {
		List<Component> t = new ArrayList<>();
		t.add(Component.translatable("workstationhelper.step_left").withStyle(ChatFormatting.GRAY));
		describe(t, s.left());
		t.add(Component.translatable("workstationhelper.step_right").withStyle(ChatFormatting.GRAY));
		describe(t, s.right());
		return t;
	}

	private void describe(List<Component> t, Piece p) {
		// название самого предмета (без переименования): «Алмазный меч» или «Зачарованная книга»
		t.add(Component.literal(" ").append(new ItemStack(p.stack.getItem()).getHoverName()).withStyle(ChatFormatting.WHITE));
		for (var e : p.ench.entrySet()) {
			MutableComponent c = Component.literal("   ").append(Enchantment.getFullname(e.getKey(), e.getValue()));
			t.add(c.withStyle(ChatFormatting.AQUA));
		}
	}

	/** Поверх слотов: подсветка книг шага, на который наведена мышь. */
	private void extractFront(GuiGraphicsExtractor g, int mx, int my) {
		if (!rightShown() || hoveredStep == null) return;
		int left = acc().workstationhelper$leftPos(), top = acc().workstationhelper$topPos();
		// те же слоты, из которых клик возьмёт вещи
		for (int idx : stepSlots(hoveredStep)) {
			if (idx < 0) continue;
			Slot s = menu().getSlot(idx);
			g.fill(left + s.x, top + s.y, left + s.x + 16, top + s.y + 16, 0x8040FF40);
		}
	}

	private int wrap(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color) {
		for (var line : font.split(text, w)) {
			g.text(font, line, x, y, color, true);
			y += 10;
		}
		return y;
	}

	// ======================== мышь ========================

	/** true — клик наш (в окно наковальни не передаётся). */
	private boolean click(double mx, double my, int button) {
		if (fits && onToggle(mx, my)) {
			cfg().sidePanels = !cfg().sidePanels;
			Vanilla.clickSound();
			Config.save();
			updateButtons();
			return true;
		}
		if (!shown()) return false;
		if (goBtn.visible && goBtn.isMouseOver(mx, my)) return false;
		if (createBtn.visible && createBtn.isMouseOver(mx, my)) return false;
		if (rightShown() && linkW > 0 && inside(mx, my, linkX, linkY - 1, linkW, 10)) {
			showAnyway();
			Vanilla.clickSound();
			return true;
		}
		if (rightShown() && inside(mx, my, pinX(), pinY(), 12, 12)) {
			togglePin();
			Vanilla.clickSound();
			return true;
		}
		if (leftShown() && overLeft(mx, my)) {
			if (pinned == null && listShown()) clickLeft(mx, my);
			return true;
		}
		if (rightShown() && inside(mx, my, rx, py, pw, ph)) {
			// в 26.3 номера кнопок мыши другие (левая = 1), поэтому только через константу игры
			if (button == InputConstants.MOUSE_BUTTON_LEFT && my >= stepsTop && my < stepsBottom) {
				// шаг ищем по месту клика: подсветка наведения обновляется только при отрисовке
				for (StepBox b : stepBoxes) {
					if (my >= b.y() && my < b.y() + STEP_ROW) {
						Vanilla.clickSound();
						fill(b.step());
						break;
					}
				}
			}
			return true;
		}
		return false;
	}

	private void clickLeft(double mx, double my) {
		if (item == null || my < listTop) return;
		int i = (int) ((my - listTop) / ROW);
		if (i >= visibleRows() || leftScroll + i >= rows.size() || mx >= lx + PAD + rowWidth()) return;
		Row r = rows.get(leftScroll + i);
		if (blocked(r.ench())) return;
		int y = listTop + i * ROW;
		int[] xs = levelXs(r, lx + PAD + rowWidth());
		for (int l = 1; l <= r.max(); l++) {
			int nw = font.width(Component.translatable("enchantment.level." + l));
			if (inside(mx, my, xs[l] - 2, y, nw + 4, ROW)) {
				if (l > item.ench.getOrDefault(r.ench(), 0)) {
					Vanilla.clickSound();
					pick(r, l);
				}
				return;
			}
		}
		// клик по названию — убрать из плана
		if (mx < xs[1] - 2 && selection.remove(r.ench()) != null) {
			Vanilla.clickSound();
			planned = false;
			savePicks();
		}
	}

	private boolean overPanel(double mx, double my) {
		return (leftShown() && overLeft(mx, my)) || (rightShown() && inside(mx, my, rx, py, pw, ph));
	}

	private boolean scroll(double mx, double my, double v) {
		if (!shown()) return false;
		int d = (int) -Math.signum(v);
		if (leftShown() && overLeft(mx, my)) {
			if (pinned != null) neededScroll += d;
			else leftScroll += d;
			return true;
		}
		if (rightShown() && inside(mx, my, rx, py, pw, ph)) {
			rightScroll += d * STEP_ROW;
			return true;
		}
		return false;
	}

	// ======================== творческий режим: создать предмет ========================

	private boolean creative() {
		return mc.player != null && mc.player.hasInfiniteMaterials();
	}

	/**
	 * Кладёт в инвентарь копию предмета из левого слота с выбранными зачарованиями — так же, как предметы
	 * выдаёт творческая вкладка. Работает только в творческом режиме (в выживании кнопки нет).
	 */
	private void createItem() {
		if (!creative() || mc.gameMode == null || item == null || selection.isEmpty()) return;
		Inventory inv = mc.player.getInventory();
		int free = inv.getFreeSlot();
		if (free < 0) {
			error(Component.translatable("workstationhelper.create.full"));
			return;
		}
		ItemStack out = lastItem.is(Items.BOOK) ? new ItemStack(Items.ENCHANTED_BOOK) : lastItem.copyWithCount(1);
		net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(out, m -> selection.forEach(m::set));
		// новый предмет — без штрафа прошлых наковален
		out.remove(net.minecraft.core.component.DataComponents.REPAIR_COST);
		inv.setItem(free, out);
		// номер слота в окне инвентаря: хотбар — 36..44, остальное — 9..35
		int menuSlot = free < 9 ? InventoryMenu.USE_ROW_SLOT_START + free : free;
		// серверу — отдельная копия: пакет уходит не сразу, а предмет в инвентаре могут сразу взять мышкой
		mc.gameMode.handleCreativeModeItemAdd(out.copy(), menuSlot);
		mc.gui.chatListener().handleOverlay(Component.translatable("workstationhelper.create.done").withStyle(ChatFormatting.GREEN));
	}

	// ======================== перекладывание книг в наковальню ========================

	/** Есть ли вещь: в инвентаре, в наковальне или прямо на курсоре (когда её перетаскивают мышкой). */
	private boolean has(Piece p) {
		return findSlot(p) >= 0 || p.matches(menu().getCarried());
	}

	/** Слот инвентаря, где лежит такая вещь, или -1. */
	private int findSlot(Piece p) {
		if (p.slot >= 0 && p.matches(menu().getSlot(p.slot).getItem())) return p.slot;
		for (Slot s : menu().slots) {
			if ((s.container instanceof Inventory || s.index == AnvilMenu.INPUT_SLOT || s.index == AnvilMenu.ADDITIONAL_SLOT) && p.matches(s.getItem())) return s.index;
		}
		return -1;
	}

	/**
	 * Из каких слотов брать вещи шага: [левая, правая]. Одна и та же логика для подсветки и для клика,
	 * поэтому подсвечивается ровно та книга, которую возьмёт мод. Две одинаковые книги — всегда разные ячейки.
	 */
	private int[] stepSlots(Planner.Step s) {
		int l = slotFor(s.left(), AnvilMenu.INPUT_SLOT, -1);
		int r = slotFor(s.right(), AnvilMenu.ADDITIONAL_SLOT, l);
		return new int[] {l, r};
	}

	/** Слот для вещи: сначала тот, где она уже лежит в наковальне, потом слот при расчёте, потом первый подходящий. */
	private int slotFor(Piece p, int target, int exclude) {
		AnvilMenu m = menu();
		if (target != exclude && p.matches(m.getSlot(target).getItem())) return target;
		if (p.slot >= 0 && p.slot != exclude && p.matches(m.getSlot(p.slot).getItem())) return p.slot;
		for (Slot s : m.slots) {
			boolean ok = s.container instanceof Inventory || s.index == AnvilMenu.INPUT_SLOT || s.index == AnvilMenu.ADDITIONAL_SLOT;
			if (ok && s.index != exclude && p.matches(s.getItem())) return s.index;
		}
		return -1;
	}

	private void fill(Planner.Step s) {
		if (mc.gameMode == null || mc.player == null || done.contains(s)) return;
		AnvilMenu m = menu();
		int[] slots = stepSlots(s);
		if (slots[0] < 0 || slots[1] < 0) {
			error(Component.translatable("workstationhelper.fill.missing"));
			return;
		}
		if (!m.getCarried().isEmpty()) {
			error(Component.translatable("workstationhelper.fill.busy"));
			return;
		}
		int id = m.containerId;
		// всё лишнее из наковальни (например меч, когда шаг — соединить книги) убираем в инвентарь
		for (int slot : new int[] {AnvilMenu.INPUT_SLOT, AnvilMenu.ADDITIONAL_SLOT}) {
			ItemStack in = m.getSlot(slot).getItem();
			boolean neededHere = slot == AnvilMenu.INPUT_SLOT ? s.left().matches(in) : s.right().matches(in);
			if (!in.isEmpty() && !neededHere) mc.gameMode.handleContainerInput(id, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
		}
		if (!m.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty() && !s.left().matches(m.getSlot(AnvilMenu.INPUT_SLOT).getItem())
			|| !m.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty() && !s.right().matches(m.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem())) {
			error(Component.translatable("workstationhelper.fill.full"));
			return;
		}
		// после уборки слоты могли поменяться — подбираем заново тем же способом, что и подсветка
		slots = stepSlots(s);
		if (slots[0] < 0 || slots[1] < 0) {
			error(Component.translatable("workstationhelper.fill.missing"));
			return;
		}
		move(slots[0], AnvilMenu.INPUT_SLOT);
		move(slots[1], AnvilMenu.ADDITIONAL_SLOT);
	}

	/** Переносит вещь из слота from в слот наковальни target (если она уже там — ничего). */
	private void move(int from, int target) {
		if (from == target) return;
		int id = menu().containerId;
		mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, mc.player);
		mc.gameMode.handleContainerInput(id, target, 0, ContainerInput.PICKUP, mc.player);
	}

	private void error(Component text) {
		mc.gui.chatListener().handleOverlay(text.copy().withStyle(ChatFormatting.RED));
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 1.0f));
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
