package synod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import synod.CrashMsg;
import synod.LaunchMsg;

import synod.Process;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;



public class faultTolerentSystem {
	
	public static int N_proc = 7; // Number of processes
	public static int f = 3; // Number of possible failure
	public static double failProb = 0.4; // Probability of failure in prone mode
	public static boolean debugMode = true;
	public static int tLe = 3000; // timeout in millisecond
	

	public static void main(String[] args) {

		final ActorSystem system = ActorSystem.create("system");
		
        system.log().info("System started.");

        ArrayList<ActorRef> references = new ArrayList<>();
        ArrayList<ActorRef> normals = new ArrayList<>();

        // Create processes and designate their IDs
        for (int i = 0; i < N_proc; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N_proc, 0.7, debugMode), "p" + (i+1));
            references.add(a);
            normals.add(a);
        }
        
        // Let processes know each other
        for (ActorRef actor : references) {
        	actor.tell(references, ActorRef.noSender());
        }
        
        // Take at most f processes to fail
        Collections.shuffle(references);

        for (int i = 0; i < f; i++) {
            references.get(i).tell(new CrashMsg(), ActorRef.noSender());
            system.log().info("SysInfo: Crash message sent to " + references.get(i).path().name());
            normals.remove(references.get(i));
        }
        
        // Launch the scheme
        for (ActorRef actor : references) {
            actor.tell(new LaunchMsg(), ActorRef.noSender());
        }
        system.log().info("SysInfo: Fully launched.");
        
        
        Collections.shuffle(normals);
        // Take the first one in randomized normal lists as leader
        // Send hold message to everyone else
        if(debugMode) {
        	system.log().info("SysInfo: " + normals.get(0).path().name() + " is the leader.");
        }
        for (int i=1; i < normals.size(); i++) {
        	ActorRef actor = normals.get(i);
        	HoldMsg holdMsg = new HoldMsg();
        	system.scheduler().scheduleOnce(Duration.ofMillis(tLe),
        			actor, holdMsg, system.dispatcher(), null); 
        	// Schedule a hold message after the timeout
        }

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
