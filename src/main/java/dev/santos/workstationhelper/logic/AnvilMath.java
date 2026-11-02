package dev.santos.workstationhelper.logic;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Цена наковальни по тем же правилам, что в игре (AnvilMenu.createResult). */
public final class AnvilMath {
	/** С этой цены игра пишет «Слишком дорого!»: один шаг — максимум 39 уровней. */
	public static final int TOO_EXPENSIVE = 40;

	public record Result(Piece piece, int cost) {
	}

	private AnvilMath() {
	}

	/**
	 * Кладём left в левый слот, right — в правый. target — сам зачаровываемый предмет (для проверки,
	 * подходит ли зачарование). Возвращает null, если ничего не перенесётся.
	 */
	public static Result combine(Piece left, Piece right, ItemStack target, boolean creative) {
		int cost = left.penalty + right.penalty;
		Map<Holder<Enchantment>, Integer> out = new LinkedHashMap<>(left.ench);
		boolean applied = false;
		for (var en : right.ench.entrySet()) {
			Holder<Enchantment> e = en.getKey();
			int l1 = out.getOrDefault(e, 0);
			int l2 = en.getValue();
			int level = l1 == l2 ? l2 + 1 : Math.max(l1, l2);
			boolean ok = left.book || creative || e.value().canEnchant(target);
			for (Holder<Enchantment> k : out.keySet()) {
				if (!k.equals(e) && !Enchantment.areCompatible(e, k)) {
					ok = false;
					cost++;
				}
			}
			if (!ok) continue;
			applied = true;
			level = Math.min(level, e.value().getMaxLevel());
			out.put(e, level);
			int c = e.value().getAnvilCost();
			if (right.book) c = Math.max(1, c / 2);
			cost += c * level;
		}
		if (!applied) return null;
		int penalty = AnvilMenu.calculateIncreasedRepairCost(Math.max(left.penalty, right.penalty));
		ItemStack icon = left.book ? Piece.bookStack(out, penalty) : left.stack;
		return new Result(new Piece(out, penalty, left.book, icon, -1), cost);
	}

	/** Сколько очков опыта нужно, чтобы с нуля набрать уровень level. */
	public static long xpForLevel(int level) {
		long l = level;
		if (l <= 16) return l * l + 6 * l;
		if (l <= 31) return (long) (2.5 * l * l - 40.5 * l + 360);
		return (long) (4.5 * l * l - 162.5 * l + 2220);
	}
}
