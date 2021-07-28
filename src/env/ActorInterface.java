package env;

import org.rlcommunity.rlglue.codec.types.Reward;

public interface ActorInterface {

    void reaction(Reward var1); // Defines the reaction to the Reward, sets the Attitude and Justification

//    int be_observed(); // Scrambles and passes Attitude

    void assess_apology(int apology);

    String actor_message(String var1);
}

