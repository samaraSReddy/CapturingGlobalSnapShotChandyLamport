import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.thrift.TException;


public class BranchRecieveHandler implements Branch.Iface {


	private volatile Integer branchBalance;
	private static Integer initialBalance; // storing initial balance to calculate the transfer amount
	private static BranchID branchID;
	private static BranchID[] otherBranchIDS;
	private boolean transferFlag;
	private boolean markerflag;
	private Timer timer = new Timer();
	private ConcurrentHashMap<String,Integer> outgoingMessageMap = new ConcurrentHashMap<String, Integer>();
	private ConcurrentHashMap<String,Integer> incomingMessageMap = new ConcurrentHashMap<String, Integer>();
	private int markersReceived = 0;
	private volatile int markersSent = 0;
	private ConcurrentHashMap<String, Boolean> markerSentmap = new ConcurrentHashMap<String, Boolean>();
	private ConcurrentHashMap<String, Boolean> markerRecievedmap = new ConcurrentHashMap<String, Boolean>();
	private ConcurrentHashMap<Integer, LocalSnapshot> localsnapshotMap = new ConcurrentHashMap<Integer, LocalSnapshot>();
	private ConcurrentHashMap<String, Boolean> recordingMap = new ConcurrentHashMap<String, Boolean>();
	private List<Integer> channelStates = new ArrayList<Integer>();
	private ConcurrentHashMap<String, List<Integer>>  channelStatesMap = new ConcurrentHashMap<String, List<Integer>>();


	public BranchRecieveHandler(BranchID branch){
		branchID=branch;
	}

	@Override
	public void initBranch(int balance, List<BranchID> all_branches) throws SystemException, TException {
		try {	

			setOtherBranchList(all_branches);
			branchBalance=balance;
			initialBalance=balance; // storing initial balance to calculate transfer amount
			System.out.println("Initialized succesfully :"+ branchID.name);
			new Thread(new Runnable() {

				@Override
				public void run() {
					transferMoneytoOtherBranches();
				}
			}).start();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}



	private  synchronized Integer getTransferAmount() {
		Integer transnsferAmount =0;
		transnsferAmount = 0;	
		int low =1;
		int high =5;

		Random random = new Random();
		int i=random.nextInt(high-low)+low;		
		transnsferAmount = (Integer)(this.initialBalance*i)/100;
		if((getCurrentBalance()==0) || getCurrentBalance()<transnsferAmount){
			transferFlag=false;
			return 0; 
		}else{
			transferFlag=true;
			return transnsferAmount;
		}


	}
	private synchronized Integer getCurrentBalance(){
		return this.branchBalance;
	}

	@Override	
	public void transferMoney(TransferMessage message, int messageId) throws SystemException, TException {
		if(message.getOrig_branchId()!=null&& message.getOrig_branchId().getName()!=null ){
			String incomingbranch =message.getOrig_branchId().getName();
			List<Integer> interBranchChannelState=channelStatesMap.get(incomingbranch);

			while(!checkCondition(incomingbranch, messageId)){
				// parking the request 
			}
			branchBalance=branchBalance+message.amount; //adding amount to branch balance 
			incomingMessageMap.put(incomingbranch,messageId);
			if(recordingMap.get(incomingbranch) && !markerRecievedmap.get(incomingbranch)){ // recording channel state
				interBranchChannelState.add(message.getAmount());
				channelStatesMap.put(incomingbranch, interBranchChannelState);
			}


		}
	}
	public synchronized void setRecording(String branch, boolean value){
		recordingMap.put(branch, value);
	}


	@Override
	public void initSnapshot(int snapshotId) throws SystemException, TException {
		try {
			markersReceived=0;
			markersSent=0;
			clearAllstates();
			channelStates.clear();
			LocalSnapshot localSnapshot = new LocalSnapshot(snapshotId,getCurrentBalance(),new ArrayList<Integer>());
			localsnapshotMap.put(snapshotId, localSnapshot);
			sendingMarkers(true);// switching on marker flag
			if(otherBranchIDS!=null && otherBranchIDS.length>0){
				for(BranchID branch : otherBranchIDS){
					BranchClientHandler branchClientHandler =  new BranchClientHandler();
					Branch.Client client = branchClientHandler.fetchClient(branch);
					client.Marker (BranchRecieveHandler.branchID, snapshotId,generateNextMessageId(branch));
					if(!recordingMap.get(branch.getName())&& !markerRecievedmap.get(branch.getName())){
						setRecording(branch.getName(),true);
					}
				}
			}

		} catch (Exception e) {
			System.out.println(e.toString());		
		}
		finally{
			sendingMarkers(false);
		}
	}	

	private void clearAllstates() {
		for(Map.Entry<String,List<Integer>> entry: channelStatesMap.entrySet()){	
			List<Integer> list=entry.getValue();
			list.clear();
		}if(null != otherBranchIDS && otherBranchIDS.length>0){
			for(BranchID branch: otherBranchIDS){
				recordingMap.put(branch.name,false);
				markerRecievedmap.put(branch.name,false);
				markerSentmap.put(branch.name,false);
			}		
		}
	}


	@Override
	public void Marker(BranchID branchId, int snapshotId, int messageId) throws SystemException, TException {
		try {
			while(!checkCondition(branchId.name, messageId)){//FIFO holding the request to meet the condition

			}
			incrementMarkersReceived();
			markerRecievedmap.put(branchId.name, true);
			setRecording(branchId.name,false);
			incomingMessageMap.put(branchId.name,messageId);

			if(!localsnapshotMap.containsKey(snapshotId)){ // If first Marker record the local balance
				try {
					sendingMarkers(true);
					clearAllstates();
					markersSent=0;
					LocalSnapshot localSnapshot = new LocalSnapshot(snapshotId,getCurrentBalance(),new ArrayList<Integer>() );
					localsnapshotMap.put(snapshotId, localSnapshot);

					for(BranchID branch : otherBranchIDS){
						if(markerSentmap!=null&& markerSentmap.get(branch.name)!=null && !markerSentmap.get(branch.name) ){
							BranchClientHandler clientHandler= new BranchClientHandler();
							Branch.Client client = clientHandler.fetchClient(branch);
							client.Marker(branchID, snapshotId,generateNextMessageId(branch));
							if(!this.recordingMap.get(branch.name) && !branchId.getName().equals(branch.name) && !markerRecievedmap.get(branchId.name)){
								setRecording(branch.name,true);
							}
							markerSentmap.put(branch.getName(), true);
							incrementMarkersSent();
						}
					}
					sendingMarkers(false);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				finally{
					sendingMarkers(false);
				}
			}
		} catch (Exception e) {
			System.out.println(e.toString());
		}
	}

	private LocalSnapshot writeChannelStatestoSnapshot(LocalSnapshot localSnapshot) {
		List<Integer> channelstates= new ArrayList<Integer>();
		if(localSnapshot!=null){
			for(BranchID branch : otherBranchIDS ){
				if(channelStatesMap!=null && channelStatesMap.size()>0){
					List<Integer> list =channelStatesMap.get(branch.name);
					int totalamountTransferred=0;
					if(list!=null && list.size()>0){
						for(Integer amount : list){
							totalamountTransferred=totalamountTransferred+amount;
						}
					}
					channelstates.add(totalamountTransferred);
				}

			}
			localSnapshot.setMessages(channelstates);
		}
		return localSnapshot;
	}


	@Override
	public LocalSnapshot retrieveSnapshot(int snapshotId) throws SystemException, TException {
		LocalSnapshot localSnapshot=localsnapshotMap.get(snapshotId);

		if(otherBranchIDS!=null && otherBranchIDS.length>0 && this.markersReceived == otherBranchIDS.length){
			localSnapshot=	writeChannelStatestoSnapshot(localSnapshot);
		}
		markersReceived=0;
		markersSent=0;
		if(otherBranchIDS!=null && otherBranchIDS.length>0 && !markerflag){
			for(BranchID branchID : otherBranchIDS){
				if(recordingMap.get(branchID)!=null){
					recordingMap.put(branchID.name,false);
				}
				markerSentmap.put(branchID.getName(), false);
				markerRecievedmap.put(branchID.getName(), false);
				channelStatesMap.put(branchID.name, new ArrayList<Integer>());
			}
		}else{

		}
		return localSnapshot;
	}


	public synchronized  void setBalance(Integer amount){
		this.branchBalance=branchBalance+amount;
		if(branchBalance>0 && transferFlag==false){
			transferFlag=true;			
		}
	}

	public  void setOtherBranchList(List<BranchID> list){
		List<BranchID> otherBranches=new ArrayList<BranchID>();
		for(BranchID branch:list){
			channelStatesMap.put(branch.getName(), new ArrayList<Integer>());

			if(!branch.getName().equalsIgnoreCase(branchID.name)){
				otherBranches.add(branch);	
				incomingMessageMap.put(branch.name, 0);
				markerSentmap.put(branch.getName(), false);
				markerRecievedmap.put(branch.getName(), false);
				recordingMap.put(branch.getName(), false);
				channelStatesMap.put(branch.getName(), new ArrayList<Integer>());
				outgoingMessageMap.put(branch.getName(), 0);
			}
			if(otherBranches!=null && otherBranches.size()>0){
				otherBranchIDS= (BranchID[]) otherBranches.toArray(new BranchID[otherBranches.size()] );
			}

		}
	}

	private void transferMoneytoOtherBranches()  {
		new MoneyTransferScheduler(0).run();
	}


	class MoneyTransferScheduler extends TimerTask{
		MoneyTransferScheduler(int c){
			count=c;
		}
		int count=0;
		@Override
		public void run()
		{

			if(count==0){
				try {
					Thread.sleep(5000); // initial delay to allow other branches to initialize
					count++;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
			BranchClientHandler clientHandler= new BranchClientHandler(); 
			Integer amount =getTransferAmount();
			if(amount>0 && transferFlag && null !=otherBranchIDS && otherBranchIDS.length>0  && !markerflag){
				setBalance(-amount);
				BranchID outgoingbranchid=selectRandomBranch();
				int messageID= generateNextMessageId(outgoingbranchid);
				clientHandler.transferMoney(branchID,outgoingbranchid,amount,messageID);
				timer.schedule(new MoneyTransferScheduler(count), generateRandomforschedule()*1000);
			}else{
				timer.schedule(new MoneyTransferScheduler(count), (generateRandomforschedule()*1000));
			}
		}
	}
	
	
	private int generateRandomforschedule(){
		Random random= new Random();
		int i=random.nextInt(6);
		return i;

	}

	private synchronized int generateNextMessageId(BranchID branch) {
		int nextmessageId=0;
		if(outgoingMessageMap!=null && outgoingMessageMap.size()>0 && outgoingMessageMap.get(branch.getName())!=null){
			nextmessageId=outgoingMessageMap.get(branch.getName())+1;
			outgoingMessageMap.put(branch.getName(), nextmessageId);
		}
		return nextmessageId;
	}

	private synchronized BranchID selectRandomBranch() {
		Random random = new Random();
		int index = random.nextInt(otherBranchIDS.length);
		BranchID branchId= otherBranchIDS[index];	
		return branchId;
	}

	private synchronized void incrementMarkersReceived(){
		this.markersReceived=markersReceived+1;
	}

	private synchronized void incrementMarkersSent(){
		this.markersSent=markersSent+1;
	}

	private synchronized boolean checkCondition(String branch , Integer messageId){
		if((incomingMessageMap.get(branch)+1)==(messageId)){
			return true;
		}else{
			return false ;
		}
	}

	private synchronized void sendingMarkers(boolean value){
		this.markerflag= value;
	}

}
