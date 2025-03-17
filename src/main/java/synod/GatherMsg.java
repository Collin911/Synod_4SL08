package synod;

public class GatherMsg implements Message {
	private int pBallot;
	private int iBallot;
	private int estimate;
	
	public GatherMsg(int ballot, int impose, int est){
		this.pBallot = ballot;
		this.iBallot = impose;
		this.estimate = est;
	}
	
	public int getBallotNum() {
		return this.pBallot;
	}
	
	public int getImposeBallotNum() {
		return this.iBallot;
	}
	
	public int getEstimate() {
		return this.estimate;
	}
}
