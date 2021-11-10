//package project2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Date;
import java.util.Properties;

public class HTTPServer implements Runnable {

	private Socket socket;
	private String docRoot;

	public HTTPServer(Socket socket, String docRoot) { // this is used when establishing connections with multiple
														// threads. each thread must have access to the socket and
														// docRoot
		this.socket = socket;
		this.docRoot = docRoot;
	}

	public static void main(String[] args) throws IOException {

		int port = 8080; // default port
		String docRoot = null;

		if (args.length == 0) {	//if 0 command line arguments, assume it's in config	      
	        Properties config = new Properties();
	        config.load(new FileInputStream(System.getProperty("user.dir")+"/resources/config.properties"));
	        System.out.println("CONFIG = "+System.getProperty("user.dir")+"/resources/config.properties");
			try {
				port = Integer.parseInt(config.getProperty("port"));
			} catch (NumberFormatException e) {
				System.out.println("port=" + config.getProperty("port") + " could not be parsed as an int. Exiting..");
				System.exit(0);
			}
			docRoot = config.getProperty("docRoot");	
		}
		if (args.length == 1) {	//if one arg, assume it's the docRoot and port is default 8080
			docRoot = args[0];
		}
		if (args.length == 2) {	//if two, assume first one is port, second is docRoot
			try {
				port = Integer.parseInt(args[0]); // configurable port
			} catch (NumberFormatException e) {
				System.out.println("args[0] = " + args[0] + " could not be parsed as an int. Exiting..");
				System.exit(0);
			}
			docRoot = args[1];
		}
		if (args.length > 2) {	//too many arguments
			System.out.println(
					"Too many arguments:\njava HTTPServer [port] <docroot>\nExiting..");
			System.exit(0);
		}
		
		ServerSocket serverSocket = new ServerSocket(port);
		System.out.println("Listening on port: " + port); // port is either 8080 or passed by argument

		while (true) { // continuous connections
			Socket socket = serverSocket.accept(); // establish connection
			Thread newConnect = new Thread(new HTTPServer(socket, docRoot));
			newConnect.start(); // make new thread and start it, pass the socket and docRoot
		}

	}

	@Override
	public void run() { // for each thread
		Socket socket = this.socket; 	// get socket and docRoot
		String docRoot = this.docRoot;

		try {
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // get streams
			String requestStr = in.readLine(); 		// get request
			String[] request = requestStr.split(" "); // parse request (into array)
			
			System.out.println("Request received: ");
			for (int i = 0; i < request.length; i++) {
				System.out.println("INDEX = " + i + " = " + request[i]);	//print request
			}

			if (isValidGET(request)) { // valid = true if we have a 'valid' GET request (3 args, starts with GET and is valid HTTP version)
				
				request[1] = request[1].replace("%20", " ");	//decode the %20 we get from the request.. IF the GET has spaces in it they change to %20, ex. INDEX = 1 = /Clip%20for%20Server.mp4
				File f = new File(docRoot + "/" + request[1]);	//docRoot+/Clip for Server.mp4  for file path

				if (!f.exists()) { // file doesn't exist
					System.out.println("File doesn't exist - " + f.toString());
					serve404(out); // return 404
					} 		
				else { // file does exist in the docRoot. we must serve it
					System.out.println("File exists = " + f.toString());
					if (request[1].matches("[/]+")) { // return index.html if request is / or /+ (//, ///, etc.)
						File index = new File(docRoot + "/index.html");
						if (index.exists() && index.isFile()) {	//make sure it's a file
							System.out.println("request[1] = / and index.html exists so serving index....");
							serveFile(out, index, socket); // serve index
						} //else.. the browser requested ex. localhost:8080 but we don't have an index.html to serve it. browser will just say "localhost didn't send any data. ERR_EMPTY_RESPONSE"
					} else {	//file doesn't match "/", serve other file
						if(f.isFile()) {	
							serveFile(out, f, socket);	//if file, serve it. localhost:8080/images/cat.jpeg
						}else {
							serve404(out); 			//not a file (can't serve it). could be a directory like localhost:8080/images, so return 404
						}
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			socket.close(); // close socket
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void serveFile(PrintWriter out, File f, Socket socket) throws IOException {

		String suffix = "";

		int i = f.toString().lastIndexOf(".");
		if (i > 0) {
			suffix = f.toString().substring(i); // get filename extension (.jpg, etc.)
			System.out.println("Suffix = " + suffix);
		}

		System.out.println("Serving file..");
		out.println("HTTP/1.1 200 OK");
		Date date = new Date();
		out.println("Date: " + date.toString());
		out.println("Server: HTTPServer");
		
		if (suffix.equals(".html")) { 				// print content type depending on file suffix/extension, few  of the most common ones below. 
			out.println("Content-Type: text/html");
		} else if (suffix.equals(".css")) {
			out.println("Content-Type: text/css");
		} else if (suffix.equals(".txt")) {
			out.println("Content-Type: text/plain");
		} else if (suffix.equals(".json")) {
			out.println("Content-Type: application/json");
		} else if (suffix.equals(".pdf")) {
			out.println("Content-Type: application/pdf");
		} else if (suffix.equals(".jpeg")) {
			out.println("Content-Type: image/jpeg");
		} else if (suffix.equals(".png")) {
			out.println("Content-Type: image/png");
		} else if (suffix.equals(".mp4")) {
			out.println("Content-Type: video/mp4");
		} else if (suffix.equals(".mpeg")) {
			out.println("Content-Type: audio/mpeg");
		}

		out.println("Content-Length: " + f.length());
		out.println(); // \r\n

		Files.copy(f.toPath(), socket.getOutputStream()); // copy the file contents to output

	}

	private void serve404(PrintWriter out) throws IOException { // serve custom 404
		System.out.println("Serving 404..");
		System.out.println(System.getProperty("user.dir")+"/resources/index.html");
		out.println("HTTP/1.1 404 OK");
		Date date = new Date();
		out.println("Date: " + date.toString());
		out.println("Server: HTTPServer");
		out.println("Content-Type: text/html");

		File f = new File(System.getProperty("user.dir") + "/resources/index.html"); // get custom 404 html in resources
		out.println("Content-Length: " + f.length());
		out.println(); // \r\n

		Files.copy(f.toPath(), socket.getOutputStream());

	}

	private boolean isValidGET(String[] request) { // check if the request is what we are expected to deal with
		if (request.length != 3) {
			System.out.println("Invalid request received (not 3 args), exiting..");
			return false;
		}
		if (!request[0].startsWith("GET")) { // not a GET
			System.out.println("Invalid request received (not GET request), exiting..");
			return false;

		}

		if (!request[2].equals("HTTP/1.0") && !request[2].equals("HTTP/1.1")) { // not http1.0 or http1.1 so it's invalid protocol
			System.out.println("Invalid HTTP version");
			return false;
		}

		return true;
	}

}
