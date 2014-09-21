package knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * This class implements knowledge in the forms of cannot-links.
 */
public class CannotLinks implements Iterable<CannotLink> {
	public ArrayList<CannotLink> cannotlinkList = null;
	public HashSet<CannotLink> uniqueCannotLinks = null;
	public Map<String, HashSet<CannotLink>> wordidToCannotlinkListMap = null;

	public CannotLinks() {
		cannotlinkList = new ArrayList<CannotLink>();
		uniqueCannotLinks = new HashSet<CannotLink>();
		wordidToCannotlinkListMap = new HashMap<String, HashSet<CannotLink>>();
	}

	public void addCannotLink(CannotLink cannotLink) {
		if (!uniqueCannotLinks.contains(cannotLink)) {
			// We only add new cannot-link.
			uniqueCannotLinks.add(cannotLink);
			cannotlinkList.add(cannotLink);

			addWordIntoMap(cannotLink.wordpair.wordstr1, cannotLink);
			addWordIntoMap(cannotLink.wordpair.wordstr2, cannotLink);
		}
	}

	/**
	 * Add the wordstr and its cannot-link into the map.
	 */
	private void addWordIntoMap(String wordstr, CannotLink cannotLink) {
		if (!wordidToCannotlinkListMap.containsKey(wordstr)) {
			wordidToCannotlinkListMap.put(wordstr, new HashSet<CannotLink>());
		}
		wordidToCannotlinkListMap.get(wordstr).add(cannotLink);
	}

	public CannotLink getCannotLink(int index) {
		assert (index < this.size() && index >= 0) : "Index is not correct!";
		return cannotlinkList.get(index);
	}

	/**
	 * Get the list of cannot-links that contain this word.
	 */
	// Change it to ArrayList.
	public HashSet<CannotLink> getCannotLinkListGivenWordstr(String wordstr) {
		if (!wordidToCannotlinkListMap.containsKey(wordstr)) {
			return new HashSet<CannotLink>();
		} else {
			return wordidToCannotlinkListMap.get(wordstr);
		}
	}

	public int size() {
		return cannotlinkList.size();
	}

	@Override
	public String toString() {
		StringBuilder sbCannotLinks = new StringBuilder();
		for (CannotLink cannotLink : cannotlinkList) {
			sbCannotLinks.append(cannotLink.toString());
			sbCannotLinks.append(System.getProperty("line.separator"));
		}
		return sbCannotLinks.toString();
	}

	@Override
	public Iterator<CannotLink> iterator() {
		return cannotlinkList.iterator();
	}
}
