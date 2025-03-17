package synod;

public class ImposeMsg implements Message {
	private int ballot;
	private int proposal;
	
	public ImposeMsg(int ballot, int value){
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
