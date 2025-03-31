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
	
	private int pBallot; // process  ballot
	private int rBallot; // read     ballot
	private int iBallot; // impose   ballot
	private int aBallot; // aborted  ballot
	private int gBallot; // gathered ballot
	private int proposal;
	private int estimate;
	private int ackRcvd;
	
	private DecideMsg decision;
	
	private long startTime;
	
	private boolean debug_mode;
	// Enabling debug mode will output every message received
	// by each process and their corresponding status
	// By default, only Crashes and decisions are displayed

	// Logger attached to actor
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

	public Process(int ID, int N_proc, double failProb) {
		this.pid = ID;
		this.numProc = N_proc;
		this.knownActors = new ArrayList<ActorRef>();
		this.states = new HashMap<>();
		this.prone_flag = false;
		this.crash = false;
		this.hold = false;
		this.decided = false;
		this.failProb = failProb; 
		this.ackRcvd = 0;
		
		this.pBallot = pid - numProc;
		this.rBallot = 0;
		this.iBallot = pid - numProc;
		this.aBallot = 0;
		this.gBallot = 0;
		this.proposal = -1; // Use -1 as bottom value
		this.estimate = -1;
		this.debug_mode = false; // false by default
		this.startTime = 0;
		
		Random random = new Random();
		this.proposal = random.nextInt(2); // Generates 0 or 1
	}
	
	public Process(int ID, int N_proc, double failProb, boolean debug) {
		this(ID, N_proc, failProb);
		this.debug_mode = debug;
	}

	// Static function creating actor
    public static Props createActor(int ID, int nb, double prob) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb, prob);
        });
    }
    
    // Explicitly specify the debug mode from caller
    public static Props createActor(int ID, int nb, double prob, boolean debug) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb, prob, debug);
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
		if(this.debug_mode) {
			//Thread.sleep(300); // To display the messages more slowly and clearly
		}
		if(prone_flag && !crash) {
			if(Math.random() < this.failProb) {
				this.crash = true; 
				log.info(self().path().name() + " has crashed.");
			}
		}
		if(!crash) {  
			// Update actors information
			if(message instanceof ArrayList){
				this.updateKnownActors((ArrayList<ActorRef>) message);
			} 
			// Fail prone setting
			else if (message instanceof CrashMsg) {
				this.prone_flag = true;
			}
			else if (message instanceof HoldMsg) {
				this.hold = true;
				if(this.debug_mode) {
					log.info(self().path().name() + " received  HOLD .");
				}
			}
			// Launch the OF-Consensus
			else if (message instanceof LaunchMsg) {
				this.propose();
			}
			// Take the start time
			else if (message.getClass() == Long.class) {
				this.startTime = (long) message;
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
		if(this.hold) {return;} // do NOT propose in the hold mode

		this.pBallot += numProc;
		this.states.clear();
		
		if(this.debug_mode) {
			log.info(self().path().name() + " proposed "+ proposal + " with ballot " + pBallot + ".");
		}
        
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
			if(this.debug_mode) {
				 //log.info(self().path().name() + "(" + this.rBallot + ", " + this.iBallot + ") read proposal from "+ readSender.path().name() 
						// + " with ballot " + ballot + ". Sending [ABORT]");
				log.info(self().path().name() + " received READ from "+ readSender.path().name() 
						+ " #ballot=" + ballot + ".  Sending [ABORT]" + " (" + this.rBallot + ", " + this.iBallot + ")");
			}
			return;
		}
		else {
			this.rBallot = ballot;
			GatherMsg gatherMsg = new GatherMsg(ballot, iBallot, estimate);
			readSender.tell(gatherMsg, this.getSelf());
			if(this.debug_mode) {
				// log.info(self().path().name() + "(" + this.rBallot + ", " + this.iBallot + ") read proposal from "+ readSender.path().name() 
						// + " with ballot " + ballot + ". Sending [GATHER]");
				log.info(self().path().name() + " received  READ  from "+ readSender.path().name() 
						+ " #ballot=" + ballot + ". Sending [GATHER]" + " (" + this.rBallot + ", " + this.iBallot + ")");
			}
			return;
		}
	}
	
	private void gather(GatherMsg gatherMsg, ActorRef gatherSender) {
		// The same thing as abort may happen
		// Suppose you have received just n/2 GATHERs and begins to impose
		// Later new GATHERs arrive and you 
		int mBallot = gatherMsg.getBallotNum();
		int imp = gatherMsg.getImposeBallotNum();
		int est = gatherMsg.getEstimate();
		
		this.states.put(gatherSender, new int[]{est, imp});
		
		if(this.debug_mode) {
			log.info(self().path().name() + " received GATHER from " + gatherSender.path().name() + ". " + states.size() 
				+ " states now.");
		}
		
		
		int highest = 0;
		if(states.size() * 2 > this.numProc && this.gBallot != mBallot) { // majority && not a ballot that already tried to impose
			// NOTE: exact half is NOT a majority!!
			this.gBallot = mBallot;
			for (Map.Entry<ActorRef, int[]> stateEntry : states.entrySet()) {
	            int estballot = stateEntry.getValue()[1];  // Get the estballot
	            if (estballot > highest) {
	            	highest = estballot;
	                this.proposal = stateEntry.getValue()[0];
	            }
	        }
			this.states.clear();
			if(this.debug_mode) {
				log.info(self().path().name() + " imposed  "+ proposal 
					+ " #ballot=" + pBallot + ".");
			}
			
			ImposeMsg imposeMsg = new ImposeMsg(pBallot,proposal); // use my own ballot to impose
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
			if(this.debug_mode) {
				// log.info(self().path().name() + "(" + this.rBallot + ", " + this.iBallot + ") has read imposeMsg from "+ imposeSender.path().name() 
					// + " with ballot " + ballot + ". [ABORT]");
				log.info(self().path().name() + " received IMPOSE from "+ imposeSender.path().name() 
						+ " #ballot=" + ballot + ". Sending [ABORT]" + " (" + this.rBallot + ", " + this.iBallot + ")"
						+ (this.rBallot > ballot) + (this.iBallot > ballot) + (this.decided));
			}
			return;
		}
		else {
			this.estimate = impValue;
			this.iBallot = ballot;
			AckMsg ackMsg = new AckMsg(ballot);
			imposeSender.tell(ackMsg, this.getSelf());
			if(this.debug_mode) {
				log.info(self().path().name() + " ack'ed   IMPOSE from "+ imposeSender.path().name()+ " #ballot=" + ballot + ".");
			}
			
		}
	}
	
	private void acknowledge() {
		this.ackRcvd += 1;
		if (ackRcvd * 2 > this.numProc && !this.decided) { 
			this.decided = true;
			this.ackRcvd = 0;
			DecideMsg decide = new DecideMsg(this.pBallot, this.proposal); 
			
			long elapsedTime =  System.currentTimeMillis() - startTime;
			for (ActorRef process : this.knownActors) {
				process.tell(decide, this.getSelf());
				// getContext().system().scheduler().scheduleOnce(Duration.ofMillis(1000), 
						// process, decide, getContext().system().dispatcher(), this.getSelf());
			}
			if(this.debug_mode) {
				log.info(self().path().name() + " received  A C K from a quorum. #ballot=" + this.pBallot);
			}
			log.info(self().path().name() + " DECIDED "
					+ " value=" + proposal + " #ballot=" + pBallot + ". Time: " + elapsedTime + ".");
			
		}
	}
	
	private void decide(DecideMsg decide, ActorRef decSender) {
		if(!this.decided) {
			this.decided = true;
			this.decision = decide;
			int value = decide.getProposal();
			long elapsedTime =  System.currentTimeMillis() - startTime;
			for (ActorRef process : this.knownActors) {
				// simply forward the decision message
				if (process != this.getSelf()) {// You don't need to forward to yourself
					process.tell(decide, this.getSelf());
					//getContext().system().scheduler().scheduleOnce(Duration.ofMillis(100), 
							//process, decide, getContext().system().dispatcher(), this.getSelf());
				}
			}
			log.info(self().path().name() + " received DECIDE from " + decSender.path().name() 
					+ " value=" + value + " #ballot=" + decide.getBallotNum() + ". Time: " + elapsedTime + ".");
			debug_mode = false;
		}
		else if (decision != null && decision.getProposal() != decide.getProposal()) { // if different decision received
			// Theoretically, this line will NEVER be executed
			// however, I'm keeping this line for debug usage
			log.error(self().path().name() + ": Multiple Decision Received!");
			// In fact, this may happen if you don't tell others to abort even when you have already received a decision (?)
		}
	}
	
	
	private void abort(AbortMsg abortMsg, ActorRef abortSender) {
		// it may receive several abort message targeted to a single proposal
		// it's crucial to identify or you risk aborting the more recent ones
		int abortTargetBallot = abortMsg.getBallotNum();
		if(abortTargetBallot > this.aBallot && !this.decided) {
			this.aBallot = abortTargetBallot;
			if(this.debug_mode) {
				log.info(self().path().name() + " received ABORT  from " + abortSender.path().name());
			}
			
			// Re-propose immediately could cause contention to others' proposal (?)
			LaunchMsg rePropose = new LaunchMsg();
			getContext().system().scheduler().scheduleOnce(Duration.ofMillis(0), 
					getSelf(), rePropose, getContext().system().dispatcher(), ActorRef.noSender());
		}
		return;
	}

}
