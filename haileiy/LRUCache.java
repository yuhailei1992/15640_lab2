/**
 * The LRU cache traces the current size of cache directory
 * evicts the least recently used file when cache is going to be full
 *
 */
import java.io.File;
import java.util.*;

public class LRUCache {

	
    private int currsize; // current cache size
    private long capacity; // maximum size
    private HashMap<String, Entry> hm; // helps to find the node quickly
    private Entry head; // dummy nodes make life easier
    private Entry tail;
    /**
     * constructor, set cache size limit
     */
    public LRUCache(long capacity) {
        head = new Entry();
        tail = new Entry();
        head.next = tail;
        tail.prev = head;

        this.capacity = capacity;
        this.currsize = 0;
        hm = new HashMap<String, Entry>();
    }

    /**
     * when get file by its path, move it to the head
     * return the path on success, return null on failure (the
     */
    public String get(String path) {
    	System.err.println("Cache: get " + path);
        Entry node = hm.get(path);
        if (node != null) {
            move_to_head(node);
            return path;
        } else {
            return null;
        }
    }

    public void set(String path, long filesize) {
    	System.err.println("Cache: insert " + path + " with size of " + filesize);
        if (this.currsize == 0) { // initial insert
            // create a new node
            Entry node = new Entry();
            node.path = path;
            node.size = filesize;
            // place this node to head of cache
            head.next = node;
            node.prev = head;
            node.next = tail;
            tail.prev = node;
            this.currsize += filesize;
            // update the hashmap
            hm.put(path, node);
        } else {
            //check if the path already exist
            Entry node = hm.get(path);
            if (node == null) {
                // create a new node
                node = new Entry();
                node.path = path;
                node.size = filesize;
                // update the cache
                set_new_head(node);
                this.currsize += filesize;
                while (this.currsize > this.capacity)
                {
                	System.err.println("Cache:need to evict something");
                    Entry evicted = hm.remove(tail.prev.path);
                    this.currsize -= evicted.size;
                    try {
                    	File f = new File(evicted.path);
                    	f.delete();
                    } catch (Exception e) {
                    	e.printStackTrace();
                    }
                }
                // update the hashmap
                hm.put(path, node);
            }
            /*
            else if  (node.path == path && node.size != size)// duplicate
            {
                node.size = size;
                hm.put(node.path, node);
                move_to_head(node);
            }
            */
            else {// already exist. size doesn't change
                move_to_head(node);
            }
        }
    }
    /* return the filesize of tail node */
    public Entry remove_tail () {
        if (tail.prev == head) {
            return null;
        } else {
            Entry evicted = tail.prev;
            tail.prev = tail.prev.prev;
            tail.prev.next = tail;
            return evicted;
        }
    }

    public void set_new_head(Entry node) {
        node.next = head.next;
        node.next.prev = node;

        head.next = node;
        node.prev = head;
    }

    public void move_to_head (Entry node) {
        if (head.next == tail) return;
        else {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            set_new_head(node);
        }
    }
    
    public void showCache() {
    	Entry node = head.next;
    	System.err.println("Showing cache");
    	while (node != tail) {
    		System.err.println(node.path + " size is " + node.size);
    		node = node.next;
    	}
    	System.err.println("Show cache ends here");
    }
    /*
    public static void main(String [] args) {
    	System.err.println("Testing LRUCache");
    	LRUCache cache = new LRUCache(Long.parseLong(args[0]));
    	cache.set("1", 50);
    	cache.set("2", 60);
    	cache.set("3", 40);
    	cache.showCache();
    	cache.get("2");
    	cache.showCache();
    	cache.set("4", 100);
    	cache.showCache();
    }
    */
}

class Entry {
    String path;
    long size;
    Entry prev;
    Entry next;
}


