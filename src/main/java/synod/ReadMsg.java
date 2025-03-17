package synod;

public class ReadMsg implements Message {
	private int ballotNum;
	
	public ReadMsg(int ballot){
		this.ballotNum = ballot;
	}
	public int getBallotNum() {
		return this.ballotNum;
	}
}
