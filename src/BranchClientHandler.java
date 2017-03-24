import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;


public class BranchClientHandler {

	public   BranchClientHandler() {
		

	}



	public void transferMoney(BranchID sendingbranch, BranchID recievingBranch,int transferAmount, int messageID){
		try {
			Branch.Client client = fetchClient(recievingBranch);
			TransferMessage message = new TransferMessage();
			message.amount = transferAmount;
			message.orig_branchId = sendingbranch;
			client.transferMoney(message, messageID);
		} catch (Exception e) {
			System.out.println(e);
		}	
	}


	public Branch.Client fetchClient(BranchID recievingBranch){
		Branch.Client client=null;
		try {
			TTransport transport;
			transport = new TSocket(recievingBranch.getIp(),recievingBranch.getPort());
			transport.open();
			TProtocol protocol = new  TBinaryProtocol(transport);
			client = new Branch.Client(protocol);

		} catch (Exception x) {
			System.out.println(x);
		} 

		return client;
	}

}
