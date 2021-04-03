/**
 * CS 421 Pragramming Assigment 2 - Ayhan Okuyan - Baris Akcin
 */
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * This class handles the client operations separately for each client
 * @author Ayhan Okuyan & Baris Akcin
 */
class ClientHandler implements Runnable  
{ 
	//Error Messages
	static String successMes = "200 OK\r\n";
	static String fail1 = "400 File name with same name already exists\r\n";
	static String fail2 = "400 Folder name with same name already exists\r\n";
	static String fail3 = "400 File doesn't exist in the current working directory\r\n";
	static String fail4 = "400 Folder doesn't exist in the current working directory\r\n";
	static String fail5 = "400 Current working directory is the root folder\r\n";

	final DataInputStream inpStr; 
	final DataOutputStream outStr; 
	Socket sckt;
	boolean isConn;
	private int dataPortRecv;
	private int dataPortSend;
	private String host;
	private String dir;
	private String root;
	private String clNo;

	/**
	 * This is the constructor for the Client Handler class 
	 * @param sckt is the Socket defined for the client
	 * @param inpStr is the input stream of the respective client
	 * @param outStr is the output stream of the respective client
	 * @param hostNo is the host name of the server machine
	 * @param clNo is the number defined for the client (to hold track of the separate client print statements)
	 */
	public ClientHandler(Socket sckt, DataInputStream inpStr, DataOutputStream outStr, String hostNo,String clNo) { 
		this.inpStr = inpStr; 
		this.outStr = outStr;  
		this.sckt = sckt; 
		this.isConn=true; 
		this.host = hostNo;
		dataPortRecv = 0;
		dataPortSend = 0;
		//get the root directory from the machine
		dir = System.getProperty("user.dir");
		//dir += "\\src\\shit";
		root = dir;
		this.clNo = clNo;
	} 

	/**
	 * Run operation for each client
	 */
	@Override
	public void run() { 
		BufferedReader inpBuf = new BufferedReader(new InputStreamReader(this.inpStr));
		String data;
		File[] files;


		while (true)  
		{ 
			try
			{ 
				while(!inpBuf.ready()) {

				}
				String received = inpBuf.readLine(); 

				System.out.println(clNo + ": " + received); 
				StringTokenizer st = new StringTokenizer(received, " "); 
				String command = st.nextToken(); 

				/**
				 * Port command
				 * Receives the client port for data connection
				 */
				if(command.equals("PORT")) {
					data = st.nextToken();
					dataPortRecv = Integer.parseInt(data);
					outStr.writeBytes(successMes);
					System.out.println(data);
				/**
				 * Gprt command
				 * Sends the server port to client for data connection
				 */
				}else if (command.equals("GPRT")) {
					this.outStr.writeBytes(successMes);
					dataPortSend = getAvSocket();
					System.out.println(dataPortSend);
					String port_str = Integer.toString(dataPortSend);
					Socket scktGPRT = new Socket(host,dataPortRecv);
					DataOutputStream outStrGPRT = new DataOutputStream(scktGPRT.getOutputStream());
					
					int size = Integer.toString(dataPortSend).length();
					
					byte[] size_byte = (""+size).getBytes("ASCII");
					outStrGPRT.write(size_byte);
					//send data
					byte[] data_byte = port_str.getBytes("ASCII");
					outStrGPRT.write(data_byte);
					System.out.println("SERVER PORT:   " + port_str); 	
					
					outStrGPRT.close();			
					scktGPRT.close();
				/**
				 * Nlst command
				 * Sends the current working directory to client as a specialized string
				 */
				}else if (command.equals("NLST")) {
					this.outStr.writeBytes(successMes);
					int dataPortNLST = dataPortRecv;
					Socket scktNLST = new Socket(host,dataPortNLST);
					DataOutputStream outStrNLST = new DataOutputStream(scktNLST.getOutputStream());
					files = new File(dir).listFiles();
					//sends zeros if the current directory is empty
					if(files.length <= 0) {
						String zeros = "00";
						byte[] data_byte = zeros.getBytes("ASCII");
						outStrNLST.write(data_byte);
						System.out.println("Empty NLST, Sending zeros");
					}else {
						//create the path string
						String pathToSend = "";
						for(File f: files) {
							if(f.isFile()) {
								pathToSend += f.getName() + ":f\r\n";
							}else if (f.isDirectory()) {
								pathToSend += f.getName() + ":d\r\n";
							}
						}
						pathToSend = pathToSend.substring(0,pathToSend.length()-2);
						//send header
						int size = pathToSend.length();
						byte[] size_byte = (""+size).getBytes("ASCII");
						outStrNLST.write(size_byte);
						//send data
						byte[] data_byte = pathToSend.getBytes("ASCII");
						outStrNLST.write(data_byte);
						System.out.println("Path: " + pathToSend); 	
					}

					outStrNLST.close();
					scktNLST.close();

				/**
				 * Cwd command
				 * moves the current directory to given child directory if exists
				 */
				}else if (command.equals("CWD")) {
					files = new File(dir).listFiles();
					data = st.nextToken();
					boolean erorr = true;
					for(File f: files) {
						if(f.isDirectory() && data.equals(f.getName())) {
							erorr = false;
							dir += "\\" + data;
							this.outStr.writeBytes(successMes);
						}
					}
					if(erorr) {
						this.outStr.writeBytes(fail4);
					}

				/**
				 * Cdup command
				 * Moves the current directory to the parent directory if current directory is not
				 * the root directory
				 */
				}else if (command.equals("CDUP")) {
					if(dir.equals(root)) {
						this.outStr.writeBytes(fail5);
					}else {
						dir = ((new File(dir)).getParent());
						this.outStr.writeBytes(successMes);
					}
				/**
				 * Put command
				 * Receives the data from the server port and saves it to the current directory
				 * if another file with the same name doesn't exist
				 */
				}else if (command.equals("PUT")) {
					files = new File(dir).listFiles();
					data = st.nextToken();
					boolean erorr = false;
					//check if the same name exists
					for(File f: files) {
						if(f.isFile() && data.equals(f.getName())) {
							erorr = true;
							this.outStr.writeBytes(fail1);
						}
					}
					if(!erorr) {
						this.outStr.writeBytes(successMes);
						
						ServerSocket serverSocket = new ServerSocket();
						serverSocket.bind(new InetSocketAddress(host,dataPortSend));
						Socket scktPUT = serverSocket.accept();
						DataInputStream inpStrPUT = new DataInputStream(scktPUT.getInputStream());
						//read the size header
						int size = 0;
						size += 256*inpStrPUT.readUnsignedByte();
						size += inpStrPUT.readUnsignedByte();
						//read the data according to the size
						byte[] data_byte = new byte[size];
						if(size != 0) {
							for(int i = 0; i < size; i++) {
								data_byte[i] = inpStrPUT.readByte();
							}
						}
						//write the received file to the current working directory
						FileOutputStream out = new FileOutputStream(dir +"\\" + data);
						out.write(data_byte);
						
						out.close();
						serverSocket.close();
						inpStrPUT.close();
						scktPUT.close();
					}

				/**
				 * Mkdr command
				 * Creates a new folder in the current working directory if there is
				 * no other folder with the same name
				 */
				}else if (command.equals("MKDR")) {
					files = new File(dir).listFiles();
					data = st.nextToken();
					boolean exist = false;
					for(File f: files) {
						if(f.getName().equals(data)) {
							this.outStr.writeBytes(fail2);
							exist = true;
							break;
						}
					}
					if(!exist) {
						this.outStr.writeBytes(successMes);
						new File(dir + "\\" + data).mkdir();
					}
				/**
				 * Retr command
				 * Sends the requested file in current working directory to the client
				 * from the data port if the file exist in the directory
				 */
				}else if (command.equals("RETR")) {
					this.outStr.writeBytes(successMes);
					data = st.nextToken();
					int dataPortRETR = dataPortRecv;
					
					Socket scktRETR = new Socket(host,dataPortRETR);
					DataOutputStream outStrRETR = new DataOutputStream(scktRETR.getOutputStream());
					files = new File(dir).listFiles();

					boolean alrExist = false;
					for(File f: files) {
						//check if the file exists
						if(f.isFile() && f.getName().equals(data)) {
							this.outStr.writeBytes(successMes);
							alrExist = true;
							File target = new File(dir + "\\" + data);
							FileInputStream fis = new FileInputStream(target);

							//send header
							long size = target.length();
							System.out.println(size);
							int size_1 = (int)size / 256;
							int size_2 = (int)size % 256;
							byte[] size_byte = {(byte)size_1, (byte)size_2};
							outStrRETR.write(size_byte);
							
							//send data
							byte[] data_byte = new byte[(int)target.length()];							
							fis.read(data_byte);
							outStrRETR.write(data_byte);
							
							fis.close();
						}
					}
					if(!alrExist) {
						this.outStr.writeBytes(fail1);
					}

					outStrRETR.close();
					scktRETR.close();
				/**
				 * Dele command
				 * Deletes target file from the current working directory if it exists
				 */
				}else if (command.equals("DELE")) {
					files = new File(dir).listFiles();
					data = st.nextToken();
					//check if the file exists
					for(File f: files) {
						if(data.equals(f.getName()) && f.isFile()) {
							if(f.delete()) {
								this.outStr.writeBytes(successMes);
							}else {
								this.outStr.writeBytes(fail3);
							}
						}
						this.outStr.writeBytes(fail3);
					}
				/**
				 * Ddir command
				 * Deletes the folder in the current working directory if it exists.
				 */
				}else if (command.equals("DDIR")) {
					files = new File(dir).listFiles();
					data = st.nextToken();
					for(File f: files) {
						if(f.getName().equals(data) && f.isDirectory()) {
							if(f.delete()) {
								this.outStr.writeBytes(successMes);
							}else {
								this.outStr.writeBytes(fail3);
							}
						}
					}
				/**
				 * Quit command
				 * Client disconnects from server
				 */
				}else if (command.equals("QUIT")) {
					this.outStr.writeBytes(successMes);
					this.isConn=false; 
					this.sckt.close(); 
					break;
				}
			} catch (IOException e) { 
				e.printStackTrace(); 
			} 
		} 
		try
		{ 
			this.inpStr.close(); 
			this.outStr.close(); 
		}catch(IOException e){ 
			e.printStackTrace(); 
		} 
	}

	/**
	 * This method searches for an unoccupied local port in the interval port number 30000-40000
	 * @return the first available port number in integer format
	 */
	public static int getAvSocket(){
		for (int port = 30000 ; port <= 40000 ; port++){
			try {
				ServerSocket skt = new ServerSocket(port); 
				skt.close();
				return port;
			} catch (IOException e) {}	
		}
		return 0;
	}

} 

/**
 * This is the CustomFTPServer class which is a multithreading server class that connects with the 
 * client, creates a ClientHandler for each client and stores them in an ArrayList
 * @author Ayhan Okuyan - Baris Akcin
 */
public class CustomFTPServer {

	static ArrayList<ClientHandler> connections = new ArrayList<ClientHandler>();
	static int clientNum = 0;
	
	//main method
	public static void main(String[] args) throws IOException{
		//receive the host and port numbers of the server
		String host = args[0];
		int portNo = Integer.parseInt(args[1]);

		//We are suppressing warnings since this ServerSocket is never closed
		@SuppressWarnings("resource")
		ServerSocket serSock = new ServerSocket(portNo);
		Socket client; 
		
		// running infinite loop for getting client requests
		while (true)  
		{ 
			//Accept request
			client = serSock.accept(); 

			System.out.println("New Client with address: " + client.getPort()); 

			//IO streams 
			DataInputStream dis = new DataInputStream(client.getInputStream()); 
			DataOutputStream dos = new DataOutputStream(client.getOutputStream()); 


			// Creating new handler
			System.out.println("Creating new ClientHandler instance"); 
			ClientHandler cHandler = new ClientHandler(client, dis, dos, host, "Client"+clientNum); 

			// Creating new thread for the client for multithreading
			Thread thread = new Thread(cHandler); 
			
			// add this client to the client list 
			connections.add(cHandler); 

			// start the thread. 
			thread.start(); 

			// increment i for new client. 
			// i is used for naming only, and can be replaced 
			// by any naming scheme 
			clientNum++; 

		} 

	}
}


