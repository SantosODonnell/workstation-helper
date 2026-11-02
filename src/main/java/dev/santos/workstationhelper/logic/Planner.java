package dev.santos.workstationhelper.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ищет самый выгодный порядок соединения книг. Перебирает все способы (динамическое программирование
 * по наборам книг), до {@link #MAX_EXACT} книг — точно, больше — простым жадным способом.
 */
public final class Planner {
	public static final int MAX_EXACT = 10;

	public record Step(Piece left, Piece right, Piece result, int cost, boolean onItem) {
	}

	public record Plan(List<Step> steps, int totalLevels, long xpLow, long xpHigh, int finalPenalty) {
	}

	/** Узел дерева соединений. */
	private static final class Node {
		final Piece piece;
		final Node left, right;
		final int cost;
		final int levels;
		final long xp;
		final boolean hasItem;

		Node(Piece piece, boolean hasItem) {
			this.piece = piece;
			this.left = this.right = null;
			this.cost = this.levels = 0;
			this.xp = 0;
			this.hasItem = hasItem;
		}

		Node(Node l, Node r, AnvilMath.Result m) {
			this.piece = m.piece();
			this.left = l;
			this.right = r;
			this.cost = m.cost();
			this.levels = l.levels + r.levels + m.cost();
			this.xp = l.xp + r.xp + AnvilMath.xpForLevel(m.cost());
			this.hasItem = l.hasItem;
		}
	}

	private final Piece item;
	private final List<Piece> books;
	private final boolean creative;
	/** false — показать план, даже если какой-то шаг дороже 39 уровней */
	private final boolean limit;

	public Planner(Piece item, List<Piece> books, boolean creative, boolean limit) {
		this.item = item;
		this.books = books;
		this.creative = creative;
		this.limit = limit;
	}

	/** null — ни один порядок не укладывается в лимит наковальни. */
	public Plan solve() {
		if (books.isEmpty()) return new Plan(List.of(), 0, 0, 0, item.penalty);
		Node best = books.size() <= MAX_EXACT ? exact() : greedy();
		if (best == null) return null;
		List<Step> merges = new ArrayList<>(), applies = new ArrayList<>();
		collect(best, merges, applies);
		List<Step> steps = new ArrayList<>(merges);
		steps.addAll(applies);
		return new Plan(steps, best.levels, best.xp, AnvilMath.xpForLevel(best.levels), best.piece.penalty);
	}

	private boolean better(Node a, Node b) {
		if (b == null) return true;
		// меньше уровней в сумме; при равенстве — меньше опыта
		return a.levels != b.levels ? a.levels < b.levels : a.xp < b.xp;
	}

	private Node merge(Node l, Node r) {
		AnvilMath.Result m = AnvilMath.combine(l.piece, r.piece, item.stack, creative);
		// шаг дороже 39 уровней игра не даст сделать («Слишком дорого!») — такие порядки не берём ни в каком режиме
		if (m == null || (limit && m.cost() >= AnvilMath.TOO_EXPENSIVE)) return null;
		return new Node(l, r, m);
	}

	/** Для каждого набора книг храним лучший вариант для каждого итогового штрафа. */
	private Node exact() {
		int n = books.size(), full = (1 << n) - 1;
		@SuppressWarnings("unchecked")
		Map<Integer, Node>[] bookSets = new Map[1 << n];
		@SuppressWarnings("unchecked")
		Map<Integer, Node>[] itemSets = new Map[1 << n];
		for (int mask = 1; mask <= full; mask++) {
			Map<Integer, Node> cur = new HashMap<>();
			if (Integer.bitCount(mask) == 1) {
				Piece p = books.get(Integer.numberOfTrailingZeros(mask));
				cur.put(p.penalty, new Node(p, false));
			} else {
				for (int sub = (mask - 1) & mask; sub > 0; sub = (sub - 1) & mask) {
					for (Node a : bookSets[sub].values()) {
						for (Node b : bookSets[mask ^ sub].values()) keep(cur, merge(a, b));
					}
				}
			}
			bookSets[mask] = cur;
		}
		itemSets[0] = new HashMap<>(Map.of(item.penalty, new Node(item, true)));
		for (int mask = 1; mask <= full; mask++) {
			Map<Integer, Node> cur = new HashMap<>();
			for (int sub = mask; sub > 0; sub = (sub - 1) & mask) {
				Map<Integer, Node> before = itemSets[mask ^ sub];
				if (before == null) continue;
				for (Node a : before.values()) {
					for (Node b : bookSets[sub].values()) keep(cur, merge(a, b));
				}
			}
			itemSets[mask] = cur;
		}
		Node best = null;
		for (Node x : itemSets[full].values()) if (better(x, best)) best = x;
		return best;
	}

	private void keep(Map<Integer, Node> map, Node x) {
		if (x == null) return;
		Node old = map.get(x.piece.penalty);
		if (better(x, old)) map.put(x.piece.penalty, x);
	}

	/** Много книг: соединяем попарно самые дорогие с самыми дешёвыми, потом кладём на предмет. */
	private Node greedy() {
		List<Node> layer = new ArrayList<>();
		for (Piece p : books) layer.add(new Node(p, false));
		while (layer.size() > 1) {
			layer.sort(Comparator.comparingInt(x -> -weight(x.piece)));
			List<Node> next = new ArrayList<>();
			int i = 0, j = layer.size() - 1;
			while (i < j) {
				Node m = merge(layer.get(i), layer.get(j));
				if (m == null) m = merge(layer.get(j), layer.get(i));
				if (m == null) return null;
				next.add(m);
				i++;
				j--;
			}
			if (i == j) next.add(layer.get(i));
			layer = next;
		}
		return merge(new Node(item, true), layer.getFirst());
	}

	private static int weight(Piece p) {
		int w = 0;
		for (var e : p.ench.entrySet()) w += e.getKey().value().getAnvilCost() * e.getValue();
		return w;
	}

	/** Обходит дерево: сначала соединения книг, потом шаги на предмет — в порядке выполнения. */
	private static void collect(Node n, List<Step> merges, List<Step> applies) {
		if (n.left == null) return;
		collect(n.left, merges, applies);
		collect(n.right, merges, applies);
		Step s = new Step(n.left.piece, n.right.piece, n.piece, n.cost, n.hasItem);
		(n.hasItem ? applies : merges).add(s);
	}
}
