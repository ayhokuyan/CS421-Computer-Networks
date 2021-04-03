/**
 * CS421 Programming Assignment 1 - Ayhan Okuyan - Tolga Din�er 
 * Note: Only assumption made while this code is written is that the only directory/file
 * that contains the word "target" is the target file "target.jpg".
 * Also, since we can't possibly imagine form the givne data its type, I have treated the downloading as if
 * the only possible file that we can download are the jpg files.
 */
import java.io.*;
import java.net.*;
import java.util.ArrayList;

/**
 * This class is used as a helper class to apply a sequential search algorithm to find target.jpg
 * @author Ayhan Okuyan
 * @param contents is the nlst string received from the server
 * @param parent is the parent Node in the tree structure
 */
final class Node{
	private boolean isFin; //true if target.jpg is found
	private ArrayList<String> dirs; //the child directories of the node, in a processed manner
	private int vis_child; //number of child directories that are previously visited
	private Node parent;
	public Node(String contents, Node parent) {
		vis_child = 0;
		this.parent = parent;
		String[] contents_parsed = contents.split("\r\n");
		dirs = new ArrayList<String>();
		isFin = false;
		for(String token: contents_parsed) {
			//target found
			if(token.substring(0,6).equals("target")) {
				isFin = true;
			} else {
				if(token.substring(token.length()-2).equals(":d")) {
					dirs.add(token);
				}
			}
		}
		//add an exemplary string to declare an end node
		if(dirs.size() == 0){
			dirs.add("END_NODE");
		}
	}
	/**
	 * Gets if the target.jpg is found
	 * @return true if target is found
	 * @return false if target is not found
	 */
	public boolean getFin() {
		return isFin;
	}
	/**
	 * Returns the number of child directories
	 * @return dirs.size()
	 */
	public int getChildNum() {
		return dirs.size();
	}
	/**
	 * Returns if the node is an end node
	 * @return true if end node
	 * @return false if not end node
	 */

	public boolean isEnd() {
		if(dirs.get(0) == "END_NODE") {
			return true;
		}else {
			return false;
		}
	}
	/**
	 * This method sends the commands responsible to move up one directory and returns the parent Node object
	 * @param out_stream is the DataOutputStream
	 * @param serverSocket is the serverSocket that listens for the server
	 * @return Node parent is the parent node of the object on the tree structure
	 * @throws IOException if server is not found
	 */
	public Node goUp(DataOutputStream out_stream, ServerSocket serverSocket) throws IOException {
		SeekAndDestroy.sendCommand("CDUP", out_stream);
		SeekAndDestroy.dataConnNLST(out_stream, serverSocket);
		return parent;
	}
	/**
	 * This method goes down to one of the child structures, different one each time.
	 * If a child is previously visited, then it cannot go down to that same node again.
	 * @param out_stream is the DataOutputStream
	 * @param serverSocket is the serverSocket that listens for the server
	 * @return Node new node if there is any non-visited node.
	 * @throws IOException
	 */
	public Node goDown(DataOutputStream out_stream, ServerSocket serverSocket) throws IOException {
		if(vis_child < dirs.size()) {
			String str_temp = dirs.get(vis_child).substring(0, dirs.get(vis_child).length()-2);
			if(!str_temp.equals("END_NO")) {
				SeekAndDestroy.sendCommand("CWD", str_temp ,out_stream);
				String str = SeekAndDestroy.dataConnNLST(out_stream, serverSocket);
				vis_child++;
				return new Node(str,this);
			}
		} else {
			return goUp(out_stream, serverSocket);
		}
		return null;
	}
}
/**
 * This class is the expected class SeekAndDestroy that finds/downloads and deletes the target.jpg file.
 */
public class SeekAndDestroy {
	//main method
	public static void main(String[] args){
		//get the input arguments
		String host_name = args[0];
		String port_number = args[1];
		Socket socket;
		try {

			//Connection 
			socket = createSocket(host_name, port_number);

			//IO stream objects
			OutputStream out_stream = socket.getOutputStream();
			DataOutputStream out_client = new DataOutputStream(out_stream);
			InputStream in_stream = socket.getInputStream();
			BufferedReader in_buff = new BufferedReader(new InputStreamReader(in_stream));

			//Enter with user name and password
			sendCommand("USER", "bilkent", out_client);
			String server_response = in_buff.readLine();
			System.out.println("Server response: " + server_response);
			sendCommand("PASS", "cs421", out_client);
			server_response = in_buff.readLine();
			System.out.println("Server response: " + server_response);

			//Get available socket and bind
			int available_sckt_num = getListeningSocket();
			ServerSocket serverSocket = new ServerSocket();
			serverSocket.bind(new InetSocketAddress(host_name, available_sckt_num));

			//Send port number to server
			sendCommand("PORT", Integer.toString(available_sckt_num), out_client);

			//Search for the target
			Node nod = new Node(dataConnNLST(out_client, serverSocket), null); 
			while(!nod.getFin()) {
				if(!nod.isEnd()) {
					nod = nod.goDown(out_client, serverSocket);
				}else {
					nod = nod.goUp(out_client, serverSocket);
				}
			}

			//Download and delete target
			//Download
			byte[] target = dataConnRETR("target.jpg", out_client, serverSocket);
			FileOutputStream out = new FileOutputStream("received.jpg");
			out.write(target);
			out.close();

			//Delete
			sendCommand("DELE", "target.jpg", out_client);
			
			//QUIT
			sendCommand("QUIT", out_client);

			//Wait for the server to respond 
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {}
			
			//close the sockets and IO streams
			out_client.close();
			out_stream.close();
			in_stream.close();
			in_buff.close();
			socket.close();
			serverSocket.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method sends a NLST command to the server.
	 * @param out_str is the output stream of the socket connected to the server
	 * @param serverSocket is the server socket that listens to the server
	 * @return data is the string received from the server by the NLST command
	 * @throws IOException if the server can't be connected.
	 */
	public static String dataConnNLST(DataOutputStream out_str, ServerSocket serverSocket) throws IOException {
		//send NLST command to the server
		sendCommand("NLST", out_str);
		//accept incoming data
		Socket lisSocket = serverSocket.accept();
		//create input stream
		DataInputStream in_stream = new DataInputStream(lisSocket.getInputStream());
		//read the data in the form of a string
		String data = readData(in_stream);
		//print the current directory data to the output console
		System.out.println(data);
		lisSocket.close();
		return data;
	}
	/**
	 * This method sends a RETR command to the server
	 * @param arg is the file name that is to be retreived
	 * @param out_str is the output stream of the socket connected to the server
	 * @param serverSocket is the server socket that listens to the server
	 * @return data a byte array that is the received data
	 * @throws IOException if the server can't be connected
	 */
	public static byte[] dataConnRETR(String arg, DataOutputStream out_str, ServerSocket serverSocket) throws IOException {
		//send RETR command to the server
		sendCommand("RETR", arg, out_str);
		//accept incoming data
		Socket lisSocket = serverSocket.accept();
		//create input stream
		DataInputStream in_stream = new DataInputStream(lisSocket.getInputStream());
		//read the data in form of byte[]
		byte[] data = readByteData(in_stream);
		lisSocket.close();
		return data;		
	}
	/**
	 * This method reads a data from an input stream and converts it to a string
	 * @param rd is the input stream object
	 * @return data is the received data in string form
	 * @throws IOException if the server can't be connected
	 */
	public static String readData(DataInputStream rd) throws IOException{
		//get the size of the data
		int size = 0;
		size += 256*rd.readUnsignedByte();
		size += rd.readUnsignedByte();
		//read the data at the specified size
		String data = "";
		if(size != 0) {
			for(int i = 0; i < size; i++) {
				data += (char)rd.readByte();
			}
		}else {
			//identify empty node
			data += "END_NODE";
		}
		return data;
	}
	/**
	 * This method reads data from an input stream object in the form of a byte array
	 * @param rd is the input stream object
	 * @return data is the data received in the form of a byte array
	 * @throws IOException if the server can't be connected
	 */
	public static byte[] readByteData(DataInputStream rd) throws IOException {
		int size = 0;
		size += 256*rd.readUnsignedByte();
		size += rd.readUnsignedByte();
		byte[] data = new byte[size];
		if(size != 0) {
			for(int i = 0; i < size; i++) {
				data[i] = rd.readByte();	        }
		}
		return data;
	}
	/**
	 * This method creates a new socket
	 * @param host is the host address
	 * @param port_no is the port number on the specified address in string format
	 * @return socket if no exception is thrown
	 * @throws UnknownHostException if the host is not known
	 * @throws IOException if the server can't be connected
	 */
	public static Socket createSocket(String host, String port_no) throws UnknownHostException, IOException {
		int port = Integer.parseInt(port_no);
		Socket sckt = new Socket(host, port);
		return sckt;
	}
	/**
	 * This method creates a new socket
	 * @param host is the host address
	 * @param port_no is the port number on the specified address in int format
	 * @return socket if no exception is thrown
	 * @throws UnknownHostException if the host is not known
	 * @throws IOException if the server can't be connected
	 */
	public static Socket createSocket(String host, int port_no) throws UnknownHostException, IOException {
		Socket sckt = new Socket(host, port_no);
		return sckt;
	}
	/**
	 * Sends a command to the server with a secondary argument
	 * @param cmd_name is the command that will be sent
	 * @param arg is the secondary argument
	 * @param out_str is the output stream object that is connected to the server
	 * @throws IOException if the server can't be connected
	 */
	public static void sendCommand(String cmd_name, String arg, DataOutputStream out_str) throws IOException {
		System.out.println("Sending command " + cmd_name + " " + arg);
		String str = cmd_name + " " + arg + "\r\n";
		char[] letters = str.toCharArray();
		for (char ch : letters) { 
			out_str.writeByte((byte) ch); 
		}

	}
	/**
	 * Overloaded function of sendCommand without a secondary input argument
	 */
	public static void sendCommand(String cmd_name, DataOutputStream out_str) throws IOException {
		System.out.println("Sending command " + cmd_name);
		String str = cmd_name + "\r\n";
		char[] letters = str.toCharArray();
		for (char ch : letters) { 
			out_str.writeByte((byte) ch);
		}
	}
	/**
	 * This method searches for an unoccupied local port in the interval port number 50000-60000
	 * @return the first available port number in integer format
	 */
	public static int getListeningSocket(){
		for (int port = 50000 ; port <= 60000 ; port++){
			try {
				ServerSocket skt = new ServerSocket(port); 
				skt.close();
				return port;
			} catch (IOException e) {}	
		}
		return 0;
	}

}
