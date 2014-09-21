package nlp;

import java.util.ArrayList;

import utility.ExceptionUtility;

/**
 * A list that records the topic assignment for words in documents.
 */
public class WordTopicAssignments {
	private ArrayList<WordTopicAssignment> wordTopicAssignments = null;

	public WordTopicAssignments() {
		wordTopicAssignments = new ArrayList<WordTopicAssignment>();
	}

	public void addWordTopicAssignment(WordTopicAssignment wta) {
		ExceptionUtility.assertAsException(!wordTopicAssignments.contains(wta),
			"The list already contains this word-topic-assignment!");
		wordTopicAssignments.add(wta);
	}

	public void removeWordTopicAssignment(WordTopicAssignment wta) {
		wordTopicAssignments.remove(wta);
	}

	public WordTopicAssignment get(int index) {
		return wordTopicAssignments.get(index);
	}

	public int size() {
		return wordTopicAssignments.size();
	}
}
