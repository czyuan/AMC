package nlp;

import java.util.ArrayList;

import utility.ItemWithValue;

/**
 * A topic is represented by its top words.
 */
public class Topic {
	// The top words with their original probabilities.
	public ArrayList<ItemWithValue> topWordList = null;
	public String domain = null; // Domain name.

	// public Topic(ArrayList<ItemWithValue> topWordList2) {
	// topWordList = topWordList2;
	// }

	public Topic(ArrayList<ItemWithValue> topWordList2, String domain2) {
		topWordList = topWordList2;
		domain = domain2;
	}

	/**
	 * Check if a word is in (top word list of) the topic .
	 */
	public boolean containsWordstr(String wordstr) {
		for (ItemWithValue iwv : topWordList) {
			if (iwv.getIterm().equals(wordstr)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (ItemWithValue iwv : topWordList) {
			String word = iwv.getIterm().toString();
			sb.append(word);
			sb.append(' ');
		}
		return sb.toString().trim();
	}

}
