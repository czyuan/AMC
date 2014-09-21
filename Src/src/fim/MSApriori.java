package fim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map.Entry;

/**
 * This class implements the Apriori algorithm with multiple minimum support.
 * This is a basic implementation as we only need to consider the item set of
 * size 2. If efficiency is a concern, a better implementation or mining
 * algorithm should be used.
 * 
 * @author Zhiyuan (Brett) Chen
 * @email czyuanacm@gmail.com
 */
public class MSApriori {
	private Transactions transactions = null;
	private double multipleMiniSupportPercentage = 0;
	private int uniformMinimumSupport = 0;
	private int MIS[] = null;
	private double supportDifferenceConstraint = 0;

	public MSApriori(Transactions transactions2, int uniformMinimumSupport2,
			double multipleMiniSupportPercentage2,
			double supportDifferenceConstraint2) {
		transactions = transactions2;
		uniformMinimumSupport = uniformMinimumSupport2;
		multipleMiniSupportPercentage = multipleMiniSupportPercentage2;
		supportDifferenceConstraint = supportDifferenceConstraint2;
		MIS = new int[transactions.mpItemToCount.size()];
	}

	/**
	 * Run MS-Apriori algorithm, return the frequent item sets.
	 */
	public ArrayList<ItemSet> runToSizeK(int K) {
		// Record the MIS for each item.
		ArrayList<Integer> M = new ArrayList<Integer>(); // Record all the
															// items.
		for (Entry<Integer, Integer> entry : transactions.mpItemToCount
				.entrySet()) {
			int item = entry.getKey();
			M.add(item);
			int support = entry.getValue();
			int mis = Math.max(uniformMinimumSupport,
					(int) Math.ceil(support * multipleMiniSupportPercentage));
			MIS[item] = mis;
		}
		// Sort all the items by MIS.
		Comparator<Integer> itemComparatorByMIS = new Comparator<Integer>() {
			public int compare(Integer item1, Integer item2) {
				// Ordered by MIS.
				int compare = MIS[item1] - MIS[item2];
				if (compare == 0) {
					// If MIS is the same, we use the lexical ordering.
					return item1 - item2;
				} else if (compare < 0) {
					return -1;
				} else {
					return 1;
				}
			}
		};
		Collections.sort(M, itemComparatorByMIS);

		// Create frequent1: the frequent list of itemset with one item only.
		ArrayList<Integer> frequent1 = new ArrayList<Integer>();
		int minMIS = -1;
		for (int i = 0; i < M.size(); ++i) {
			int item = M.get(i);
			if (minMIS < 0) {
				if (transactions.mpItemToCount.get(item) >= MIS[item]) {
					frequent1.add(item);
					minMIS = MIS[item];
				}
			} else {
				if (transactions.mpItemToCount.get(item) >= minMIS) {
					frequent1.add(item);
				}
			}
		}

		if (frequent1.size() == 0) {
			return new ArrayList<ItemSet>(); // No frequent patterns found.
		}

		// Sort each transaction by MIS.
		for (ArrayList<Integer> transaction : transactions.transactionList) {
			Collections.sort(transaction, itemComparatorByMIS);
		}

		ArrayList<ItemSet> frequents = null;
		for (int k = 2; k <= K; ++k) {
			ArrayList<ItemSet> candidates = null;
			if (k == 2) {
				candidates = generateCandidateOfSize2(frequent1);
			} else {
				candidates = generateCandidateOfSizeK(frequents);
			}
			frequents = pruneCandiatesBySupport(candidates);
			if (frequents.size() == 0) {
				break;
			}
		}

		return frequents;
	}

	/**
	 * Generate candidates from F1 (i.e., frequent item set with 1 item only).
	 */
	private ArrayList<ItemSet> generateCandidateOfSize2(
			ArrayList<Integer> frequent1) {
		ArrayList<ItemSet> candidates = new ArrayList<ItemSet>();
		for (int i = 0; i < frequent1.size(); ++i) {
			int item1 = frequent1.get(i);
			int support1 = transactions.mpItemToCount.get(item1);
			for (int j = i + 1; j < frequent1.size(); ++j) {
				int item2 = frequent1.get(j);
				int support2 = transactions.mpItemToCount.get(item2);
				if (support2 >= MIS[item1]
						&& 1.0 * Math.abs(support1 - support2)
								/ transactions.size() <= supportDifferenceConstraint) {
					// Note that MIS[item1] <= MIS[item2].
					ArrayList<Integer> items = new ArrayList<Integer>();
					items.add(item1);
					items.add(item2);
					candidates.add(new ItemSet(items));
				}
			}
		}
		// No need to check the subsets of candidates.
		return candidates;
	}

	/**
	 * Generate candidates Cks from Fks (which is F_{k-1}).
	 */
	private ArrayList<ItemSet> generateCandidateOfSizeK(
			ArrayList<ItemSet> frequents) {
		ArrayList<ItemSet> candidates = new ArrayList<ItemSet>();
		for (int i = 0; i < frequents.size(); ++i) {
			ItemSet fk_i = frequents.get(i);
			int support_i = transactions.mpItemToCount.get(fk_i);
			for (int j = i + 1; j < frequents.size(); ++j) {
				ItemSet fk_j = frequents.get(j);
				if (fk_i.sharesPrefixExceptLastOne(fk_j)) {
					int support_j = transactions.mpItemToCount.get(fk_j);
					if (1.0 * Math.abs(support_i - support_j)
							/ transactions.size() <= supportDifferenceConstraint) {
						ItemSet candidate = mergePrefixItemSet(fk_i, fk_j);
						if (checkAllSubsetsAreFrequent(candidate, frequents)) {
							candidates.add(candidate);
						}
					}
				}
			}
		}
		return candidates;
	}

	/**
	 * Merge two itemsets that share the prefix except the last one.
	 */
	private ItemSet mergePrefixItemSet(ItemSet fk_i, ItemSet fk_j) {
		ArrayList<Integer> items = new ArrayList<Integer>();
		int len = fk_i.size();
		for (int i = 0; i < len - 1; ++i) {
			items.add(fk_i.get(i));
		}
		if (MIS[fk_i.get(len - 1)] < MIS[fk_j.get(len - 1)]) {
			items.add(fk_i.get(len - 1));
			items.add(fk_j.get(len - 1));
		}
		if (MIS[fk_i.get(len - 1)] > MIS[fk_j.get(len - 1)]) {
			items.add(fk_j.get(len - 1));
			items.add(fk_i.get(len - 1));
		} else {
			// Sorted by lexically.
			if (fk_i.get(len - 1) < fk_i.get(len - 1)) {
				items.add(fk_i.get(len - 1));
				items.add(fk_j.get(len - 1));
			} else {
				items.add(fk_j.get(len - 1));
				items.add(fk_i.get(len - 1));
			}
		}
		return new ItemSet(items);
	}

	/**
	 * MSApriori version of checking if all subsets of candidate are in
	 * frequents. Since frequents are sorted, we can use binary search here.
	 */
	private boolean checkAllSubsetsAreFrequent(ItemSet candidate,
			ArrayList<ItemSet> frequents) {
		for (int removePosition = 0; removePosition < candidate.size(); ++removePosition) {
			// Check the subset of candidate (remove the item in
			// removePosition).
			if (removePosition != 1
					|| MIS[candidate.get(0)] == MIS[candidate.get(1)]) {
				// We check the subset when we have the first item or MIS[c[0]]
				// = MIS[c[1]]. This is because the c[1..k] may not exist while
				// c[0..k] may be valid.
				int left = 0;
				int right = frequents.size();
				boolean found = false;
				while (left <= right) {
					int mid = (left + right) >> 1;
					int compareValue = candidate.compareToExcludingIndex(
							frequents.get(mid), removePosition);
					if (compareValue < 0) {
						right = mid - 1;
					} else if (compareValue > 0) {
						left = mid + 1;
					} else {
						found = true;
						break;
					}
				}
				if (!found) {
					// This subset is not in the frequents, so this candidate is
					// not valid.
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Remove those candidates that do not meet the support threshold.
	 */
	private ArrayList<ItemSet> pruneCandiatesBySupport(
			ArrayList<ItemSet> candidates) {
		ArrayList<ItemSet> frequents = new ArrayList<ItemSet>();
		for (ItemSet candidate : candidates) {
			candidate.support = getItemSetSupport(transactions, candidate.items);
			if (candidate.support >= MIS[candidate.get(0)]) {
				frequents.add(candidate);
			}
		}
		return frequents;
	}

	/**
	 * Get the support of an itemset in the transactions.
	 */
	private int getItemSetSupport(Transactions transactions,
			ArrayList<Integer> items) {
		int count = 0;
		for (ArrayList<Integer> transaction : transactions.transactionList) {
			if (transactionContainsItemSet(transaction, items)) {
				++count;
			}
		}
		return count;
	}

	/**
	 * O(n) algorithm to compare as both list are sorted already.
	 */
	private boolean transactionContainsItemSet(ArrayList<Integer> transaction,
			ArrayList<Integer> items) {
		int i = 0;
		int j = 0;
		for (; i < transaction.size() && j < items.size();) {
			int item_i = transaction.get(i);
			int item_j = items.get(j);
			if (item_i == item_j) {
				++i;
				++j;
			} else if (MIS[item_i] <= MIS[item_j]) {
				++i;
			} else {
				return false;
			}
		}
		return j == items.size();
	}
}
