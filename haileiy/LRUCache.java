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
    
    public void setUsed(String path) {
    	Entry node = hm.get(path);
    	if (node != null) {
    		node.isInUse = false;
    	}
    }

    /** return true on success
     * return false if there is no space in the cache
     */
    public boolean insert(String path, long filesize) {
    	System.err.println("Cache: insert " + path + " with size of " + filesize);
    	
    	if (this.currsize + filesize > this.capacity) {
    		System.err.println("Cache: full.need to evict something");
    		while (this.currsize + filesize > this.capacity) {
    			Entry p = tail.prev;
    			for (; p != head; p = p.prev) {
    				if (Proxy.isInUse(p.path)) {
    					System.err.println("Cache::Cannot evict file " + p.path);
    				} else {
    					break;
    				}
    			}
    			if (p == head) {
    				System.err.println("Cache: cannot evict anything");
    				return false;
    			} else {
    				this.currsize -= p.size;
    				remove_node(p);
    				hm.remove(p.path);
    				Proxy.removeFile(p.path);
    			}
    		}
    	}
    	System.err.println("Cache:: now we have enough space");
    	this.currsize += filesize;
    	// create new node
    	Entry node = new Entry();
    	node.path = path;
    	node.size = filesize;
    	node.isInUse = true;
    	// insert the node to the head
    	node.next = head.next;
    	head.next = node;
    	node.next.prev = node;
    	node.prev = head;
    	hm.put(path, node);
    	showCache();
    	return true;
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
    
    public boolean removeNode(String path) {
    	System.err.println("Cache:remove node " + path);
    	Entry todelete = hm.get(path);
    	return remove_node(todelete);
    }

    public boolean remove_node (Entry node) {
    	if (node == tail || node == head) {
    		System.err.println("Cannot remove head or tail!!!!!!!");
    		return false;
    	} else {
    		node.prev.next = node.next;
    		node.prev.next.prev = node.prev;
    		return true;
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
    /**
     * show cache in a friendly style
     */
    public void showCache() {
    	Entry node = head.next;
    	System.err.println("====================Cache start====================");
    	int i = 0;
    	while (node != tail) {
    		System.err.println("#" + i + node.path + " size is " + node.size);
    		node = node.next;
    		i++;
    	}
    	System.err.println("********************Cache end ********************");
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
    boolean isInUse;
    Entry prev;
    Entry next;
}


