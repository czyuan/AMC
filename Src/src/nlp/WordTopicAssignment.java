package nlp;

/**
 * This class records the topic assignment of a word in a document.
 */
public class WordTopicAssignment{
	public int wordId = -1;
	public int documentId = -1;
	public int wordIndex = -1; // The index of this word in this document.
	public int topicId = -1;

	public WordTopicAssignment(int wordId2, int documentId2, int wordIndex2,
			int topicId2) {
		wordId = wordId2;
		documentId = documentId2;
		wordIndex = wordIndex2;
		topicId = topicId2;
	}

	@Override
	public boolean equals(Object obj) {
		WordTopicAssignment wta = (WordTopicAssignment) obj;
		return this.wordId == wta.wordId && this.documentId == wta.documentId
				&& this.wordIndex == wta.wordIndex;
	}
}
