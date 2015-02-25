import java.rmi.Remote;
import java.rmi.RemoteException;
import java.io.*;

public interface IServer extends Remote {
    public String sayHello() throws RemoteException;
    public int getVersion(String path) throws RemoteException;
    public File getFile(String path) throws RemoteException;
    public byte[] getFileContent(String path) throws RemoteException;
    public void writeToServer (String path, byte[] b) throws RemoteException;
}
