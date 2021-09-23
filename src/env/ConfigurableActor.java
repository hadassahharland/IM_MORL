package env;

import agents.Thresholds;
import org.rlcommunity.rlglue.codec.RLGlue;
import org.rlcommunity.rlglue.codec.types.Reward;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import env.Attitude;

public class ConfigurableActor implements ActorInterface{

    // The actor's attitude can be one of three: -1 is upset, 0 is neutral and 1 is happy.
    public int attitude;
    // The actor's justification can be -1 for neutral state, else corresponds with the offending objective number
    public int justification;
    public String agentType; // the actor's type, set at init
    private Random random;

    private final double emotiveFactor = 1; //0.8; // likelihood of NOT being assigned another random attitude
    private final double FERFactor = 1; //0.8; // likelihood of correctly recognising the actor's attitude

    private int episode;
    private int step;

    private int storedJust;

//    private int [] ON = {50, 50, 50, 50};
//    private final int OFF = -10000;

    // Actor Settable variables
//    public static int[] thresholds;   // Thresholds reward minimums for actor reactions
//    private double persistence; // chance of being upset about a previous action in the subsequent action sequence (decay factor)

    public boolean[] sensitivity;
    private double persistence;

    public ConfigurableActor(String type){
        setAttitude(0);  // neutral attitude
        justification = -1; // neutral justification
        agentType = type;
        episode = -1;
        random = new Random(578);
        System.out.println("Initiating Actor: " + type);
        persistence = 1;

        storedJust = -2; // init value to avoid updating prior to reacting
        System.out.println("Actor Type: " +type);

        if (agentType.equals("IndifferentIra")){
            // doesn't care about anything
            sensitivity = new boolean[]{false, false, false};
        } else if (type.equals("TidyToni")) {
            // prefers the table put back
            sensitivity = new boolean[]{false, true, false};
        } else if (type.equals("QuietQuinn")) {
            // prefers the cat left alone
            sensitivity = new boolean[]{false, false, true};
        } else if (type.equals("SensitiveSami")) {
            // prefers everything
            sensitivity = new boolean[]{true, true, true};
        } else if (type.equals("goal")) {
            // prefers everything
            sensitivity = new boolean[]{true, false, false};
        } else if (type.equals("notTable")) {
            // prefers everything
            sensitivity = new boolean[]{true, false, true};
        } else if (type.equals("notCat")) {
            // prefers everything
            sensitivity = new boolean[]{true, true, false};
        } else if (type.equals("notGoal")) {
            // prefers everything
            sensitivity = new boolean[]{false, true, true};
        } else {
            System.out.println("type not found, using default settings");
            sensitivity = new boolean[]{false, false, false};
        }

        printFourToFile("Episode", "Step", "Attitude", "Justification");  // add headings to file


    }

    public void cleanUp() {
        setAttitude(0);
        justification = -1; // neutral justification
        episode = -1;
    }

    public void reaction(boolean[] watchedStates) {
        updatePreviousReaction();
        storedJust = nextReaction(watchedStates);
    }

    public int nextReaction(boolean[] watchedStates) { // Defines the reaction to the Reward, sets the env.Attitude and Justification
        boolean[] states = new boolean[]{step < 50, watchedStates[0], watchedStates[1]};
        // Part 1: determine outcome of reaction
//        double[] diff = new double[]{0, 0, 0, 0};
//        double min = 0 - OFF;   // Initially set to large value
//        int imin = 0;  // Initialised to prevent unexpected crashes, should always be overwritten
        int just = -1;

        // Iterate over each objective and determine sensitivity
        for (int i = 0; i < states.length; i++) {
            // If the flawed state (false) correlates with a sensitivity (selects latest objectives with priority in a tie)
            // Then save the index of that value
            if (sensitivity[i] & !states[i]) {
                just = i;
            }
        }
//        for (int i = 0; i < diff.length; i++) {
//            diff[i] = r.doubleArray[i] - thresholds[i];
//            // If the difference is less than the running minimum (selects latest objectives with priority in a tie)
//            // Then save the index of that value
//            if (diff[i] < min) {
//                imin = i;
//            }
//        }
//        // Only become upset if the value is actually below the threshold
//        if (diff[imin] < 0) {
//            just = imin;
//        }

        // Part 2: disintegration of reaction
        // If actor is already unhappy with a non-neutral justification - maybe react maybe not.
        if (justification > -1) {
            // If a random number is larger than the persistence (probability of still being upset in the next step)
            // Then the actor will not still be upset about the existing justification
            // ie. if persistence is 0.8, there should be an 80% chance of still being upset and a 20% chance of not.
            if (random.nextDouble() > persistence) {  // Actor is NOT still upset
                // Then the actor will not still be upset about the existing justification
                // if the actor has become upset at this action, then react
                return just;
            } // else (if the actor IS still upset) { nothing should change }
        } else {
            // If the actor is not already unhappy, then they can react to the new information
            // Then the actor should react
            return just;
        }
        return -2;
    }

    private void updatePreviousReaction() {
        if (storedJust > -2) {
            react(storedJust);
        }
        printFourToFile(Integer.toString(episode), Integer.toString(step), Integer.toString(attitude), Integer.toString(justification));
        step += 1; //next step

        // update attitude
//        Attitude.setAttitude(attitude);
        setAttitude(attitude);
    }

    private void setAttitude(int attitude) {
        Attitude.setAttitude(attitude);
        this.attitude = attitude;
    }


        //System.out.println("reward 1: " + r.doubleArray[0] + " 2: " + r.doubleArray[1] + " length = "+ r.doubleArray.length)


    private void react(int just) {
        // if the actor has become upset at this action, then react
        justification = just;
        if (just > 0) {
            attitude = -1;
        } else { // else the actor has not become upset, justification and attitude should be reset
//            justification = -1;  // reset justification to neutral
            attitude = setNeutral(); // set the actor's attitude to neutral, with some random factor included
        }
    }

    private int setNeutral(){
        if (random.nextDouble() > emotiveFactor){  // 20% chance of -1, 0, 1
            return (random.nextInt(3)-1); // random int 0, 1 or 2, -1 to transform to -1, 0, 1
        } else return 0;
    }

//    public int be_observed() { // Scrambles and passes env.Attitude
//        // This scrambler sits in place of using a image recognition software, and implements a chance of incorrect
//        // identification at env.Attitude for the Agent at a similar (intended to be representative) proportion
//        int scrambledAttitude = attitude;
//        if (random.nextDouble() > FERFactor){
//            scrambledAttitude = random.nextInt(3)-1; // random int 0, 1 or 2, -1 to transform to -1, 0, 1
//        }
//        return scrambledAttitude;
//    }

//    public void assess_apology(int apology){ // When an apology is provisioned, determine whether it aligns with justification and update attitude
//        if (apology == justification){  // If the agent has apologised successfully
//            attitude = 1;  // Update the actor's attitude to "happy"
//        }
//        // If apology is wrong, do nothing.
//    }

    public String actor_message(String var1){
        return "The actor's current attitude is " + attitude + " and the justification is " + justification;
    }

    public void printFourToFile(String ep, String step, String attitude, String justification) {
        try {
            FileWriter myWriter = new FileWriter("WatcherOutput.txt", true);
            myWriter.write(ep + ", " + step + ", "  + attitude + ", " + justification + System.lineSeparator());
            myWriter.close();
//            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void nextEpisode() {
        this.episode += 1;
        justification = -1; // neutral justification
        this.step = 0;
        setAttitude(0);
    }
}

