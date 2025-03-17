package synod;

public class DecideMsg implements Message {
	private int proposal;
	
	public DecideMsg(int value){
		this.proposal = value;
	}
	public int getProposal() {
		return this.proposal;
	}
}
