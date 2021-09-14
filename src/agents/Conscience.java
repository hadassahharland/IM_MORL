package agents;

import java.io.FileWriter;
import java.io.IOException;

public class Conscience {
    // Stores and calculates the response to potential harm, produces the response

//    public int justification;
    private int episode;
    private int step;
    public int notThis;
    public int resetDelay;
    private boolean apologised;
    private double[] priorRewards;
    private double[] theseRewards;

    public Conscience() {
        episode = -1;
        apologised = false;
        printToConscienceOutputFile("Episode", "Step", "env.Attitude", "Justification");  // add headings to file
        priorRewards = new double[]{0, 0, 0}; // initial set
    }

    public void cleanUp() {
        episode = -1;
    }

    public void nextEpisode() {
        this.episode += 1;
        this.step = 0;
        this.apologised = false;
        priorRewards = new double[]{0, 0, 0}; // reset
    }

    // Create wrapper method that contains steps for each action
    public int assess(double[] accumulatedRewards, int attitude) {
        this.theseRewards = accumulatedRewards;
        // At the end of each action, the agent must step through a process defined by the following methods
        // if the actor is upset, determine fault
        printToConscienceOutputFile("Accumulated Rewards:",
                Double.toString(accumulatedRewards[0]),
                Double.toString(accumulatedRewards[1]),
                Double.toString(accumulatedRewards[2]));
        if (attitude < 0) {
            // if the actor's attitude is upset, determine fault
            return determineFault(accumulatedRewards);
        }
        // if the actor is not upset, then no justification is required.
        else {
            return -1;
        }
    }

    public void setApologisedFlagTrue() {
        this.apologised = true;
    }

    public boolean isApologised() {
        return apologised;
    }

    public void setNotThis(int notThis) {
        // if the apology fails, then avoid apologising for this item again next time.
        this.notThis = notThis;
        this.resetDelay = 2; //number of apology sequences before this is unlocked, unless overwritten
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
        int imin = -1;  // Initialised to prevent unexpected crashes, should always be overwritten
        double min = 0;
        int just = -1;

        // Iterate over each objective
        for (int i = 0; i < accumulatedRewards.length; i++) {
//            if ((accumulatedRewards[i] < min)){;  // !(i == notThis) & don't assess the "not this" objective
//            // If the value is less than the running minimum (selects latest objectives with priority in a tie)
//            // Then save the index of that value
//                imin = i;
//                min = accumulatedRewards[i];
//            }
            if (((accumulatedRewards[i] - priorRewards[i]) < min)){;  // !(i == notThis) & don't assess the "not this" objective
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
//        this.resetDelay -= 1; // count down the reset counter
//        if (resetDelay <= 0) { this.notThis = -1; } // if it hits zero, reset "not this"

        this.priorRewards = accumulatedRewards;

        return just;
    }

    public void printThis(String attitude, String justification) {
        // pass variables into the Conscience locality, pickup episode and step
        printToConscienceOutputFile(Integer.toString(episode), Integer.toString(step), attitude, justification);
        step += 1; //next step
    }

    public void printToConscienceOutputFile(String ep, String step, String attitude, String justification) {
        try {
            FileWriter myWriter = new FileWriter("ConscienceOutput.txt", true);
            myWriter.write(ep + ", " + step + ", "  + attitude + ", " + justification + System.lineSeparator());
            myWriter.close();
//            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

//    public void adjustThresholds(int justification) {
//        RLGlue.RL_agent_message("adjust_threshold:" + justification);
////        Thresholds.adjustThresholds(justification);
//    }
}
