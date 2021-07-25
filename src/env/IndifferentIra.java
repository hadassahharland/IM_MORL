package env;

import org.rlcommunity.rlglue.codec.types.Reward;
import java.util.Random;

public class IndifferentIra implements ActorInterface{

    // The actor's attitude can be one of three: -1 is upset, 0 is neutral and 1 is happy.
    public int attitude;
    // The actor's justification can be -1 for neutral state, else corresponds with the offending objective number
    public int justification;
    private Random random;

    // Actor Settable variables
    private final int[] thresholds = {-10000,-10000,-10000,-10000};   // Thresholds reward minimums for actor reactions
    private final double persistance = 0; // chance of being upset about a previous action in the subsequent action sequence (decay factor)

    public void actor_init(){
        attitude = 0;
    }

    public void reaction(Reward var1) { // Defines the reaction to the Reward, sets the Attitude and Justification

    }

    public int be_observed() { // Scrambles and passes Attitude
        // This scrambler sits in place of using a image recognition software, and implements a chance of incorrect
        // identification at Attitude for the Agent at a similar (intended to be representative) proportion
        int scrambledAttitude = attitude;
        if (random.nextDouble() > 0.8){
            scrambledAttitude = random.nextInt(3)-1; // random int 0, 1 or 2, -1 to transform to -1, 0, 1
        }
        return scrambledAttitude;
    }

    public void assess_apology(int apology){ // When an apology is provisioned, determine whether it aligns with justification and update attitude
        if (apology == justification){  // If the agent has apologised successfully
            attitude = 1;  // Update the actor's attitude to "happy"
        }
        // If apology is wrong, do nothing.
    }

    public String actor_message(String var1){
        return "The actor's current attitude is " + attitude + " and the justification is " + justification;
    }
}

