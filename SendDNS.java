import java.net.*;
import java.io.*;
import java.util.*;

class SendDNS {

    static final short DNSPORT = 53;

    static void usage() {
	System.out.println("Usage: java SendDNS nameserver domain_name/ip_address");
	System.exit(1);
    }

    static byte[] parseInetAddress(String inetAddr) throws NumberFormatException {
	String[] sp = inetAddr.split("\\.");
	byte[] addr = new byte[4];
	if(sp.length!=4){
	    throw new NumberFormatException();
	}
	for(int i=0; i<4; i++){
	    int x = Integer.parseInt(sp[i]);
	    if(x<0 || x>255)
		throw new NumberFormatException();
	    addr[i] = (byte) x;
	}
	return addr;
    }

    static public void main(String[] args) throws IOException{
	boolean debug = false;

	if(args.length<2) usage();
  
	String nameserver = args[0];
	String hostname = args[1];
	    
	System.out.printf("Sending DNS Query (%s) to server %s\n",hostname,nameserver);

	byte[] serveraddr = null;
	try {
	     serveraddr = parseInetAddress(nameserver);
	}
	catch(NumberFormatException e){
	    System.out.printf("Invalid nameserver %s\n",nameserver);
	    System.exit(1);
	}

	DNS result = sendRequest(serveraddr, hostname);
	System.out.println("Response:\n");
	System.out.println(result);
    }

    static DNS sendRequest(byte[] serveraddr, String hostname) throws IOException {
	/* create a datagram socket to send messages */
	DatagramSocket dSocket = null;
	try {
	    dSocket = new DatagramSocket();
	}
	catch(IOException e){
	    System.err.println(e);
	    System.exit(1);
	}

	// using a constant name server address for now.

	InetAddress serverAddress = null;
	/* get inet address of name server */
	try {
	    serverAddress = InetAddress.getByAddress(serveraddr);
	}
	catch(UnknownHostException e){
	    System.err.println(e);
	    System.exit(1);
	}

	/* set up buffers */
	String line;
	byte[] inBuffer = new byte[1000];

        DatagramPacket outPacket = new DatagramPacket(new byte[1],1,serverAddress,DNSPORT);
        DatagramPacket inPacket = new DatagramPacket(inBuffer,1000);

	// construct the query message in a byte array
	byte[] query = new byte[1500];
	int querylen=DNS.constructQuery(query,1500,hostname);

	// construct a DNS object from the byte array
	DNS dnsMessage = new DNS(query);
	System.out.println("Sending query:"+dnsMessage+" to "+serverAddress);

	// send the byte array as a datagram
	outPacket.setData(query,0,querylen);
	for(int i=0; i<200; i++)
	    dSocket.send(outPacket);

	// await the response 
	dSocket.receive(inPacket);

	byte[] answerbuf = inPacket.getData();

	DNS response = new DNS(answerbuf);
	return response;
    }
}
