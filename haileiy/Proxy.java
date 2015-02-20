/**
 * Author: Harold Yu (Hailei Yu)
 * Date: Feb. 17 2015
 */

import java.io.*;
import java.util.*;
class Proxy {
	public static class FileProperty {
		String filename;
		boolean isReadOnly;
		boolean isDirectory;
		
		public FileProperty () {
			filename = "";
			isReadOnly = false;
			isDirectory = false;
		}
		
	}
	private static class FileHandler implements FileHandling {

		HashMap<String, Integer> fd_map;
		HashMap<Integer, String> path_map;
		HashMap<String, RandomAccessFile> raf_map;
		HashMap<String, FileProperty> prop_map;
		HashMap<String, File> file_map;

		// constructor
		public FileHandler() {
			// initialize the hashmaps
			fd_map = new HashMap<String, Integer>();
			path_map = new HashMap<Integer, String>();
			raf_map = new HashMap<String, RandomAccessFile>();
			prop_map = new HashMap<String, FileProperty>();
			file_map = new HashMap<String, File>();
		}

		/**
		 * return the first valid file descriptor
		 */
		private int getNewFd(String path) {
			for (int i = 3; ; i++) {
				if (!path_map.containsKey(i)) {
					return i;
				}
			}
		}
		
		/**
		 * open returns fd on success, or errors on failure
		 * if the file is a directory, we add the entry in file_map
		 * if the file is a file, we add the entry in raf_map
		 */
		public int open(String path, OpenOption o ) {
			
            System.err.println("Proxy::Open. path is " + path);
            // 1, check corner cases, and create the file on demand
        	File f = null;
        	try {
        		f = new File(path);
        		// 1, check if the file already exists. if so, and if option is 
        		// CREATE_NEW, return Error
        		if (o == OpenOption.CREATE_NEW && f.exists()) {
        			System.err.println("Proxy::open. CREATE_NEW + file already exists");
        			return Errors.EEXIST;
        		}
        		// 2, directories can only be opened readonly
        		if (f.isDirectory() && (o != OpenOption.READ)) {
        			System.err.println("Proxy::open. Trying to open a directory for writing");
        			return Errors.EISDIR;
        		}
        		// 3, for READ and WRITE, the file must have exist
        		if (!f.exists()) {
        			if (o == OpenOption.READ || o == OpenOption.WRITE) {
        				System.err.println("Proxy::open. READ WRITE + file not exist");
        				return Errors.ENOENT;
        			}
        			f.createNewFile();
        		}
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
            
        	// 2, check if the fd is already in hashmap
        	if (fd_map.containsKey(path)) {
        		System.err.println("Proxy::open. Already have this file in HashMap");
        		return fd_map.get(path);
        	} 
    		// 4, enter general process
        	
        	/* 
        	 * if the file is a directory, just put it into file_map
        	 * if not, create a randomaccessfile and put it into raf_map
        	 */
    		if (f.isDirectory()) {
    			file_map.put(path, f);
    		} else {
				RandomAccessFile raf = null;
				String option = "rw";
				if (o == OpenOption.READ) {
					option = "r";
				}
				System.err.println("Proxy::open. option is " + option);
				try {
	    			raf = new RandomAccessFile(path, option);
	    		} catch (IOException e) {
	    			System.err.println("Proxy::open. openFile failed...");
	    			e.printStackTrace();
	    		}
	    		// save the file
				raf_map.put(path, raf);
    		}
    		
    		int fd = getNewFd(path);
    		// create file property
    		FileProperty prop = new FileProperty();
    		prop.filename = path;
    		if (o == OpenOption.READ) {
    			prop.isReadOnly = true;
    		}
    		if (f.isDirectory()) {
    			prop.isDirectory = true;
    		}
    		// update hashmaps
    		fd_map.put(path, fd);
    		path_map.put(fd, path);
    		prop_map.put(path, prop);
    		
    		System.err.println("Proxy::open. The fd is " + fd);
    		return fd;
		}

		/**
		 * return 0 on success, return Errors.EBADF on failure
		 * TODO: do i need to check if this is my file? How? 
		 */
		public int close( int fd ) {
			System.err.println("Proxy::close. fd is " + fd);
			if (path_map.containsKey(fd)) {
				String path = path_map.get(fd);
				// try to close this file
				if (prop_map.get(path).isDirectory) {
					File f = file_map.get(path);
					file_map.remove(f);
				} else {
					RandomAccessFile raf = raf_map.get(path);
					try {
						raf.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					raf_map.remove(raf);
				}
				
				// update hashmaps
				fd_map.remove(path);
				path_map.remove(fd);
				prop_map.remove(path);
				return 0;
			} else {
				System.err.println("Proxy::close. File hasn't been opened yet");
				return Errors.EBADF;
			}
		}

		/**
		 * write a byte array into file. return actual bytes wrote on success
		 * return EBADF on failure
		 */
		public long write( int fd, byte[] buf) {
			System.err.println("Proxy::write. fd is " + fd);
			System.err.println(Arrays.toString(buf));
			// check fd range
			if (fd < 3) {
				System.err.println("Proxy::write. Invalid filedescriptor");
				return Errors.EINVAL;
			}
			
			if (path_map.containsKey(fd)) {
				// you cannot write to a readonly file or a directory
				if (prop_map.get(path_map.get(fd)).isReadOnly) {
					System.err.println("Proxy::write. Trying to write to a readonly file");
					return Errors.EBADF;
				}
				
				if (prop_map.get(path_map.get(fd)).isDirectory) {
					System.err.println("Proxy::write. Trying to write to a directory");
					return Errors.EBADF;
				}
				
				String path = path_map.get(fd);
				RandomAccessFile raf = raf_map.get(path);
				// remember the current position
				long prev_pos = 0;
				try {
					prev_pos = raf.getFilePointer();
				} catch (Exception e) {
					e.printStackTrace();
				}
				//long write_cnt = 0;
				try {
					raf.write(buf);
				} catch (IOException e) {
					e.printStackTrace();
					return Errors.ENOMEM;
				}
				// check the current position
				long curr_pos = 0;
				try {
					curr_pos = raf.getFilePointer();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return curr_pos - prev_pos;
			} else {
				System.err.println("Proxy:: write. File not opened yet");
				return Errors.EBADF;
			}
		}

		/**
		 * read a buf from file. return num of bytes read on success
		 * return 
		 */
		public long read( int fd, byte[] buf ) {
            System.err.println("Proxy::read. " + "fd is " + fd);
            if (path_map.containsKey(fd)) {
            	String path = path_map.get(fd);
            	// you cannot read a directory
            	if (prop_map.get(path).isDirectory) {
            		System.err.println("Proxy:: read. Error! you cannot read a directory");
            		return Errors.EISDIR;
            	}
            	
				RandomAccessFile raf = raf_map.get(path);
				long readcnt = 0;
				
				long prev_pos = 0;
				try {
					prev_pos = raf.getFilePointer();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				try {
					readcnt = raf.read(buf);
				} catch (IOException e) {
					e.printStackTrace();
					return Errors.ENOMEM;
				}
				
				long curr_pos = 0;
				try {
					curr_pos = raf.getFilePointer();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				return curr_pos - prev_pos;
				
			} else {
				System.err.println("Proxy::read. File not opened yet");
				return Errors.EBADF;
			}
		}

		/**
		 * 
		 */
		public long lseek( int fd, long pos, LseekOption o ) {
			System.err.println("Proxy:: lseek. fd is " + fd + ", pos is " + pos);
			RandomAccessFile raf = null;
			if (path_map.containsKey(fd)) {
				String path = path_map.get(fd);
				raf = raf_map.get(path);
			} else {
				System.err.println("Proxy:: lseek. file not opened yet");
				return Errors.EBADF;
			}
			long newpos = 0;
			try {
				if (o == LseekOption.FROM_CURRENT) {
					newpos = pos + raf.getFilePointer();
				}
				if (o == LseekOption.FROM_END) {
					newpos = raf.length() - pos;
				}
				if (o == LseekOption.FROM_START) {
					newpos = pos;
				}
				raf.seek(newpos);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.err.println("Proxy::lseek. new pos is " + newpos);
			return newpos;
			
		}

		/**
		 * TODO need to close the file first?
		 */
		public int unlink( String path ) {
			System.err.println("Proxy::Unlink. path is " + path);
			File f = null;
			try {
				f = new File(path);

				if (f.exists()) {
					f.delete();
					return 0;
				} else {
					System.err.println("Proxy::unlink. file doesn't exist at all");
					return Errors.ENOENT;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return Errors.ENOENT;
		}

		public void clientdone() {
			return;
		}
	}

	private static class FileHandlingFactory implements FileHandlingMaking {
		public FileHandling newclient() {
			return new FileHandler();
		}
	}

	public static void main(String[] args) throws IOException {
		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}
