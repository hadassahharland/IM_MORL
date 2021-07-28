package agents;

import env.ConfigurableActor;
import org.rlcommunity.rlglue.codec.types.Reward;

public class Conscience {
    // Stores

    public Conscience() {
    }

    // Create wrapper method that contains steps for each action
    public int OnNextAction(Reward r, ConfigurableActor actor, Thresholds thresholds) {
        // At the end of each action, the agent must step through a process defined by the following methods
        int attitude = ObserveActor(actor);
        if (attitude < 0) {
            // if the actor's attitude is upset, determine fault
            int justification = DetermineFault(r);
            if (justification > 0) {
                // if the apology is required, send it to the Actor
                actor.assess_apology(justification);
                // Once actor has updated their attitude, observe again to determine reaction
                int reaction = ObserveActor(actor);
                if (reaction >= 0) {
                    // if the actor is satisfied with the apology, correct the threshold
                    AdjustThresholds(thresholds, justification);
                }
            }
        }
    }

    public int ObserveActor(ConfigurableActor actor) {
        return actor.be_observed();
    }

    public int DetermineFault(Reward r) {
        // Using a negative reward approach:
        int imin = 0;  // Initialised to prevent unexpected crashes, should always be overwritten
        double min = 0;
        int just = -1;

        // Iterate over each objective and subtract the threshold
        for (int i = 0; i < r.doubleArray.length; i++) {
            if (r.doubleArray[i] < min){;
            // If the difference is less than the running minimum (selects latest objectives with priority in a tie)
            // Then save the index of that value
                imin = i;
                min = r.doubleArray[i];
            }
        }
        // Only determine fault if the value is below 0
        if (r.doubleArray[imin] < 0) {
            just = imin;
        }
        return just;
    }

    public void AdjustThresholds(Thresholds thresholds, int justification) {
        thresholds.adjustDueToFault(justification);
    }
}