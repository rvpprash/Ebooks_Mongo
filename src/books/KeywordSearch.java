package books;

import java.net.UnknownHostException;
import java.util.Scanner;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

public class KeywordSearch {
	public void search(String searchkey) throws UnknownHostException {
		Mongo mongo = new Mongo("localhost",27017);
		DB db = mongo.getDB("booksdb");
		DBCollection collection = db.getCollection("books");		
		BasicDBObject searchQuery= new BasicDBObject();
		searchQuery.put("word",searchkey);
		DBCursor cursor=collection.find(searchQuery);
		if(cursor.hasNext()){
			while(cursor.hasNext()){
				DBObject ob= cursor.next();
				BasicDBList listEditions = (BasicDBList)ob.get("docs");
				System.out.println("Your search found the below documents:");
				for(Object element: listEditions) 
					System.out.println(element);
			}
		}
		else
			System.out.println("Sorry! No reults found.");
	}
		
	
	public static void main(String[] args) throws UnknownHostException {
		KeywordSearch kw = new KeywordSearch();
		Scanner keyboard = new Scanner(System.in);
		System.out.println("Enter the word to search: ");
		String word = keyboard.nextLine();
		long startTime = System.currentTimeMillis();
		kw.search(word);
		System.out.println("Total time taken: " + (System.currentTimeMillis()-startTime)+ "milliseconds");

	}

}
