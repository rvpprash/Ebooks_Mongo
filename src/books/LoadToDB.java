package books;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

public class LoadToDB {
	// List to hold all the file references.
	private List<File> fileList = new ArrayList<File>();
	private List<String> ignoreWordList = new ArrayList<String>();

	// Word index cache
	private Map<String, HashSet<String>> wordMap = new HashMap<String, HashSet<String>>();

	private static Mongo mongo = null;
	private static DB db = null;
	private static DBCollection collection = null;

	public static void main(String[] args) throws IOException {

		LoadToDB mainObj = new LoadToDB();
		mongo = new Mongo("localhost", 27017);

		db = mongo.getDB("booksdb");
		collection = db.getCollection("books");
		// Get the list of words to ignore
		File swf = new File("Data/stopwords.txt");
		mainObj.ignoreWordList = mainObj.getWordsInIgnoreFile(swf);

		// Get files from file system to the array list.
		File dir = new File("Data/books");
//		 
		mainObj.getFiles(dir);
		// Ok now we have fileList populated.
		//System.out.println(mainObj.fileList);
		boolean indexInMongo = false;
		long startTime = System.currentTimeMillis();
		for (File fRef : mainObj.fileList) {
			System.out.println("processing " + fRef.getName());
			mainObj.generateWordIndex(fRef,indexInMongo);
		}
		//System.out.println(mainObj.wordMap);
		if(!indexInMongo)
			mainObj.persistToMongoDB();
		long totalTime = (System.currentTimeMillis() - startTime);
		System.out.println("Total time taken : " +totalTime);

	}

	private void persistToMongoDB() throws UnknownHostException {

		for (String word : wordMap.keySet()) {
			DBObject document = new BasicDBObject();
			document.put("word", word);
			document.put("docs", wordMap.get(word));
			collection.insert(document);
		}
	}

	/**
	 * Populate the wordMap ( word index cache ) for each file passed to this
	 * method
	 * 
	 * @param fRef
	 * @throws IOException
	 */
	private void generateWordIndex(File fRef, boolean indexInMongo) throws IOException {
		for (String word : getWordsInAFile(fRef)) {
			if(word.length() > 5)
				putWordToIndex(word, fRef.getName(), indexInMongo);
		}
	}

	/**
	 * 
	 * @param word
	 * @return
	 */
	private Set<String> getWordFromIndex(String word, boolean indexInMongo) {
		if (!indexInMongo)
			return wordMap.get(word);
		else {

			return getFromMongo(word);
		}

	}

	private void putWordToIndex(String word, String fRefName,boolean indexInMongo) {
		if (!indexInMongo) {
			if (getWordFromIndex(word, indexInMongo) == null)
				insertNewtoIndex(word, fRefName, indexInMongo);
			getWordFromIndex(word, indexInMongo).add(fRefName);
		} else {
			Set<String> oldSet;
			if ((oldSet = getWordFromIndex(word, indexInMongo)) != null) {
				//Set<String> oldSet = getWordFromIndex(word, true);
				Set<String> newSet = new HashSet<String>();
				newSet.addAll(oldSet);
				newSet.add(fRefName);
				updateToIndex(word,newSet);
			}
			else
			{
				insertNewtoIndex(word, fRefName, true);
			}
		}

	}

	private Set<String> getFromMongo(String word) {
		Set<String> returnList = null;
		;
		BasicDBObject searchQuery = new BasicDBObject();
		searchQuery.put("word", word);
		DBCursor cursor = collection.find(searchQuery);
		if (cursor.hasNext()) {
			returnList = new HashSet<String>();
			DBObject ob = cursor.next();
			BasicDBList listEditions = (BasicDBList) ob.get("docs");
			if (listEditions != null) {
				for (Object element : listEditions) {
					returnList.add(element.toString());
				}
			}
		}
		return returnList;
	}

	private void insertNewtoIndex(String word, String fRefName,boolean indexInMongo) {
		if (!indexInMongo)
			wordMap.put(word, new HashSet<String>());
		else {
			DBObject document = new BasicDBObject();
			Set<String> tempSet = new HashSet<String>();
			tempSet.add(fRefName);
			document.put("word", word);
			document.put("docs", tempSet);
			collection.insert(document);
		}
	}
	
	private void updateToIndex(String word,Set<String> newSet)
	{
		DBObject documentNew = new BasicDBObject();
		documentNew.put("word", word);
		documentNew.put("docs", newSet);
		DBObject documentOld = new BasicDBObject().append("word", word);
		collection.update(documentOld,documentNew);
	}

	/**
	 * 
	 * @param f
	 * @return
	 * @throws IOException
	 */
	private List<String> getWordsInIgnoreFile(File f) throws IOException {
		List<String> ignoreList = new ArrayList<String>();
		BufferedReader rdr = new BufferedReader(new FileReader(f));
		String line = null;
		try {
			while ((line = rdr.readLine()) != null) {
				if (!line.startsWith("#"))
					ignoreList.add(line.trim().toLowerCase());
			}
		} finally {
			rdr.close();
		}

		return ignoreList;
	}

	/**
	 * Get all the words in a file
	 * 
	 * @param f
	 * @return
	 * @throws IOException
	 */
	private Set<String> getWordsInAFile(File f) throws IOException {
		Set<String> wordSet = new HashSet<String>();
		BufferedReader rdr = new BufferedReader(new FileReader(f));
		String line = null;
		try {
			while ((line = rdr.readLine()) != null) {
				String[] parts = line.trim().split(
						"[\\s,\\.:;\\-#~\\(\\)\\?\\!\\&\\*\\\"\\/\\'\\`]");// doubt
				for (String p : parts) {
					if (!ignoreWordList.contains(p)) {
						wordSet.add(p);
					}
				}
			}
		} finally {
			rdr.close();
		}

		return wordSet;
	}

	/**
	 * @throws IOException
	 * 
	 */

	private void getFiles(File f) {
		if (f == null)
			return;
		if (f.isFile())
			fileList.add(f);
		else {
			discoverFiles(f);
		}
	}

	/**
	 * 
	 * @param dir
	 */
	private void discoverFiles(File dir) {
		System.out.println(dir);
		if (dir == null || dir.isFile())
			return;

		File[] dirs = dir.listFiles(new FileFilter() {

			@Override
			public boolean accept(File f) {
				if (f.getName().startsWith(".")) {
					// ignore
				} else if (f.isFile())
					fileList.add(f);

				else if (f.isDirectory()) {
					discoverFiles(f);
				}

				return false;
			}
		});

	}

}
