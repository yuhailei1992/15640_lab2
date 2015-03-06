import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;

//You should investigate when to use UnicastRemoteObject vs Serializable. This is really important!
public class Server extends UnicastRemoteObject implements IServer, Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	// static variables
    public static String serverrootdir;
    public static int serverport;
    public static ConcurrentHashMap<String, RandomAccessFile> rafMap;
    // constructor
    public Server() throws RemoteException {
        versionMap = new ConcurrentHashMap<String, Integer>();
        rafMap = new ConcurrentHashMap<String, RandomAccessFile>();
    }
    
    public long getFileSize(String orig_path) throws RemoteException {
    	long size = 0;
    	try {
    		File f = new File(serverrootdir + orig_path);
    		size = f.length();
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return size;
    }

    /**
     * 
     */
    public synchronized int removeServerFile(String orig_path) throws RemoteException {
    	System.err.println("Server::unlink");
    	// 1, update version map
    	if (versionMap.containsKey(orig_path)) {
    		versionMap.remove(orig_path);
    	}
    	// 2, delete the file
    	String server_path = serverrootdir + orig_path;
    	File f = null;
    	try {
            f = new File(server_path);
            if (f.exists()) {
                f.delete();
                return 0;
            } else {
                System.err.println("Error:Proxy:unlink. file doesn't exist at all");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    	return -1;
    }
    
    /**
     * get version number of a file.
     * -1 represents non-existency
     */
    public int getVersion(String orig_path) throws RemoteException {
        int ver = 0;
        try {
            if (versionMap.containsKey(orig_path)) {
                ver = versionMap.get(orig_path);
            } else {
                ver = -1;
            }
        } catch (Exception e) {
            System.err.println("Error in getVersion");
        }
        System.err.println("Server::getVersion for file " + orig_path + " is " + ver);
        return ver;
    }

    /**
     * this function will write a byte array to a file within the server's cache directory
     */
    
    public synchronized void writeInChunkPrep(String orig_path) throws RemoteException {
    	System.err.println("Server::writeToServerPrep");
    	String server_path = serverrootdir + orig_path;
    	if (versionMap.containsKey(orig_path)) {
            versionMap.put(orig_path, versionMap.get(orig_path) + 1);
            System.err.println("The new version num is " + versionMap.get(orig_path));
        } else {
            versionMap.put(orig_path, 1);
        }
    	File file = new File(server_path);
    	if (file.exists()) {
            System.err.println("The file already exists, need to delete it first, then write");
            System.err.println("The file is " + server_path + "of version " + versionMap.get(orig_path));
            if(file.delete()) {
                System.err.println(file.getName() + " is deleted!");
            } else {
                System.err.println("Delete operation failed.");
            }
        }
    }
    
    public synchronized void writeToServer (String orig_path, byte[] b) throws RemoteException {
    	
    }
    
    public void writeInChunk(String orig_path, long start_offset, byte[] b) throws RemoteException {
    	//System.err.println("Server::writeInChunk");

        // 2, write the byte array to file
        String server_path = serverrootdir + orig_path;
        // 3, write to the file
        try {
        	RandomAccessFile raf = new RandomAccessFile(server_path, "rw");
        	raf.seek(start_offset);
        	raf.write(b);
        	raf.close();
        } catch (Exception e) {
            System.err.println("Error while writing to servercache in chunks");
        }
    }

    /**
     * this function will get the content of a file provided its path
     */
    public byte[] getFileContent(String orig_path) throws RemoteException {
        System.err.println("Server::getFileContent");
        String localpath = serverrootdir + orig_path;

        File file = new File(localpath);

        byte[] b = new byte[(int) file.length()];
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(b);
            fileInputStream.close();
        } catch (FileNotFoundException e) {
            System.err.println("File Not Found.");
            e.printStackTrace();
        }
        catch (IOException e1) {
            System.err.println("Error Reading The File.");
            e1.printStackTrace();
        }
        return b;
    }
    
    public byte[] readInChunk(String path, long start_offset, long chunksize) throws RemoteException {
    	// System.err.println("Server::readInChunk");
    	// 1, check if the path exists
    	String server_path = serverrootdir + path;
    	byte[] b = new byte[(int)chunksize];
    	RandomAccessFile raf = null;
    	try {
    		// 2, read a chunk
    		raf = new RandomAccessFile(server_path, "r");
    		raf.seek(start_offset);
            raf.read(b);
            raf.close();
            return b;
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return b;
    }

    
    /**
     * return 0 on success, -1 on failure
     */
    public int createFile(String orig_path) throws RemoteException {
    	System.err.println("Server:createFile " + orig_path);
    	String server_path = serverrootdir + orig_path;
    	File file = new File(server_path);
    	try {
    		file.createNewFile();
    		return 0;
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return -1;
    }

    public static ConcurrentHashMap<String, Integer> versionMap;

    /**
     * this function is used when server initializes
     * @param folder
     * @param superfolder
     */
    public static void listFilesForFolder (final File folder, String superfolder) {
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                String folderpath = "";
                if (superfolder.length() > 0) {
                    folderpath = superfolder + "/" + fileEntry.getName();
                } else {
                    folderpath = fileEntry.getName();
                }
                listFilesForFolder(fileEntry, folderpath);
            } else {
                String filename = "";
                if (superfolder.length() > 0) {
                    filename = superfolder + "/" + fileEntry.getName();
                } else {
                    filename = fileEntry.getName();
                }
                System.err.println(filename);
                versionMap.put(filename, 0);
            }
        }
    }

    /**
     * two args
     * @param args
     */
    public static void main(String [] args) {
        System.err.println("Cache server has started");
        serverport = Integer.parseInt(args[0]);
        serverrootdir = args[1];
        if (serverrootdir.charAt(serverrootdir.length()-1) != '/') {
            serverrootdir += '/';
        }

        try {
            //create the RMI registry if it doesn't exist.
            LocateRegistry.createRegistry(serverport);
        }
        catch(RemoteException e) {
            System.err.println("Failed to create the RMI registry " + e);
        }

        Server server = null;
        try {
            server = new Server();
            final File folder = new File(serverrootdir);
            listFilesForFolder(folder, "");
        }
        catch(RemoteException e) {
            System.err.println("Failed to create server " + e);
            System.exit(1);
        }

        try {
            Naming.rebind(String.format("//127.0.0.1:%d/ServerService", serverport), server);
        } catch (RemoteException e) {
            System.err.println(e); //you probably want to do some decent logging here
        } catch (MalformedURLException e) {
            System.err.println(e); //same here
        }
    }
}
