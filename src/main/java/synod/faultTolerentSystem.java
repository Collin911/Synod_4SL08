package synod;

import java.util.ArrayList;
import java.util.Collections;

import synod.CrashMsg;
import synod.LaunchMsg;

import synod.Process;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;



public class faultTolerentSystem {
	
	public static int N_proc = 7; // Number of processes
	public static int f = 3; //Number of possible failure
	

	public static void main(String[] args) {

		final ActorSystem system = ActorSystem.create("system");
		
        system.log().info("System started.");

        ArrayList<ActorRef> references = new ArrayList<>();

        // Create processes and designate their IDs
        for (int i = 0; i < N_proc; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N_proc), "" + i);
            references.add(a);
        }
        
        // Let processes know each other
        for (ActorRef actor : references) {
        	actor.tell(references, ActorRef.noSender());
        }
        
        // Take at most f processes to fail
        Collections.shuffle(references);

        for (int i = 0; i < f; i++) {
            references.get(i).tell(new CrashMsg(), ActorRef.noSender());
            system.log().info("Crash message sent to p" + i);
        }
        
        // Launch the scheme
        for (ActorRef actor : references) {
            actor.tell(new LaunchMsg(), ActorRef.noSender());
        }
        system.log().info("System fully launched.");

	    try {
			waitBeforeTerminate();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		} finally {
			system.terminate();
		}
	}

	public static void waitBeforeTerminate() throws InterruptedException {
		Thread.sleep(50000);
	}
	

}
