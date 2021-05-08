package agents;

import java.math.BigDecimal;
import java.util.Random;
import java.util.Stack;

import org.rlcommunity.rlglue.codec.AgentInterface;
import org.rlcommunity.rlglue.codec.taskspec.TaskSpecVRLGLUE3;
import org.rlcommunity.rlglue.codec.types.Action;
import org.rlcommunity.rlglue.codec.types.Observation;
import org.rlcommunity.rlglue.codec.types.Reward;
import org.rlcommunity.rlglue.codec.util.AgentLoader;

import tools.hypervolume.Point;
import tools.staterep.DummyStateConverter;
import tools.staterep.interfaces.StateConverter;
import tools.traces.StateActionDiscrete;
import tools.valuefunction.WSLookupTable;
import tools.valuefunction.interfaces.ActionSelector;
import tools.valuefunction.interfaces.ValueFunction;


public class WSNoveltyAgent implements AgentInterface {

    ValueFunction vf = null;
    Stack<StateActionDiscrete> tracingStack = null;

    private boolean policyFrozen = false;
    private Random random;

    private int numActions = 0;
    private int numStates = 0;
    int numActualObjectives; // number of objectives, not counting the 'novelty' objective
    int numOfObjectives; // number of objectives, including the 'novelty' objective

    private final double initQValues[]={0,0,0,100}; // zero for all regular objectives, positive value for 'novelty' objective
    double alpha = 0.2;
    double startingEpsilon = 0.0; // should generally be 0 when using novelty objective exploration, but this option has been retained for further flexibility
    double epsilonLinearDecay = startingEpsilon / 4999; // set this to the inverse of the max number of episodes - 1
    double epsilon;
    double gamma = 1.0;
    double lambda = 0.9;
    double noveltyGamma = 0.99;
    double noveltyWeight = 0.9;
    double objWeights[] = {0.6,0.3,0.1};
    double gammaArray[] = {gamma,gamma,gamma,noveltyGamma};
    final int MAX_STACK_SIZE = 10;
    
    int numOfSteps;
    int numEpisodes;

    StateConverter stateConverter = null;

    @Override
    public void agent_init(String taskSpecification) {
    	System.out.println("Linear-weighted novelty-driven Q-learning agent launched");
        TaskSpecVRLGLUE3 theTaskSpec = new TaskSpecVRLGLUE3(taskSpecification);

        numActions = theTaskSpec.getDiscreteActionRange(0).getMax() + 1;
        numStates = theTaskSpec.getDiscreteObservationRange(0).getMax()+1;
        numActualObjectives = theTaskSpec.getNumOfObjectives();
        numOfObjectives = numActualObjectives + 1;

        double[] weights = new double[numOfObjectives];
        for (int i=0; i<numActualObjectives; i++)
        {
        	weights[i]=objWeights[i]*(1-noveltyWeight);
        }
        weights[numOfObjectives-1] = noveltyWeight;
        vf = new WSLookupTable( numOfObjectives, numActions, numStates, 0, weights );

        random = new Random();
        tracingStack = new Stack<>();

        //set the model of converting MDP observation to an int state representation
        stateConverter = new DummyStateConverter();
        StateActionDiscrete.setStateConverter( stateConverter );
        resetForNewTrial();

    }
    
    private void resetForNewTrial()
    {
        numOfSteps = 0;
        numEpisodes = 0;  
        epsilon = startingEpsilon;
        // reset Q-values
        vf.resetQValues(initQValues);     
    }

    @Override
    public Action agent_start(Observation observation) {
    	//System.out.println("Starting episode " + numEpisodes + " Epsilon = " + epsilon);
        tracingStack.clear();
        int state = stateConverter.getStateNumber( observation );
        int action = getAction(state);

        Action returnAction = new Action(1, 0, 0);
        returnAction.intArray[0] = action;

        tracingStack.add(new StateActionDiscrete(observation, returnAction));

        return returnAction;

    }

    @Override
    public Action agent_step(Reward reward, Observation observation) 
    {
        numOfSteps++;

        int state = stateConverter.getStateNumber( observation );
        int action;
        int greedyAction = ((ActionSelector)vf).chooseGreedyAction(state);

        if (!policyFrozen) {
        	// copy the existing Reward into a new object with the novelty reward appended to it
        	Reward rewardWithNovelty = new Reward(0,numOfObjectives,0);
            for (int o=0; o<numActualObjectives; o++)
            {
                rewardWithNovelty.setDouble(o, reward.getDouble(o));           	
            }
            rewardWithNovelty.setDouble(numOfObjectives-1,0); // novelty reward is always 0
            double currentLambda = lambda;
            for (int i = tracingStack.size() - 1; i >= 0; i--) {
                StateActionDiscrete pair = tracingStack.get(i);

                int prevAction = pair.getAction().getInt(0);
                int prevState = stateConverter.getStateNumber(pair.getObservation());

                if (i + 1 == tracingStack.size()) {
                    vf.calculateErrors(prevAction, prevState, greedyAction, state, gammaArray, rewardWithNovelty);
                    vf.update(prevAction, prevState, 1.0, alpha);
                } 
                else 
                {
                	// if there is no more recent entry for this state-action pair then update it
                	// this is to implement replacing rather than accumulating traces
                    int index = tracingStack.indexOf(pair, i + 1);
                    if (index == -1) {
                        vf.update(prevAction, prevState, currentLambda, alpha);
                    }
                    currentLambda *= lambda;
                }
            }
            action = getAction(state);
        } else {// if frozen, don't learn and follow greedy policy
            action = greedyAction;
        }

        Action returnAction = new Action(1, 0, 0);
        returnAction.intArray[0] = action;
        // clear trace if this action is not greedy, otherwise trim stack if necessary
        if (isGreedy(state,action))
        {
	        if( tracingStack.size() == MAX_STACK_SIZE ) 
	        {
	            tracingStack.remove(0);
	        }
        }
        else
        {
        	tracingStack.clear();
        }
        // in either case, can now add this state-action to the trace stack
        tracingStack.add( new StateActionDiscrete(observation, returnAction ) );

        return returnAction;
    }

    @Override
    public void agent_end(Reward reward) 
    {
  	  	numOfSteps++;
  	  	numEpisodes++;
  	  	epsilon -= epsilonLinearDecay;
        if (!policyFrozen) {
        	// copy the existing Reward into a new object with the novelty reward appended to it
        	Reward rewardWithNovelty = new Reward(0,numOfObjectives,0);
            for (int o=0; o<numActualObjectives; o++)
            {
                rewardWithNovelty.setDouble(o, reward.getDouble(o));           	
            }
            rewardWithNovelty.setDouble(numOfObjectives-1,0); // novelty reward is always 0
            double currentLambda = lambda;
            for (int i = tracingStack.size() - 1; i >= 0; i--) {
                StateActionDiscrete pair = tracingStack.get(i);

                int prevAction = pair.getAction().getInt(0);
                int prevState = stateConverter.getStateNumber(pair.getObservation());

                if (i + 1 == tracingStack.size()) {
                    vf.calculateTerminalErrors(prevAction, prevState, gammaArray, rewardWithNovelty);
                    vf.update(prevAction, prevState, 1.0, alpha);
                } 
                else 
                {
                    int index = tracingStack.indexOf(pair, i + 1);
                    if (index == -1) {
                        vf.update(prevAction, prevState, currentLambda, alpha);
                    }
                    currentLambda *= lambda;
                }
            }
        }
    }

    @Override
    public void agent_cleanup() {
        vf = null;
        policyFrozen = false;
    }

    private int getAction(int state) {
        ActionSelector valueFunction = (ActionSelector) vf;
        int action;
        if (!policyFrozen) {
            if (random.nextDouble() < epsilon) {
                action = random.nextInt(numActions);
            } else {
                action = valueFunction.chooseGreedyAction(state);
            }
        } else {
            action = valueFunction.chooseGreedyAction(state);
        }
        return action;
    }
    
    // returns true if the specified action is amongst the greedy actions for the 
    // specified state, false otherwise
    private boolean isGreedy(int state, int action)
    {
        ActionSelector valueFunction = (ActionSelector) vf;
        return valueFunction.isGreedy(state,action);  	
    }

    @Override
    public String agent_message(String message) {
        if (message.equals("freeze learning")) {
            policyFrozen = true;
            System.out.println("Learning has been freezed");
            return "message understood, policy frozen";
        }
        if (message.equals("start_new_trial")){
        	resetForNewTrial();
            System.out.println("New trial started: Q-values and other variables reset");
            return "New trial started: Q-values and other variables reset";
        }

        return "WSNoveltyAgent(Java) does not understand your message.";
    }

    public static void main(String[] args) {
        AgentLoader theLoader = new AgentLoader( new WSNoveltyAgent() );
        theLoader.run();

    }


}
