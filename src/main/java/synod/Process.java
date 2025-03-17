package synod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Process extends UntypedAbstractActor{
	
	private int pid;
	private int numProc;
	private ArrayList<ActorRef> knownActors;
	private Map<ActorRef, int[]> states;
	private boolean prone_flag;
	private boolean crash;  // This flag serves as indicator for crash
	private boolean hold;    // This flag indicates if the process is on hold
	private boolean decided; // This flag indicates whether a decision is made
	private double failProb;
	
	private int pBallot; // process ballot
	private int rBallot; // read    ballot
	private int iBallot; // impose  ballot
	private int aBallot; // aborted ballot
	private int proposal;
	private int estimate;
	private int ackRcvd;

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

	public Process(int ID, int N_proc) {
		this.pid = ID;
		this.numProc = N_proc;
		this.knownActors = new ArrayList<ActorRef>();
		this.states = new HashMap<>();
		this.prone_flag = false;
		this.crash = false;
		this.hold = false;
		this.decided = false;
		this.failProb = 0.5; 
		
		this.pBallot = pid - numProc;
		this.rBallot = 0;
		this.iBallot = pid - numProc;
		this.aBallot = 0;
		this.proposal = -1; // Use -1 as bottom value
		this.estimate = -1;
	}

	// Static function creating actor
    public static Props createActor(int ID, int nb) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb);
        });
    }
	
	public String printActorList(ArrayList<ActorRef> list) {
		String returnVal = "";
		for(ActorRef actor : list) {
			returnVal = returnVal + actor.path().name();
		}				
		return returnVal;
	}
	
	private ArrayList<ActorRef> updateKnownActors(ArrayList<ActorRef> list){
		for(ActorRef actor : list) {
			if(!this.knownActors.contains(actor)) {
				this.knownActors.add(actor);
			}
		}
		return this.knownActors;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void onReceive(Object message) throws Throwable {
		Thread.sleep(300);
		if(prone_flag && !crash) {
			if(Math.random() < this.failProb) {
				this.crash = true; 
				log.debug("p" + self().path().name() + " has crashed.");
			}
		}
		if(!crash && ! decided) {  
			// Update actors information
			if(message instanceof ArrayList){
				this.updateKnownActors((ArrayList<ActorRef>) message);
			} 
			// Fail prone setting
			else if (message instanceof CrashMsg) {
				this.prone_flag = true;
			}
			// Launch the OF-Consensus
			else if (message instanceof LaunchMsg) {
				this.propose();
			}
			// Receive READ
			else if (message instanceof ReadMsg) {
				this.read((ReadMsg)message, getSender());
			}
			// Receive ABORT
			else if (message instanceof AbortMsg) {
				this.abort((AbortMsg) message, getSender());
			}
			// Receive GATHER
			else if (message instanceof GatherMsg) {
				this.gather((GatherMsg) message, getSender());
			}
			// Receive IMPOSE
			else if (message instanceof ImposeMsg) {
				this.impose((ImposeMsg) message, getSender());
			}
			// Receive ACK
			else if (message instanceof AckMsg) {
				this.acknowledge();
			}
			// Receive DECIDE
			else if (message instanceof DecideMsg) {
				this.decide((DecideMsg) message, getSender());
			}
		}
		
	}
	
	private void propose() {
		
		Random random = new Random();
		this.proposal = random.nextInt(2); // Generates 0 or 1
		
		this.ackRcvd = 0;
		this.pBallot += numProc;
		this.states.clear();
		
        log.info("p" + self().path().name() + " proposed "+ proposal + " with ballot " + pBallot + ".");
        for (ActorRef actor : this.knownActors) {
            actor.tell(new ReadMsg(pBallot), this.getSelf());
        }
		return;
	}
	
	private void read(ReadMsg readMsg, ActorRef readSender) {
		int ballot = readMsg.getBallotNum();
		if (this.rBallot > ballot || this.iBallot > ballot) {
			AbortMsg abortMsg = new AbortMsg(ballot);
			readSender.tell(abortMsg, this.getSelf());
			log.info("p" + self().path().name() + "(" + this.rBallot + ", " + this.iBallot + ") read proposal from p"+ readSender.path().name() 
					+ " with ballot " + ballot + ". [ABORT]");
			return;
		}
		else {
			this.rBallot = ballot;
			GatherMsg gatherMsg = new GatherMsg(ballot, iBallot, estimate);
			readSender.tell(gatherMsg, this.getSelf());
			log.info("p" + self().path().name() + "(" + this.rBallot + ", " + this.iBallot + ") read proposal from p"+ readSender.path().name() 
					+ " with ballot " + ballot + ". [GATHER]");
			return;
		}
	}
	
	private void abort(AbortMsg abortMsg, ActorRef abortSender) {
		// it may receive several abort message targeted to a single proposal
		// it's crucial to identify or you risk aborting the more recent ones
		int abortTargetBallot = abortMsg.getBallotNum();
		if(abortTargetBallot != this.aBallot && !this.hold) {
			this.aBallot = abortTargetBallot;
			log.info("p" + self().path().name() + " has aborted due to p" + abortSender.path().name());
			// Re-propose immediately could cause contention to others' proposal
			LaunchMsg rePropose = new LaunchMsg();
			getContext().system().scheduler().scheduleOnce(Duration.ofMillis(50), 
					getSelf(), rePropose, getContext().system().dispatcher(), ActorRef.noSender());

			
		}
		return;
	}
	
	private void gather(GatherMsg gatherMsg, ActorRef gatherSender) {
		int mBallot = gatherMsg.getBallotNum();
		int est = gatherMsg.getEstimate();
		int imp = gatherMsg.getImposeBallotNum();
		this.states.put(gatherSender, new int[]{est, imp});
		log.info("p" + self().path().name() + " has "+ states.size() 
				+ " states now.");
		
		int highest = 0;
		if(states.size() * 2 >= this.numProc) { // if more than half
			for (Map.Entry<ActorRef, int[]> stateEntry : states.entrySet()) {
	            int estballot = stateEntry.getValue()[1];  // Get the estballot
	            if (estballot > highest) {
	            	highest = estballot;
	                this.proposal = stateEntry.getValue()[0];
	            }
	        }
			this.states.clear();
			log.info("p" + self().path().name() + " is trying to impose the proposal "+ proposal 
					+ " with ballot number " + mBallot + ".");
			ImposeMsg imposeMsg = new ImposeMsg(mBallot, this.proposal);
			for (ActorRef process : this.knownActors) {
				process.tell(imposeMsg, this.getSelf());
			}
		}
	}
	
	private void impose(ImposeMsg imposeMsg, ActorRef imposeSender) {
		int ballot = imposeMsg.getBallotNum();
		int impValue = imposeMsg.getProposal(); 
		if (this.rBallot > ballot || this.iBallot > ballot) {
			AbortMsg abortMsg = new AbortMsg(ballot);
			imposeSender.tell(abortMsg, this.getSelf());
			log.info("p" + self().path().name() + "(" + this.rBallot + ", " + this.iBallot + ") has read imposeMsg from p"+ imposeSender.path().name() 
					+ " with ballot " + ballot + ". [ABORT]");
			return;
		}
		else {
			this.estimate = impValue;
			this.iBallot = ballot;
			AckMsg ackMsg = new AckMsg(ballot);
			imposeSender.tell(ackMsg, this.getSelf());
			log.info("p" + self().path().name() + " has ACKed imposeMsg from p"+ imposeSender.path().name()+ ".");
		}
	}
	
	private void acknowledge() {
		this.ackRcvd += 1;
		if (ackRcvd * 2 >= this.numProc && !this.decided) {
			this.decided = true;
			DecideMsg decide = new DecideMsg(this.proposal);
			log.info("p" + self().path().name() + " has DECIDED the value "+ this.proposal 
					+ " with ballot number " + this.pBallot + ".");
			for (ActorRef process : this.knownActors) {
				if (process != this.getSelf()) { // You don't need to tell yourself the decision
					process.tell(decide, this.getSelf());
				}
			}
		}
	}
	
	private void decide(DecideMsg decide, ActorRef decSender) {
		if(!this.decided) {
			this.decided = true;
			int value = decide.getProposal();
			log.info("p" + self().path().name() + " has received decision from p" + decSender.path().name() + " with value=" + value + ".");
			for (ActorRef process : this.knownActors) {
				if (process != this.getSelf()) { 
					process.tell(decide, this.getSelf());
				}
			}
		}
	}

}
