public class LRUCache {
    // some variables
	private int curr_size;
    private int capacity;
    private HashMap<Integer, Entry> hm;
    private Entry head;
    private Entry tail;
    // functions
    public LRUCache(int capacity) {
        head = new Entry();
        tail = new Entry();
        head.next = tail;
        tail.prev = head;
        
        this.capacity = capacity;
        this.curr_size = 0;
        hm = new HashMap<Integer, Entry>();
    }
    
    public int get(int key) {
    	Entry node = hm.get(key);
    	if (node != null)
    	{
    		move_to_head(node);
    		return node.value;
    	}
    	else
    	{
    		return -1;
    	}
    }
    
    public void set(int key, int value) {
        if (this.curr_size == 0)//initial insert
        {
        	// create a new node
        	Entry node = new Entry();
        	node.key = key;
        	node.value = value;
        	// update the cache
        	head.next = node;
        	node.prev = head;
        	node.next = tail;
        	tail.prev = node;
        	this.curr_size++;
        	// update the hashmap
        	hm.put(key, node);
        }
        else
        {
        	//check if the key already exist
        	Entry node = hm.get(key);
        	if (node == null)//doesn't exist
        	{
        		// create a new node
        		node = new Entry();
        		node.key = key;
        		node.value = value;
        		// update the cache
        		set_new_head(node);
        		this.curr_size++;
        		if (this.curr_size > this.capacity)
        		{
        			hm.remove(tail.prev.key);
        			remove_tail();
        			this.curr_size--;
        		}
        		// update the hashmap
        		hm.put(key, node);
        	}
        	else if  (node.key == key && node.value != value)// duplicate
        	{
        		node.value = value;
        		hm.put(node.key, node);
        		move_to_head(node);
        	}
        	else // already exist. size doesn't change
        	{
        		move_to_head(node);
        	}
        }
    	
    }
    
    public void remove_tail () 
    {
    	if (tail.prev == head)
    	{
    	    return;
    	}
    	else
    	{
    	    tail.prev = tail.prev.prev;
    	    tail.prev.next = tail;
    	}
    }
    
    public void set_new_head(Entry node)
    {
        node.next = head.next;
        node.next.prev = node;
        
    	head.next = node;
    	node.prev = head;
    }
    
    public void move_to_head (Entry node)
    {
    	if (head.next == tail) return;
    	else
    	{
    	    node.prev.next = node.next;
    	    node.next.prev = node.prev;
    	    
    	    set_new_head(node);
    	}
    }
}

class Entry {
	int key;
	int value;
	Entry prev;
	Entry next;
}
