import java.net.Inet4Address;

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

public class BranchServer {

	public static int port;
	public static BranchRecieveHandler handler;
	public static Branch.Processor processor;

	public static void main(String[] args) {
		try {
			if (args != null && args.length ==2) {
				String branchName = args[0];
				final Integer branchPort = Integer.parseInt(args[1]);
				BranchID branch = new BranchID();	
				branch.setName(branchName);
				branch.setIp(Inet4Address.getLocalHost().getHostAddress());
				System.out.println("Running "+branchName + " on ip " + branch.getIp() + " port No: "+branchPort);
				branch.setPort(branchPort);
				handler = new BranchRecieveHandler(branch);
				processor = new Branch.Processor(handler);
				port = Integer.valueOf(args[1]);
				simple(processor,branchPort);

			}else{
				System.out.println("Please input branchName and port to run the branch server");	
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}



	public static void simple(Branch.Processor processor, Integer port) {
		try {
			TServerTransport serverTransport = new TServerSocket(port);
			TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));
			System.out.println("Starting the TThreadPoolServer server...");
			server.serve();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
