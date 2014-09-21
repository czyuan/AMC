package model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

import knowledge.CannotLink;
import knowledge.CannotLinks;
import knowledge.KnowledgeExtractingAndProcessingForAMC;
import knowledge.MustLink;
import knowledge.MustLinks;
import knowledge.TopicOverlappingOfMustLinks;
import nlp.Corpus;
import nlp.Topics;
import nlp.WordTopicAssignment;
import nlp.WordTopicAssignments;
import utility.ArrayAllocationAndInitialization;
import utility.ExceptionUtility;
import utility.FileReaderAndWriter;
import utility.InverseTransformSampler;

/**
 * This implements the AMC (topic modeling with Automatically generated
 * Must-links and Cannot-links) model proposed in (Chen and Liu, KDD 2014).
 * 
 * @author Zhiyuan (Brett) Chen
 * @email czyuanacm@gmail.com
 */
public class AMC extends TopicModel {
	/******************* Hyperparameters *********************/
	// The hyperparameter for the document-topic distribution.
	// alpha is in the variable param in TopicModel.
	private double tAlpha = 0;
	// The hyperparameter for the topic-word distribution.
	// beta is in the variable param in TopicModel.
	private double vBeta = 0;

	/******************* Posterior distributions *********************/
	private double[][] theta = null; // Document-topic distribution, size D * T.
	private double[][] thetasum = null; // Cumulative document-topic
										// distribution, size
										// D * T.
	private double[][] phi = null; // Topic-word distribution, size T * V.
	private double[][] phisum = null; // Cumulative topic-word distribution,
										// size T * V.
	// Number of times to add the sum arrays, such as thetasum and phisum.
	public int numstats = 0;

	/******************* Temp variables while sampling *********************/
	// z is defined in the superclass TopicModel.
	// private int[][] z = null; // Topic assignments for each word.
	// ndt[d][t]: the counts of document d having topic t.
	private double[][] ndt = null;
	// ndsum[d]: the counts of document d having any topic.
	private double[] ndsum = null;
	// ntw[t][w]: the counts of word w appearing under topic t.
	private double[][] ntw = null;
	// ntsum[t]: the counts of any word appearing under topic t.
	private double[] ntsum = null;

	// wtaOfWordUnderTopic[t][w]: the actual positions (document index and word
	// index)
	// of word w assigned to topic t.
	private WordTopicAssignments[][] wtaOfWordUnderTopic = null;

	private Topics priorTopicsForKnowledgeExtraction = null;

	/******************* Knowledge *********************/
	// Must-Links.
	private MustLinks mustLinks = null;
	private Map<MustLink, Integer> mpMustLinkToMustLinkId = null;
	private Map<Integer, MustLink> mpMustLinkIdToMustLink = null;
	private Map<Integer, Double> mpMustLinkIdToGPUValue = null;
	// The constructed must-link graph.
	private ArrayList<ArrayList<Integer>> mustLinkGraph = null;
	// Record the sampled must-link list for each word in each document:
	// sampledMustLinkListForEachWord.get(d).get(n);
	private ArrayList<ArrayList<ArrayList<MustLink>>> sampledMustLinkListForEachWord = null;
	// Cannot-Links.
	private CannotLinks cannotLinks = null;

	/**
	 * Create a new topic model with all variables initialized. The z[][] is
	 * randomly assigned.
	 */
	public AMC(Corpus corpus2, ModelParameters param2) {
		super(corpus2, param2);

		/******************* Hyperparameters *********************/
		tAlpha = param.T * param.alpha;
		vBeta = param.V * param.beta;

		// Allocate memory for temporary variables and initialize their
		// values.
		allocateMemoryForTempVariables();

		// Get the prior topic (p-topic) set.
		priorTopicsForKnowledgeExtraction = new Topics();
		for (TopicModel tm : param.topicModelList_LDA_KnowledgeFrom) {
			if (!tm.corpus.domain.equals(param.domain)) {
				// Knowledge is extracted from the domain other than the
				// current domain.
				// Knowledge is extracted from the top topical words under
				// topics, i.e., only top topical words are remained in the
				// priorTopicsForKnowledgeExtraction.
				Topics topicsOfDomain = tm
						.getTopics(param.numberOfTopWordsUnderPriorTopicsForKnowledgeExtraction);
				priorTopicsForKnowledgeExtraction.addTopics(topicsOfDomain);
			}
		}

		if (param.useMustLinkInAMC) {
			// Extract must-links and build must-link graph.
			KnowledgeExtractingAndProcessingForAMC kepAMC = new KnowledgeExtractingAndProcessingForAMC();
			mustLinks = kepAMC.extractMustLinks(
					priorTopicsForKnowledgeExtraction, corpus,
					param.uniformMinimumSupport,
					param.multipleMiniSupportPercentage,
					param.supportDifferenceConstraint);

			buildMustLinkGraph();
		}

		// Initialize the first status of Markov chain using topic
		// assignments from the last iteration topic model result.
		TopicModel topicmodel_currentDomain = findCurrentDomainTopicModel(param.topicModelList_LDA_SameSetting);
		initializeFirstMarkovChainUsingExistingZ(topicmodel_currentDomain.z);
	}

	/**
	 * Create a new topic model with all variables initialized. The z[][] is
	 * assigned to the value loaded from other models.
	 */
	public AMC(Corpus corpus2, ModelParameters param2, int[][] z2,
			double[][] twdist) {
		super(corpus2, param2);
		tAlpha = param.T * param.alpha;
		vBeta = param.V * param.beta;
		// Allocate memory for temporary variables and initialize their
		// values.
		allocateMemoryForTempVariables();
		// Assign z2 to z.
		z = z2; // Here we do not call initializeFirstMarkovChainUsingExistingZ
				// because we do not load the knowledge.
		// Assign Topic-Word distribution.
		phi = twdist;
	}

	// ------------------------------------------------------------------------
	// Memory Allocation and Initialization
	// ------------------------------------------------------------------------

	/**
	 * Allocate memory for temporary variables and initialize their values. Note
	 * that z[][] and y[][] are not created in this function, but in the
	 * function initializeFirstMarkovChainRandomly().
	 * 
	 * We Allocate gamma, eta, etasum, ntsw specifically to save the memory. For
	 * each must-link ml, we only allocate the size of words inside it.
	 */
	private void allocateMemoryForTempVariables() {
		/******************* Posterior distributions *********************/
		theta = ArrayAllocationAndInitialization.allocateAndInitialize(theta,
				param.D, param.T);
		phi = ArrayAllocationAndInitialization.allocateAndInitialize(phi,
				param.T, param.V);
		if (param.sampleLag > 0) {
			thetasum = ArrayAllocationAndInitialization.allocateAndInitialize(
					thetasum, param.D, param.T);
			phisum = ArrayAllocationAndInitialization.allocateAndInitialize(
					phisum, param.T, param.V);
			numstats = 0;
		}

		/******************* Temp variables while sampling *********************/
		ndt = ArrayAllocationAndInitialization.allocateAndInitialize(ndt,
				param.D, param.T);
		ndsum = ArrayAllocationAndInitialization.allocateAndInitialize(ndsum,
				param.D);
		ntw = ArrayAllocationAndInitialization.allocateAndInitialize(ntw,
				param.T, param.V);
		ntsum = ArrayAllocationAndInitialization.allocateAndInitialize(ntsum,
				param.T);

		wtaOfWordUnderTopic = new WordTopicAssignments[param.T][param.V];
		for (int t = 0; t < param.T; ++t) {
			for (int w = 0; w < param.V; ++w) {
				wtaOfWordUnderTopic[t][w] = new WordTopicAssignments();
			}
		}

		/******************* Knowledge *********************/
		if (docs != null) {
			sampledMustLinkListForEachWord = new ArrayList<ArrayList<ArrayList<MustLink>>>();
			for (int d = 0; d < param.D; ++d) {
				sampledMustLinkListForEachWord
						.add(new ArrayList<ArrayList<MustLink>>());
				for (int n = 0; n < docs[d].length; ++n) {
					sampledMustLinkListForEachWord.get(d).add(
							new ArrayList<MustLink>());
				}
			}
		}
	}

	/**
	 * Initialized the first status of Markov chain using topic assignments from
	 * the LDA results.
	 */
	private void initializeFirstMarkovChainUsingExistingZ(int[][] z2) {
		z = new int[param.D][];
		for (int d = 0; d < param.D; ++d) {
			int N = docs[d].length;
			z[d] = new int[N];

			for (int n = 0; n < N; ++n) {
				int word = docs[d][n];
				int topic = z2[d][n];
				z[d][n] = topic;

				updateCount(d, n, topic, word, +1);
			}
		}
	}

	/**
	 * There are several main steps:
	 * 
	 * 1. Run a certain number of Gibbs Sampling sweeps.
	 * 
	 * 2. Compute the posterior distributions.
	 */
	@Override
	public void run() {
		// 1. Run a certain number of Gibbs Sampling sweeps.
		runGibbsSampling();
		// 2. Compute the posterior distributions.
		computePosteriorDistribution();
	}

	// ------------------------------------------------------------------------
	// Gibbs Sampler
	// ------------------------------------------------------------------------

	/**
	 * Run a certain number of Gibbs Sampling sweeps.
	 */
	private void runGibbsSampling() {
		// The total number of iterations include that for must-links only and
		// both must-links and cannot-links.
		int totalIterations = param.nIterations
				* (1 + param.cannotLinkLearningIterations);
		for (int i = 0; i < totalIterations; ++i) {
			for (int d = 0; d < param.D; ++d) {
				int N = docs[d].length;
				for (int n = 0; n < N; ++n) {
					if (i < param.nBurnin) {
						// Burn in period.
						sampleTopicAssignment(d, n, false, null);
					} else {
						sampleTopicAssignment(d, n, true, null);
					}
				}
			}

			if (param.useCannotLinkInAMC
					&& (i > 0 && i % param.nIterations == 0)) {
				// Extract new cannot-links and add them into the current list
				// of cannot-links.
				if (i >= param.nBurnin) {
					// After burn in.
					// Compute the values of distributions given current Markov
					// status.
					computeTopicWordDistribution(-1);
					ArrayList<PriorityQueue<Integer>> topWordIDList = getTopWordsUnderEachTopicGivenCurrentMarkovStatus();
					KnowledgeExtractingAndProcessingForAMC kepAMC = new KnowledgeExtractingAndProcessingForAMC();
					CannotLinks extractedCannotLinks = kepAMC
							.extractCannotLinks(topWordIDList,
									priorTopicsForKnowledgeExtraction, corpus,
									param.supportRatioForCannotLink,
									param.supportThresholdForCannotLink);

					if (cannotLinks == null) {
						cannotLinks = new CannotLinks();
					}
					// Add extracted cannot-links into the all cannot-links.
					for (CannotLink cannotLink : extractedCannotLinks.cannotlinkList) {
						cannotLinks.addCannotLink(cannotLink);
					}
				}
			}

			if (i >= param.nBurnin && param.sampleLag > 0
					&& i % param.sampleLag == 0) {
				updatePosteriorDistribution();
			}
		}
	}

	/**
	 * Sample a topic assigned to the word in position n of document d.
	 */
	private void sampleTopicAssignment(int d, int n,
			boolean resampleCannotWords, HashSet<Integer> hsValidSampledTopics) {
		int old_topic = z[d][n];
		int word = docs[d][n];
		updateCount(d, n, old_topic, word, -1);

		double[] p = new double[param.T];
		for (int t = 0; t < param.T; ++t) {
			if (hsValidSampledTopics == null
					|| hsValidSampledTopics.contains(t)) {
				p[t] = (ndt[d][t] + param.alpha) / (ndsum[d] + tAlpha)
						* (ntw[t][word] + param.beta) / (ntsum[t] + vBeta);
			}
		}
		int topic = InverseTransformSampler.sample(p,
				randomGenerator.nextDouble());
		ExceptionUtility.assertAsException(topic >= 0 && topic < p.length,
				"Something is wrong with inverse transform sampling.");

		z[d][n] = topic;
		updateCount(d, n, topic, word, +1);

		if (resampleCannotWords && cannotLinks != null) {
			// M-GPU model.
			String wordstr = corpus.vocab.getWordstrByWordid(word);
			HashSet<CannotLink> cannotLinkList = cannotLinks
					.getCannotLinkListGivenWordstr(wordstr);
			if (cannotLinkList != null) {
				for (CannotLink cannotLink : cannotLinkList) {
					String theOtherWordstr = cannotLink.wordpair.wordstr1
							.equals(wordstr) ? cannotLink.wordpair.wordstr2
							: cannotLink.wordpair.wordstr1;
					int cannotWord = corpus.vocab
							.getWordidByWordstr(theOtherWordstr);
					// Move one cannot word to other topic urn.
					WordTopicAssignments cannotWordTopicAssignments = wtaOfWordUnderTopic[topic][cannotWord];
					if (cannotWordTopicAssignments.size() == 0) {
						// There is no cannot word in this topic to
						// sample.
						continue;
					}
					WordTopicAssignment wta = sampleOneCannotWordFromWordTopicAssignments(
							topic, cannotWordTopicAssignments);
					int docId = wta.documentId;
					int wordIndexOfDoc = wta.wordIndex;
					ExceptionUtility.assertAsException(wta.topicId == topic,
							"The sampled cannot word was not in this topic!");
					HashSet<Integer> hsTransferedTopics = getTopicsWithHigherWordProbability(
							wta.topicId, wta.wordId);
					if (hsTransferedTopics.size() > 0) {
						// There are valid topics to move.
						// Sample a new topic for this word.
						sampleTopicAssignment(docId, wordIndexOfDoc, false,
								hsTransferedTopics);
						ExceptionUtility
								.assertAsException(
										z[docId][wordIndexOfDoc] != topic,
										"The transferred new topic should not the same as old topic!");
					}
				}
			}
		}
	}

	/**
	 * Update the counts in the Gibbs sampler.
	 */
	private void updateCount(int d, int n, int topic, int word, int flag) {
		ndt[d][topic] += flag;
		ndsum[d] += flag;

		if (flag > 0 && mustLinks != null) {
			// Sample a must-link that represents the word meaning of this word.
			String wordstr = corpus.vocab.getWordstrByWordid(word);
			ArrayList<MustLink> mustLinkListGivenWord = mustLinks
					.getMustLinkListGivenWordstr(wordstr);
			if (mustLinkListGivenWord != null
					&& mustLinkListGivenWord.size() > 0) {
				double[] p_of_mustLink = new double[mustLinkListGivenWord
						.size()];
				for (int i = 0; i < mustLinkListGivenWord.size(); ++i) {
					MustLink mustLink = mustLinkListGivenWord.get(i);
					p_of_mustLink[i] = getProbOfMustLinkUnderTopicGivenCurrentMarkovStatus(
							topic, mustLink);
				}
				int index = InverseTransformSampler.sample(p_of_mustLink,
						randomGenerator.nextDouble());
				ExceptionUtility.assertAsException(index >= 0
						&& index < p_of_mustLink.length,
						"Something is wrong with inverse transform sampling.");
				// The sampled must-link.
				MustLink sampledMustLink = mustLinkListGivenWord.get(index);
				int sampledMustLinkId = mpMustLinkToMustLinkId
						.get(sampledMustLink);
				// Get one-degree neighbors in the must-link graph.
				ArrayList<Integer> linkedListInGraph = mustLinkGraph
						.get(sampledMustLinkId);
				ArrayList<MustLink> sampledMustLinkList = new ArrayList<MustLink>();
				for (int nodeId : linkedListInGraph) {
					MustLink mustLink = mpMustLinkIdToMustLink.get(nodeId);
					if (mustLink.wordpair.wordstr1.equals(wordstr)
							|| mustLink.wordpair.wordstr2.equals(wordstr)) {
						// It must contain this word.
						sampledMustLinkList.add(mustLink);
						double value = mpMustLinkIdToGPUValue.get(nodeId);
						String theOtherWordstr = mustLink.wordpair.wordstr1
								.equals(wordstr) ? mustLink.wordpair.wordstr2
								: mustLink.wordpair.wordstr1;
						int w2 = corpus.vocab
								.getWordidByWordstr(theOtherWordstr);
						ntw[topic][w2] += flag * value;
						ntsum[topic] += flag * value;
					}
				}
				sampledMustLinkListForEachWord.get(d).set(n,
						sampledMustLinkList);
			}
		} else if (mustLinks != null) {
			// Revert the sampling effects by looking up at the records in
			// sampledMustLinkListForEachWord.
			String wordstr = corpus.vocab.getWordstrByWordid(word);
			ArrayList<MustLink> sampledMustLinkList = sampledMustLinkListForEachWord
					.get(d).get(n);
			for (MustLink mustLink : sampledMustLinkList) {
				int mustLink_id = mpMustLinkToMustLinkId.get(mustLink);
				double value = mpMustLinkIdToGPUValue.get(mustLink_id);
				String theOtherWordstr = mustLink.wordpair.wordstr1
						.equals(wordstr) ? mustLink.wordpair.wordstr2
						: mustLink.wordpair.wordstr1;
				int w2 = corpus.vocab.getWordidByWordstr(theOtherWordstr);
				ntw[topic][w2] += flag * value;
				ntsum[topic] += flag * value;
			}
			// Clear the record.
			sampledMustLinkListForEachWord.get(d).set(n,
					new ArrayList<MustLink>());
		}

		ntw[topic][word] += flag;
		ntsum[topic] += flag;

		if (flag > 0) {
			// Record this word with the topic assignment for cannot
			// words transfer.
			wtaOfWordUnderTopic[topic][word]
					.addWordTopicAssignment(new WordTopicAssignment(word, d, n,
							topic));
		} else {
			// Remove the word with the topic assignment.
			wtaOfWordUnderTopic[topic][word]
					.removeWordTopicAssignment(new WordTopicAssignment(word, d,
							n, topic));
		}
	}

	// ------------------------------------------------------------------------
	// Knowledge Related.
	// ------------------------------------------------------------------------

	/************************* Must-Link Related *****************************/
	/**
	 * Build the must-link graph.
	 */
	public void buildMustLinkGraph() {
		int MS = mustLinks.size();
		// Initialization.
		mpMustLinkToMustLinkId = new HashMap<MustLink, Integer>();
		mpMustLinkIdToMustLink = new HashMap<Integer, MustLink>();
		mpMustLinkIdToGPUValue = new HashMap<Integer, Double>();
		mustLinkGraph = new ArrayList<ArrayList<Integer>>();
		for (int i = 0; i < MS; ++i) {
			mustLinkGraph.add(new ArrayList<Integer>());
		}

		for (int i = 0; i < MS; ++i) {
			MustLink mustLink = mustLinks.getMustLink(i);
			mpMustLinkToMustLinkId.put(mustLink, i);
			mpMustLinkIdToMustLink.put(i, mustLink);
			mpMustLinkIdToGPUValue.put(i, getGPULambdaValue(mustLink));
		}

		TopicOverlappingOfMustLinks topicOverlappingHandle = new TopicOverlappingOfMustLinks(
				priorTopicsForKnowledgeExtraction);

		// Compute the edge weight of each pair of must-links in the graph.
		for (int i = 0; i < MS; ++i) {
			MustLink mustLink_i = mustLinks.getMustLink(i);
			int mustLink_i_id = mpMustLinkToMustLinkId.get(mustLink_i);
			mustLinkGraph.get(mustLink_i_id).add(mustLink_i_id);
			for (int j = i + 1; j < MS; ++j) {
				if (i == j) {
					continue;
				}
				MustLink mustLink_j = mustLinks.getMustLink(j);
				int mustLink_j_id = mpMustLinkToMustLinkId.get(mustLink_j);
				if (mustLink_i.sharesAWordWithMustLink(mustLink_j)) {
					int topicOverlappingCount = topicOverlappingHandle
							.getTopicOverlappingCount(mustLink_i, mustLink_j);
					double ratio = 1.0 * topicOverlappingCount
							/ Math.max(mustLink_i.weight, mustLink_j.weight);
					if (ratio > param.mustLinkGraphCutRatioThreshold) {
						mustLinkGraph.get(mustLink_i_id).add(mustLink_j_id);
						mustLinkGraph.get(mustLink_j_id).add(mustLink_i_id);
					}
				}
			}
		}
	}

	/**
	 * Get the parameter lambda for the GPU model.
	 * 
	 * @param pair
	 * @return
	 */
	private double getGPULambdaValue(MustLink mustLink) {
		String wordstr1 = mustLink.wordpair.wordstr1;
		String wordstr2 = mustLink.wordpair.wordstr2;

		if (!wordstr1.equals(wordstr2)) {
			// Use PMI to update urn matrix.
			int coDocFrequency = corpus.getCoDocumentFrequency(wordstr1,
					wordstr2) + 1; // Add 1 for smoothing.
			int docFrequency1 = corpus.getDocumentFrequency(wordstr1) + 1;
			int docFrequency2 = corpus.getDocumentFrequency(wordstr2) + 1;

			double Pxy = 1.0 * coDocFrequency / param.D;
			double Px = 1.0 * docFrequency1 / param.D;
			double Py = 1.0 * docFrequency2 / param.D;
			double PMI = Math.log(Pxy / (Px * Py));
			double gpuScale = param.pmiToLambdaScaleInGPUModel * PMI;

			if (gpuScale > 0) {
				return gpuScale;
			} else {
				return 0;
			}
		}
		return 0;
	}

	private double getProbOfMustLinkUnderTopicGivenCurrentMarkovStatus(
			int topicId, MustLink mustLink) {
		int wordId1 = corpus.vocab
				.getWordidByWordstr(mustLink.wordpair.wordstr1);
		int wordId2 = corpus.vocab
				.getWordidByWordstr(mustLink.wordpair.wordstr2);
		double prob1 = getProbOfWordUnderTopicGivenCurrentMarkovStatus(topicId,
				wordId1);
		double prob2 = getProbOfWordUnderTopicGivenCurrentMarkovStatus(topicId,
				wordId2);
		double prob = prob1 * prob2;
		return prob;
	}

	/************************* Cannot-Link Related *****************************/
	/**
	 * Sample one cannot word from a list of WordTopicAssignment.
	 */
	private WordTopicAssignment sampleOneCannotWordFromWordTopicAssignments(
			int topic, WordTopicAssignments cannotWordTopicAssignments) {
		int size = cannotWordTopicAssignments.size();

		double[] p = new double[size];

		int i = 0;
		for (i = 0; i < size; ++i) {
			int d = cannotWordTopicAssignments.get(i).documentId;
			int n = cannotWordTopicAssignments.get(i).wordIndex;
			int w = cannotWordTopicAssignments.get(i).wordId;
			int t = z[d][n];
			p[i] = (ndt[d][t] + param.alpha) / (ndsum[d] + tAlpha)
					* (ntw[t][w] + param.beta) / (ntsum[t] + vBeta);
		}
		int index = InverseTransformSampler.sample(p,
				randomGenerator.nextDouble());
		ExceptionUtility.assertAsException(index >= 0 && index < p.length,
				"Something is wrong with inverse transform sampling.");

		return cannotWordTopicAssignments.get(index);
	}

	/**
	 * Get topics that have higher probability of this word than the current
	 * topic.
	 */
	private HashSet<Integer> getTopicsWithHigherWordProbability(
			int currentTopic, int word) {
		HashSet<Integer> hsHigherTopics = new HashSet<Integer>();
		// Get the probability of this word under current topic.
		double probOfWordGivenCurrentTopic = getProbOfWordUnderTopicGivenCurrentMarkovStatus(
				currentTopic, word);

		for (int t = 0; t < param.T; ++t) {
			if (t == currentTopic) {
				continue;
			}
			double probOfWordGivenTopicT = getProbOfWordUnderTopicGivenCurrentMarkovStatus(
					t, word);
			if (probOfWordGivenTopicT > probOfWordGivenCurrentTopic) {
				hsHigherTopics.add(t);
			}
		}
		return hsHigherTopics;
	}

	/**
	 * Get the probability of a word under a topic given current Markov status.
	 */
	private double getProbOfWordUnderTopicGivenCurrentMarkovStatus(int t, int w) {
		return (ntw[t][w] + param.beta) / (ntsum[t] + vBeta);
	}

	/**
	 * Get the top words under each topic given current Markov status.
	 */
	private ArrayList<PriorityQueue<Integer>> getTopWordsUnderEachTopicGivenCurrentMarkovStatus() {
		ArrayList<PriorityQueue<Integer>> topWordIDList = new ArrayList<PriorityQueue<Integer>>();
		int top_words = param.numberOfTopWordsUnderPriorTopicsForKnowledgeExtraction;

		for (int t = 0; t < param.T; ++t) {
			Comparator<Integer> comparator = new TopicalWordComparator(phi[t]);
			PriorityQueue<Integer> pqueue = new PriorityQueue<Integer>(
					top_words, comparator);

			for (int w = 0; w < param.V; ++w) {
				if (pqueue.size() < top_words) {
					pqueue.add(w);
				} else {
					if (phi[t][w] > phi[t][pqueue.peek()]) {
						pqueue.poll();
						pqueue.add(w);
					}
				}
			}

			topWordIDList.add(pqueue);
		}
		return topWordIDList;
	}

	// ------------------------------------------------------------------------
	// Posterior Distribution Computation
	// ------------------------------------------------------------------------

	/**
	 * After burn in phase, update the posterior distributions every sample lag.
	 */
	private void updatePosteriorDistribution() {
		for (int d = 0; d < param.D; ++d) {
			for (int t = 0; t < param.T; ++t) {
				thetasum[d][t] += (ndt[d][t] + param.alpha)
						/ (ndsum[d] + tAlpha);
			}
		}

		for (int t = 0; t < param.T; ++t) {
			for (int w = 0; w < param.V; ++w) {
				phisum[t][w] += (ntw[t][w] + param.beta) / (ntsum[t] + vBeta);
			}
		}
		++numstats;
	}

	/**
	 * Compute the posterior distributions.
	 */
	private void computePosteriorDistribution() {
		computeDocumentTopicDistribution(param.sampleLag);
		computeTopicWordDistribution(param.sampleLag);
	}

	/**
	 * Document-topic distribution: theta[][].
	 */
	private void computeDocumentTopicDistribution(int slag) {
		if (slag > 0) {
			for (int d = 0; d < param.D; ++d) {
				for (int t = 0; t < param.T; ++t) {
					theta[d][t] = thetasum[d][t] / numstats;
				}
			}
		} else {
			for (int d = 0; d < param.D; ++d) {
				for (int t = 0; t < param.T; ++t) {
					theta[d][t] = (ndt[d][t] + param.alpha)
							/ (ndsum[d] + tAlpha);
				}
			}
		}
	}

	/**
	 * Topic-word distribution: phi[][].
	 */
	private void computeTopicWordDistribution(int slag) {
		if (slag > 0) {
			for (int t = 0; t < param.T; ++t) {
				for (int w = 0; w < param.V; ++w) {
					phi[t][w] = phisum[t][w] / numstats;
				}
			}
		} else {
			for (int t = 0; t < param.T; ++t) {
				for (int w = 0; w < param.V; ++w) {
					phi[t][w] = (ntw[t][w] + param.beta) / (ntsum[t] + vBeta);
				}
			}
		}
	}

	@Override
	public double[][] getTopicWordDistribution() {
		return phi;
	}

	@Override
	public double[][] getDocumentTopicDistrbution() {
		return theta;
	}

	@Override
	/**
	 * Print out must-links and cannot-links.
	 */
	public void printKnowledge(String filepath) {
		if (mustLinks != null) {
			FileReaderAndWriter.writeFile(filepath + "_mustlinks",
					mustLinks.toString());
		}
		if (cannotLinks != null) {
			FileReaderAndWriter.writeFile(filepath + "_cannotlinks",
					cannotLinks.toString());
		}
	}
}

/**
 * Comparator to rank the words according to their probabilities.
 */
class TopicalWordComparator implements Comparator<Integer> {
	private double[] distribution = null;

	public TopicalWordComparator(double[] distribution2) {
		distribution = distribution2;
	}

	@Override
	public int compare(Integer w1, Integer w2) {
		if (distribution[w1] < distribution[w2]) {
			return -1;
		} else if (distribution[w1] > distribution[w2]) {
			return 1;
		}
		return 0;
	}
}