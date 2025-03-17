package synod;

public class AbortMsg implements Message {
	private int ballotNum;
	
	public AbortMsg(int ballot){
		this.ballotNum = ballot;
	}
	public int getBallotNum() {
		return this.ballotNum;
	}
}
