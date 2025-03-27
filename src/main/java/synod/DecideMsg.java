package synod;

public class DecideMsg implements Message {
	private int ballot;
	private int proposal;
	
	public DecideMsg(int ballot, int value){
		this.ballot = ballot;
		this.proposal = value;
	}
	
	public int getBallotNum() {
		return this.ballot;
	}
	
	public int getProposal() {
		return this.proposal;
	}
}

