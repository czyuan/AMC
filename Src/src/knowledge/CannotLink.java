package knowledge;

import nlp.WordPair;

/**
 * This class implements the cannot-link.
 */

public class CannotLink {
	public WordPair wordpair = null;

	public CannotLink(String wordstr1_2, String wordstr2_2) {
		// We put the words in the alphabetical order.
		if (wordstr1_2.compareTo(wordstr2_2) <= 0) {
			wordpair = new WordPair(wordstr1_2, wordstr2_2);
		} else {
			wordpair = new WordPair(wordstr2_2, wordstr1_2);
		}
	}

	@Override
	public int hashCode() {
		return this.wordpair.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		CannotLink link = (CannotLink) obj;
		return this.wordpair.setString.equals(link.wordpair.setString);
	}

	@Override
	public String toString() {
		return wordpair.toString();
	}

}
