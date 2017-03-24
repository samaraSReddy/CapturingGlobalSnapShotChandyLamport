import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import org.apache.thrift.TException;

public class Controller{

	private static List<BranchID> listOfBranches= new ArrayList<BranchID>();	

	private static Timer timer = new Timer();
	public static void main(String [] args) throws Exception{	
		if(args!= null && args.length==2 ){
			BufferedReader reader = null;
			String line=null;	
			reader = new BufferedReader(new FileReader(args[1]));
			line= reader.readLine();
			while(line!=null ){
				String branchName = null;
				String branchIP = null;
				Integer port = null;
				Pattern pattern = Pattern.compile("(\\s+)");
				String[] branchParamsString = pattern.split(line);			
				if(branchParamsString!=null && branchParamsString.length==3){ 

					branchName=branchParamsString[0];
					branchIP= branchParamsString[1];
					port=Integer.parseInt(branchParamsString[2]);
					BranchID branch= new BranchID();
					branch.setIp(branchIP);
					branch.setPort(port);	
					branch.setName(branchName);
					listOfBranches.add(branch);
				}	
				line=reader.readLine();
			}
			if(listOfBranches!=null && listOfBranches.size()>0 && args.length>0 && args[0]!=null){	
				Integer initialBalance= (Integer.parseInt(args[0]))/(listOfBranches.size());
				for(BranchID branch :listOfBranches){
					System.out.println("Initializing branch :"+branch.name);
					initializeBranch(branch, listOfBranches,initialBalance);
				}
			}
			new Thread(new Runnable() {
				@Override
				public void run() {
					initiateAndRetrieveSnapshots();
				}
			}).start();
		}else{
			System.out.println("Please input the balance and file to start");
		}
	}




	public static void initializeBranch(BranchID branch,List<BranchID> listOfBranches, Integer balance) throws SystemException, TException{
		BranchClientHandler clientHandler =  new BranchClientHandler();
		Branch.Client client= clientHandler.fetchClient(branch);
		client.initBranch(balance, listOfBranches);
	}



	private  static void initiateAndRetrieveSnapshots()  {
		SnapshotScheduler scheduler= new SnapshotScheduler(1);
		scheduler.run();
	}

	static class  SnapshotScheduler extends TimerTask{
		SnapshotScheduler(Integer snapshotId){
			snapshotNo=snapshotId;
		}
		int snapshotNo=1;
		@Override
		public void run() {
			try {
				Thread.sleep(2000);
				BranchID branchid= selectRandomBranch();
				BranchClientHandler clientHandler =  new BranchClientHandler();
				Branch.Client client= clientHandler.fetchClient(branchid);
				System.out.println();
				System.out.println("Initiating snapshot for "+branchid.getName()+ " with snapshot no :"+snapshotNo );
				System.out.println();
				client.initSnapshot(snapshotNo);
				Thread.sleep(30000);
				int i=1;
				for(BranchID id: listOfBranches){
					Branch.Client client2= clientHandler.fetchClient(id);
					System.out.println("Retrieving snapshot from "+id.getName());
					LocalSnapshot snapshot= client2.retrieveSnapshot(snapshotNo);
					System.out.print("{ branch"+ i +"  "+ snapshot.balance+ "}");
					int j=1;
					if(snapshot.getMessages()!=null){
						for(Integer channel:snapshot.getMessages()){
							System.out.print(" {"+"branch"+(j=(j==i)? j+1:j)+"->branch"+i+ "  "+ channel+"}");
							j++;
						}
					}
					i++;
					System.out.println();
				}
				timer.schedule(new SnapshotScheduler(snapshotNo+1), 5000);
			} catch (SystemException e) {
				System.out.println(e.toString());
			} catch (InterruptedException e) {
				System.out.println(e);
			} catch (TException e) {
				System.out.println(e);		
			}
		}
	}

	private static synchronized BranchID selectRandomBranch() {
		Random random = new Random();
		BranchID[] branchids=listOfBranches.toArray(new BranchID[listOfBranches.size()]);
		int index = random.nextInt(branchids.length);
		BranchID branchId= branchids[index];	
		return branchId;
	}

}
