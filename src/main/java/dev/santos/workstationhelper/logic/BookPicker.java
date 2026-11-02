package dev.santos.workstationhelper.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Режим «Мои книги»: подбирает книги из инвентаря под выбранные уровни.
 * Нужный уровень берётся из книги с таким же или большим уровнем, либо из двух книг на уровень ниже
 * (наковальня склеит их: Острота III + Острота III = Острота IV).
 */
public final class BookPicker {
	public record Missing(Holder<Enchantment> ench, int level) {
	}

	public record Result(List<Piece> books, List<Missing> missing) {
	}

	private BookPicker() {
	}

	public static Result pick(Map<Holder<Enchantment>, Integer> targets, List<Piece> inventory) {
		// книги, на которых нет ничего лишнего (всё — среди выбранного и не выше нужного уровня)
		List<Piece> usable = new ArrayList<>();
		for (Piece p : inventory) {
			boolean ok = !p.ench.isEmpty();
			for (var e : p.ench.entrySet()) {
				Integer want = targets.get(e.getKey());
				if (want == null) ok = false;
			}
			if (ok) usable.add(p);
		}
		List<Piece> chosen = new ArrayList<>();
		List<Missing> missing = new ArrayList<>();
		// сначала дорогие зачарования — их выгоднее закрыть одной книгой
		List<Map.Entry<Holder<Enchantment>, Integer>> order = new ArrayList<>(targets.entrySet());
		order.sort(Comparator.comparingInt(e -> -e.getKey().value().getAnvilCost() * e.getValue()));
		for (var t : order) {
			Holder<Enchantment> e = t.getKey();
			int want = t.getValue();
			if (reached(chosen, e) >= want) continue;
			if (!take(e, want, usable, chosen, 0)) missing.add(new Missing(e, want));
		}
		return new Result(chosen, missing);
	}

	/** Какой уровень e получится из уже выбранных книг (с учётом склейки одинаковых). */
	private static int reached(List<Piece> chosen, Holder<Enchantment> e) {
		Map<Integer, Integer> count = new LinkedHashMap<>();
		for (Piece p : chosen) {
			Integer l = p.ench.get(e);
			if (l != null) count.merge(l, 1, Integer::sum);
		}
		int best = 0;
		for (int l : count.keySet()) best = Math.max(best, l);
		// две книги одного уровня дают уровень выше
		for (var c : count.entrySet()) if (c.getValue() >= 2) best = Math.max(best, Math.min(c.getKey() + 1, e.value().getMaxLevel()));
		return best;
	}

	/** Берёт книгу с уровнем ≥ want или (до двух раз вглубь) две книги на уровень ниже. */
	private static boolean take(Holder<Enchantment> e, int want, List<Piece> usable, List<Piece> chosen, int depth) {
		Piece exact = null;
		for (Piece p : usable) {
			Integer l = p.ench.get(e);
			if (l == null || l < want || chosen.contains(p)) continue;
			// меньше лишних зачарований и меньше штраф — лучше
			if (exact == null || p.ench.size() < exact.ench.size() || (p.ench.size() == exact.ench.size() && p.penalty < exact.penalty)) exact = p;
		}
		if (exact != null) {
			chosen.add(exact);
			return true;
		}
		if (want <= 1 || depth >= 2) return false;
		List<Piece> trial = new ArrayList<>(chosen);
		if (take(e, want - 1, usable, trial, depth + 1) && take(e, want - 1, usable, trial, depth + 1)) {
			chosen.clear();
			chosen.addAll(trial);
			return true;
		}
		return false;
	}
}
