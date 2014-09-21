package knowledge;

import nlp.WordPair;

/**
 * This class implements the must-link.
 */

public class MustLink {
	public WordPair wordpair = null;
	public double weight = 1.0;

	public MustLink(String wordstr1_2, String wordstr2_2) {
		wordpair = new WordPair(wordstr1_2, wordstr2_2);
	}

	public MustLink(String wordstr1_2, String wordstr2_2, double weight2) {
		wordpair = new WordPair(wordstr1_2, wordstr2_2);
		weight = weight2;
	}

	public boolean sharesAWordWithMustLink(MustLink mustLink) {
		if (this.wordpair.wordstr1.equals(mustLink.wordpair.wordstr1)
				|| this.wordpair.wordstr1.equals(mustLink.wordpair.wordstr2)
				|| this.wordpair.wordstr2.equals(mustLink.wordpair.wordstr1)
				|| this.wordpair.wordstr2.equals(mustLink.wordpair.wordstr2)) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return this.wordpair.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		MustLink link = (MustLink) obj;
		return this.wordpair.setString.equals(link.wordpair.setString);
	}

	@Override
	public String toString() {
		return wordpair.toString();
	}

}
