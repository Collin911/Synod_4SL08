package synod;

public class AckMsg implements Message {
	private int ballotNum;
	
	public AckMsg(int ballot){
		this.ballotNum = ballot;
	}
	public int getBallotNum() {
		return this.ballotNum;
	}
}
