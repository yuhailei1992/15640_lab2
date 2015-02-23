import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.io.Serializable;
import java.io.*;
//You should investigate when to use UnicastRemoteObject vs Serializable. This is really important!
public class Server extends UnicastRemoteObject implements IServer, Serializable {

	public Server() throws RemoteException {
		versionMap = new HashMap<String, Integer>();
	}

	public String sayHello() throws RemoteException{
		return "Hello :)";
	}
	
	public int getVersion(String path) throws RemoteException {
		int ver = 0;
		try {
			if (versionMap.containsKey(path)) {
				ver = versionMap.get(path);
			} else {
				ver = -1;
			}
		} catch (Exception e) {
			System.out.println("Error in getVersion");
		}
		return ver;
	}

	public File getFile(String path) throws RemoteException {
		// return the file
		try {
			String newpath = "../servercache/" + path;
			File file = new File(newpath);
			return file;
		} catch (Exception e) {
			System.out.println("Error in getFile");
			return null;
		}
	}
	
	public void writeToServer (String path, byte[] b) throws RemoteException {
		// update versionmap first
		if (versionMap.containsKey(path)) {
			versionMap.put(path, versionMap.get(path) + 1);
		} else {
			versionMap.put(path, 1);
		}
		
		// then, write the byte array to file
		String newpath = "../servercache/" + path;
		File file = new File(newpath);
		try {
			FileOutputStream fos = new FileOutputStream(newpath);
            fos.write(b);
            fos.close();
		} catch (Exception e) {
			System.out.println("Error while writing to servercache");
		}
	}
	
	public byte[] getFileContent(String path) throws RemoteException {
		String newpath = "../servercache/" + path;
		
		File file = new File(newpath);

        byte[] b = new byte[(int) file.length()];
        try {
              FileInputStream fileInputStream = new FileInputStream(file);
              fileInputStream.read(b);
              
        } catch (FileNotFoundException e) {
                     System.out.println("File Not Found.");
                     e.printStackTrace();
        }
         catch (IOException e1) {
                  System.out.println("Error Reading The File.");
                   e1.printStackTrace();
        }
        return b;
	}
	
	public static HashMap<String, Integer> versionMap;
	
	public static void main(String [] args) {

		int port = 12345; //you should get port from args

		try {
			//create the RMI registry if it doesn't exist.
			LocateRegistry.createRegistry(port);
		}
		catch(RemoteException e) {
			System.err.println("Failed to create the RMI registry " + e);
		}

		Server server = null;
		try{
			server = new Server(); 
		}
		catch(RemoteException e) {
			//You should handle errors properly.
			System.err.println("Failed to create server " + e);
			System.exit(1);
		}
		try {
			Naming.rebind(String.format("//127.0.0.1:%d/ServerService", port), server);
		} catch (RemoteException e) {
			System.err.println(e); //you probably want to do some decent logging here
 		} catch (MalformedURLException e) {
			System.err.println(e); //same here
		}
	}
}