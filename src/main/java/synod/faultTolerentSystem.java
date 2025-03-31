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
	
	public static int N_proc = 10; // Number of processes
	public static int f = 4; // Number of possible failure
	public static double failProb = 1; // Probability of failure in prone mode
	public static boolean debugMode = false;
	public static int tLe = 2000; // leader timeout in millisecond
	

	public static void main(String[] args) {

		
		final ActorSystem system = ActorSystem.create("system");
		
        system.log().info("System started.");

        ArrayList<ActorRef> references = new ArrayList<>();
        ArrayList<ActorRef> normals = new ArrayList<>();

        // Create processes and designate their IDs
        for (int i = 0; i < N_proc; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N_proc,failProb, debugMode), "p" + (i+1));
            references.add(a);
            normals.add(a);
        }
        
        // Let processes know each other
        long startTime = System.currentTimeMillis();
        for (ActorRef actor : references) {
        	actor.tell(references, ActorRef.noSender());
        	actor.tell(startTime, ActorRef.noSender());
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
        // system.log().info("SysInfo: " + normals.get(0).path().name() + " is the leader.");
        system.scheduler().scheduleOnce(Duration.ofMillis(tLe), // Delay
                () -> system.log().info("SysInfo: " + normals.get(0).path().name() + " is the leader."),
                system.dispatcher()
            );
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
		Thread.sleep(600000);
	}
	
}
