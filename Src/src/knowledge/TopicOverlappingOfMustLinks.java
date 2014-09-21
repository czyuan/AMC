package knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import utility.ItemWithValue;
import nlp.Topic;
import nlp.Topics;

/**
 * Record the list of topics that each must-link is extracted from, used to
 * compute how much topic overlapping two must-links have to construct the
 * must-link graph.
 */
public class TopicOverlappingOfMustLinks {
	private Topics topics = null;
	private Map<String, ArrayList<Topic>> mpMustLinkToTopicList = null;

	public TopicOverlappingOfMustLinks(Topics topics2) {
		topics = topics2;

		mpMustLinkToTopicList = new HashMap<String, ArrayList<Topic>>();
		for (Topic topic : topics) {
			ArrayList<ItemWithValue> itemList = topic.topWordList;
			for (int i = 1; i < itemList.size(); ++i) {
				String wordStr1 = itemList.get(i).getIterm().toString();
				for (int j = 0; j < i; ++j) {
					String wordStr2 = itemList.get(j).getIterm().toString();
					String linkedWordstr = getLinkedWords(wordStr1, wordStr2);
					if (!mpMustLinkToTopicList.containsKey(linkedWordstr)) {
						mpMustLinkToTopicList.put(linkedWordstr,
								new ArrayList<Topic>());
					}
					mpMustLinkToTopicList.get(linkedWordstr).add(topic);
				}
			}
		}
	}

	public int getTopicOverlappingCount(MustLink mustLink1, MustLink mustLink2) {
		ArrayList<Topic> topicList1 = null;
		ArrayList<Topic> topicList2 = null;

		String linkedWord1 = getLinkedWords(mustLink1);
		String linkedWord2 = getLinkedWords(mustLink2);

		if (mpMustLinkToTopicList.containsKey(linkedWord1)) {
			topicList1 = mpMustLinkToTopicList.get(linkedWord1);
		}
		if (mpMustLinkToTopicList.containsKey(linkedWord2)) {
			topicList2 = mpMustLinkToTopicList.get(linkedWord2);
		}
		// The weight of the must-link is support, and thus the following two
		// statements should be true.
		assert (Math.abs(mustLink1.weight - topicList1.size()) < 1e-6);
		assert (Math.abs(mustLink2.weight - topicList2.size()) < 1e-6);

		int topicOverlappingCount = 0;
		for (Topic topic1 : topicList1) {
			for (Topic topic2 : topicList2) {
				if (topic1 == topic2) {
					++topicOverlappingCount;
				}
			}
		}

		return topicOverlappingCount;
	}

	public String getLinkedWords(MustLink mustLink) {
		return getLinkedWords(mustLink.wordpair.wordstr1,
				mustLink.wordpair.wordstr2);
	}

	public String getLinkedWords(String wordStr1, String wordStr2) {
		if (wordStr1.compareTo(wordStr2) <= 0) {
			return wordStr1 + "_" + wordStr2;
		} else {
			return wordStr2 + "_" + wordStr1;
		}
	}
}
