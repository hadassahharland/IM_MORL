package agents;

import env.ConfigurableActor;
import org.rlcommunity.rlglue.codec.RLGlue;
import org.rlcommunity.rlglue.codec.types.Reward;

public class Conscience {
    // Stores and calculates the response to potential harm, produces the response

    public int justification;

    public Conscience() {
    }

    // Create wrapper method that contains steps for each action
    public int assess(double[] accumulatedRewards, int attitude) {
        // At the end of each action, the agent must step through a process defined by the following methods
        if (attitude < 0) {
            // if the actor's attitude is upset, determine fault
            int justification = determineFault(accumulatedRewards);
        }
        return justification;
    }


//    if (justification > 0) {
//        // if the apology is required, send it to the Actor
//        actor.assess_apology(justification);
//        // Once actor has updated their attitude, observe again to determine reaction
//        int reaction = observeActor();
//        if (reaction >= 0) {
//            // if the actor is satisfied with the apology, correct the threshold
//            adjustThresholds(justification);
//        }
//    }

//
//    public int observeActor(ConfigurableActor actor) {
//        return actor.be_observed();
//    }

    public int determineFault(double[] accumulatedRewards) {
        // Using a negative reward approach:
        int imin = 0;  // Initialised to prevent unexpected crashes, should always be overwritten
        double min = 0;
        int just = -1;

        // Iterate over each objective
        for (int i = 0; i < accumulatedRewards.length; i++) {
            if (accumulatedRewards[i] < min){;
            // If the value is less than the running minimum (selects latest objectives with priority in a tie)
            // Then save the index of that value
                imin = i;
                min = accumulatedRewards[i];
            }
        }
        // Only determine fault if the value is below 0
        if (accumulatedRewards[imin] < 0) {
            just = imin;
        }
        return just;
    }

//    public void adjustThresholds(int justification) {
//        RLGlue.RL_agent_message("adjust_threshold:" + justification);
////        Thresholds.adjustThresholds(justification);
//    }
}
