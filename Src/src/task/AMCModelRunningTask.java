package task;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import model.ModelLoader;
import model.ModelParameters;
import model.TopicModel;
import multithread.TopicModelMultiThreadPool;
import nlp.Corpus;
import global.CmdOption;

/**
 * The task of running the proposed method in the paper (Chen and Liu, KDD
 * 2014).
 */
public class AMCModelRunningTask {
	private CmdOption cmdOption = null;

	public AMCModelRunningTask(CmdOption cmdOption2) {
		cmdOption = cmdOption2;
	}

	/**
	 * Read the corpus of each domain.
	 */
	private ArrayList<Corpus> getCorpora(String inputCorporeaDirectory,
			String suffixInputCorporeaDocs, String suffixInputCorporeaVocab) {
		ArrayList<Corpus> corpora = new ArrayList<Corpus>();
		File[] domainFiles = new File(inputCorporeaDirectory).listFiles();
		for (File domainFile : domainFiles) {
			if (domainFile.isDirectory()) {
				// Only consider folders.
				String domain = domainFile.getName();
				String docsFilepath = domainFile.getAbsolutePath()
						+ File.separator + domain + suffixInputCorporeaDocs;
				String vocabFilepath = domainFile.getAbsolutePath()
						+ File.separator + domain + suffixInputCorporeaVocab;
				Corpus corpus = Corpus.getCorpusFromFile(domain, docsFilepath,
						vocabFilepath);
				corpora.add(corpus);
			}
		}
		return corpora;
	}

	/**
	 * For each model, create its output directory and run the model.
	 */
	public void run() {
		// There are two corporea in each domain. One contains 100 reviews and
		// the other one contains 1000 reviews.
		ArrayList<Corpus> corpora_100Reviews = getCorpora(
				cmdOption.input100ReviewCorporeaDirectory,
				cmdOption.suffixInputCorporeaDocs,
				cmdOption.suffixInputCorporeaVocab);
		ArrayList<Corpus> corpora_1000Reviews = getCorpora(
				cmdOption.input1000ReviewCorporeaDirectory,
				cmdOption.suffixInputCorporeaDocs,
				cmdOption.suffixInputCorporeaVocab);

		run(corpora_100Reviews, corpora_1000Reviews, cmdOption.nTopics,
				cmdOption.modelName, cmdOption.outputRootDirectory);
	}

	/**
	 * Run LDA on both 100 reviews and 1000 reviews of each domain. Then run the
	 * proposed AMC model on the 100 reviews of each domain.
	 */
	private void run(ArrayList<Corpus> corpora_100Reviews,
			ArrayList<Corpus> corpora_1000Reviews, int nTopics,
			String modelName, String outputRootDirectory) {
		// Run LDA on 100 reviews of each domain.
		System.out.println("-----------------------------------");
		System.out.println("Running LDA on 100 reviews of each domain.");
		System.out.println("-----------------------------------");
		String outputRootDirectory_LDA_100Reivews = outputRootDirectory + "LDA"
				+ File.separator + "100Reviews" + File.separator;
		// When the data is very small (e.g., 100 reviews), we only retain the
		// last Markov chain status (i.e., sampleLag = -1). The reason is that
		// it avoids the topics being dominated by the most frequent words.
		cmdOption.sampleLag = -1;
		ArrayList<TopicModel> topicModelList_LDA_100Reviews = runTopicModelOnCorpus(
				corpora_100Reviews, nTopics, "LDA",
				outputRootDirectory_LDA_100Reivews, null, null);

		// Run LDA on 1000 reviews of each domain.
		System.out.println("-----------------------------------");
		System.out.println("Running LDA on 1000 reviews of each domain.");
		System.out.println("-----------------------------------");
		String outputRootDirectory_LDA_1000Reivews = outputRootDirectory
				+ "LDA" + File.separator + "1000Reviews" + File.separator;
		// When the data is not very small (e.g., 1000 reviews), we should set
		// sampleLag as 20.
		cmdOption.sampleLag = 20;
		ArrayList<TopicModel> topicModelList_LDA_1000Reviews = runTopicModelOnCorpus(
				corpora_1000Reviews, nTopics, "LDA",
				outputRootDirectory_LDA_1000Reivews, null, null);

		// Run the proposed AMC model on 100 reviews of each domain.
		System.out.println("-----------------------------------");
		System.out.println("Running AMC on 100 reviews of each domain.");
		System.out.println("-----------------------------------");
		String outputRootDirectory_AMC_100Reivews = outputRootDirectory + "AMC"
				+ File.separator + "100Reviews" + File.separator;
		// When the data is very small (e.g., 100 reviews), we only retain the
		// last Markov chain status (i.e., sampleLag = -1). The reason is that
		// it avoids the topics being dominated by the most frequent words.
		cmdOption.sampleLag = -1;
		runTopicModelOnCorpus(corpora_100Reviews, nTopics, "AMC",
				outputRootDirectory_AMC_100Reivews,
				topicModelList_LDA_100Reviews, topicModelList_LDA_1000Reviews);
	}

	/**
	 * Run the topic model (LDA or AMC) on the corpus. We use multithreading and
	 * each thread executes the model in one domain.
	 */
	private ArrayList<TopicModel> runTopicModelOnCorpus(
			ArrayList<Corpus> corpora, int nTopics, String modelName,
			String outputRootDirectory,
			ArrayList<TopicModel> topicModelList_LDA_SameSetting,
			ArrayList<TopicModel> topicModelList_LDA_KnowledgeFrom) {
		ArrayList<TopicModel> topicModelList_current = new ArrayList<TopicModel>();
		TopicModelMultiThreadPool threadPool = new TopicModelMultiThreadPool(
				cmdOption.nthreads);

		for (Corpus corpus : corpora) {
			String outputDomainDirectory = outputRootDirectory + File.separator
					+ "DomainModels" + File.separator + corpus.domain
					+ File.separator;

			if (new File(outputDomainDirectory).exists()) {
				// If the model of a domain in this learning
				// iteration already exists, we load it and add it into the
				// topic model list.
				ModelLoader modelLoader = new ModelLoader();
				TopicModel modelForDomain = modelLoader.loadModel(modelName,
						corpus.domain, outputDomainDirectory);
				System.out.println("Loaded the model of domain "
						+ corpus.domain);
				topicModelList_current.add(modelForDomain);
			} else {
				// Run the model on each domain.
				// Construct all the parameters needed to run the model.
				ModelParameters param = new ModelParameters(corpus, nTopics,
						cmdOption);

				param.modelName = modelName;
				param.outputModelDirectory = outputDomainDirectory;
				param.topicModelList_LDA_SameSetting = topicModelList_LDA_SameSetting;
				param.topicModelList_LDA_KnowledgeFrom = topicModelList_LDA_KnowledgeFrom;

				threadPool.addTask(corpus, param);
			}
		}
		threadPool.awaitTermination();
		topicModelList_current.addAll(threadPool.topicModelList);
		// Sort the topic model list based on the domain name alphabetically.
		Collections.sort(topicModelList_current, new Comparator<TopicModel>() {
			@Override
			public int compare(TopicModel o1, TopicModel o2) {
				return o1.corpus.domain.toLowerCase().compareTo(
						o2.corpus.domain.toLowerCase());
			}
		});
		return topicModelList_current;
	}

}
