package knowledge;

import java.util.ArrayList;
import java.util.PriorityQueue;

import utility.ExceptionUtility;
import fim.ItemSet;
import fim.MSApriori;
import fim.Transactions;
import nlp.Corpus;
import nlp.Topic;
import nlp.Topics;

/**
 * This class implements the functions to extract/process the knowledge
 * (must-links and cannot-links) used by AMC.
 */
public class KnowledgeExtractingAndProcessingForAMC {
	private final int MAXIMUM_ITEMSET_SIZE = 2;

	/**
	 * Extract must-links by applying frequent itemset mining with multiple
	 * minimum supports.
	 */
	public MustLinks extractMustLinks(Topics priorTopics, Corpus corpus,
			int minimumSupport, double multipleMiniSupportPercentage,
			double supportDifferenceConstraint) {
		// Convert topics into transactions.
		Transactions transactions = new Transactions(priorTopics);
		// Run MS-Apriori algorithm.
		MSApriori ms_apriori = new MSApriori(transactions, minimumSupport,
				multipleMiniSupportPercentage, supportDifferenceConstraint);
		ArrayList<ItemSet> freqItemSetList = ms_apriori
				.runToSizeK(MAXIMUM_ITEMSET_SIZE);
		// Convert 2-frequent patterns to must-links.
		MustLinks mustLinks = new MustLinks();
		if (!freqItemSetList.isEmpty()) {
			// Add into the knowledge must-links.
			for (ItemSet freqItemSet : freqItemSetList) {
				ExceptionUtility.assertAsException(freqItemSet.size() == 2,
						"The size of frequent item set should be 2!");
				String wordstr1 = transactions.mpItemToWord.get(freqItemSet
						.get(0));
				String wordstr2 = transactions.mpItemToWord.get(freqItemSet
						.get(1));
				if (corpus.vocab.containsWordstr(wordstr1)
						&& corpus.vocab.containsWordstr(wordstr2)) {
					// We remain both words when they appear in the corpus.
					int support = freqItemSet.support;
					MustLink mustlink = new MustLink(wordstr1, wordstr2,
							support);
					mustLinks.addMustLink(mustlink);
				}
			}
		}

		return mustLinks;
	}

	/**
	 * Extract cannot-links by enumerating each pair of top words under topics.
	 */
	public CannotLinks extractCannotLinks(
			ArrayList<PriorityQueue<Integer>> topWordIDList,
			Topics priorTopicsForKnowledgeExtraction, Corpus corpus,
			double supportRatioForCannotLink, int supportThresholdForCannotLink) {
		CannotLinks cannotLinks = new CannotLinks();
		for (int t = 0; t < topWordIDList.size(); ++t) {
			PriorityQueue<Integer> pqueue = topWordIDList.get(t);
			ArrayList<Integer> topWordIDs = new ArrayList<Integer>();
			// The first word has the lowest probability and the last word has
			// the highest probability.
			while (!pqueue.isEmpty()) {
				topWordIDs.add(pqueue.poll());
			}
			for (int i = topWordIDs.size() - 1; i >= 0; --i) {
				int wordid1 = topWordIDs.get(i);
				for (int j = topWordIDs.size() - 1; j >= i + 1; --j) {
					int wordid2 = topWordIDs.get(j);
					String wordstr1 = corpus.vocab.getWordstrByWordid(wordid1);
					String wordstr2 = corpus.vocab.getWordstrByWordid(wordid2);

					if (isCannotLink(wordstr1, wordstr2,
							priorTopicsForKnowledgeExtraction,
							supportRatioForCannotLink,
							supportThresholdForCannotLink)) {
						CannotLink cannotLink = new CannotLink(wordstr1,
								wordstr2);
						cannotLinks.addCannotLink(cannotLink);
					}
				}
			}
		}
		return cannotLinks;
	}

	/**
	 * Check if two words form a cannot-link.
	 */
	private boolean isCannotLink(String wordstr1, String wordstr2,
			Topics priorTopicsForKnowledgeExtraction,
			double supportRatioForCannotLink, int supportThresholdForCannotLink) {
		int coDomainFreq = 0;
		int diffDomainFreq = 0;
		int index = 0;
		while (index < priorTopicsForKnowledgeExtraction.topicList.size()) {
			String currentDomain = priorTopicsForKnowledgeExtraction.topicList
					.get(index).domain;
			int freq1 = 0;
			int freq2 = 0;
			int coFreq = 0;
			// Enumerate all topics in this domain.
			while (index < priorTopicsForKnowledgeExtraction.topicList.size()
					&& priorTopicsForKnowledgeExtraction.topicList.get(index).domain
							.equals(currentDomain)) {
				// We assume that the topics of the same domain are put together
				// in the priorTopicsForKnowledgeExtraction.
				Topic topic = priorTopicsForKnowledgeExtraction.topicList
						.get(index);
				boolean wordAppear1 = topic.containsWordstr(wordstr1);
				boolean wordAppear2 = topic.containsWordstr(wordstr2);
				if (wordAppear1 && wordAppear2) {
					++coFreq;
				} else if (wordAppear1) {
					++freq1;
				} else if (wordAppear2) {
					++freq2;
				}
				++index;
			}
			if (coFreq > 0) {
				// Both words appear in the same topic.
				++coDomainFreq;
			} else if (freq1 > 0 && freq2 > 0) {
				// Both words appear in the different topics.
				++diffDomainFreq;
			}
		}
		double ratio = 1.0 * diffDomainFreq / (diffDomainFreq + coDomainFreq);
		if (ratio >= supportRatioForCannotLink
				&& diffDomainFreq >= supportThresholdForCannotLink) {
			return true;
		} else {
			return false;
		}
	}
}
