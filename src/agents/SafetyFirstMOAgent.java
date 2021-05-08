// A quick-and-dirty conversion of the WSAgent/TLOAgent code to implement multi-objective
// Q-learning for the AI safety side-effects research project. The agent expects a
// vector of 3 values - the (incorrectly specified) goal reward, our potential-based
// impact reward, and the true performance reward. It ignores the latter (which it 
// shouldn't have access to), and maximises the first, subject to achieving an acceptable
// value for the impact. This is achieved by making action selection dependent on the 
// combination of the accrued impact reward and the expected future impact reward.

package agents;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import tools.valuefunction.SafetyFirstLookupTable;
import tools.valuefunction.TLO_LookupTable;
import tools.valuefunction.interfaces.ActionSelector;
import tools.valuefunction.interfaces.ValueFunction;


public class SafetyFirstMOAgent implements AgentInterface {

    double impactThreshold = -0.1; // sets threshold on the acceptable level of environmental disruption - use high value to 'switch off' thresholding
	
	SafetyFirstLookupTable vf = null;
    Stack<StateActionDiscrete> tracingStack = null;

    private boolean policyFrozen = false;
    private boolean debugging = false;
    private Random random;

    private int numActions = 0;
    private int numStates = 0;
    int numOfObjectives;

    private final double initQValues[]={0,0,0};
    int explorationStrategy; // flag used to indicate which type of exploration strategy is being used
    //if using eGreedy exploration
    double startingEpsilon;
    double epsilonLinearDecay;
    double epsilon;
    // if using softmax selection
    double startingTemperature;
    double temperatureDecayRatio;
    double temperature;
    
    double alpha;
    double gamma;
    double lambda;
    final int MAX_STACK_SIZE = 20;

    int numOfSteps;
    int numEpisodes;
    double accumulatedImpact; // sum the impact reward received so far

    StateConverter stateConverter = null;

    @Override
    public void agent_init(String taskSpecification) {
    	System.out.println("SafetyFirstMOAgent launched");
        TaskSpecVRLGLUE3 theTaskSpec = new TaskSpecVRLGLUE3(taskSpecification);

        numActions = theTaskSpec.getDiscreteActionRange(0).getMax() + 1;
        numStates = theTaskSpec.getDiscreteObservationRange(0).getMax()+1;
        numOfObjectives = theTaskSpec.getNumOfObjectives();
        vf = new SafetyFirstLookupTable(numOfObjectives, numActions, numStates, 0, impactThreshold);

        random = new Random(471);
        tracingStack = new Stack<>();

        //set the model of converting MDP observation to an int state representation
        stateConverter = new DummyStateConverter();
        StateActionDiscrete.setStateConverter( stateConverter );
        resetForNewTrial();

    }
    
    private void resetForNewTrial()
    {
    	policyFrozen = false;
        numOfSteps = 0;
        numEpisodes = 0;  
        epsilon = startingEpsilon;
        temperature = startingTemperature;
        // reset Q-values
        vf.resetQValues(initQValues);
        accumulatedImpact = 0.0;
        vf.setAccumulatedImpact(accumulatedImpact);
    }
    
    private void resetForNewEpisode()
    {
  	  	numEpisodes++;
        numOfSteps = 0; 
        accumulatedImpact = 0.0;
        vf.setAccumulatedImpact(accumulatedImpact);    	
        tracingStack.clear();
    }

    @Override
    public Action agent_start(Observation observation) {

    	resetForNewEpisode();
        int state = stateConverter.getStateNumber( observation );
        int action = getAction(state);

        Action returnAction = new Action(1, 0, 0);
        returnAction.intArray[0] = action;
        tracingStack.add(new StateActionDiscrete(observation, returnAction)); // put executed action on the stack
    	if (debugging)
    	{
        	for (int i=0; i<numActions; i++)
        	{
        		System.out.print("(");
        		double[] q = vf.getQValues(i, state);
        		for (int j=0; j<numOfObjectives; j++)
        		{
        			System.out.print(q[j]+" ");
        		}
        		System.out.print(") ");
        	}
        	System.out.println();
    		int greedyAction = ((ActionSelector)vf).chooseGreedyAction(state);
    		System.out.println("Starting episode " + numEpisodes + " Epsilon = " + epsilon + " Alpha = " + alpha);
    		System.out.println("Step: " + numOfSteps +"\tState: " + state + "\tGreedy action: " + greedyAction + "\tAction: " + action);
    	}
   		//System.out.println("Starting episode " + numEpisodes + " Epsilon = " + epsilon + " Alpha = " + alpha);
        return returnAction;
    }

    @Override
    public Action agent_step(Reward reward, Observation observation) 
    {
        numOfSteps++;
        accumulatedImpact += reward.getDouble(1); // get the impact-measuring reward
        vf.setAccumulatedImpact(accumulatedImpact);

        int state = stateConverter.getStateNumber( observation );
        int action;
        int greedyAction = ((ActionSelector)vf).chooseGreedyAction(state);

        if (!policyFrozen) {
            double currentLambda = lambda;
            for (int i = tracingStack.size() - 1; i >= 0; i--) {
                StateActionDiscrete pair = tracingStack.get(i);

                int prevAction = pair.getAction().getInt(0);
                int prevState = stateConverter.getStateNumber(pair.getObservation());

                if (i + 1 == tracingStack.size()) // this is the most recent action
                {
                    vf.calculateErrors(prevAction, prevState, greedyAction, state, gamma, reward);
                    vf.update(prevAction, prevState, 1.0, alpha);
                } 
                else {
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
        // clear trace if this action is not greedy, otherwise trim stack if neccesary
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
        if (debugging)
        {
        	for (int i=0; i<numActions; i++)
        	{
        		System.out.print("(");
        		double[] q = vf.getQValues(i, state);
        		for (int j=0; j<numOfObjectives; j++)
        		{
        			System.out.print(q[j]+" ");
        		}
        		System.out.print(") ");
        	}
        	System.out.println();
        	greedyAction = ((ActionSelector)vf).chooseGreedyAction(state);
        	System.out.println("Step: " + numOfSteps +"\tState: " + state + "\tGreedy action: " + greedyAction + "\tAction: " + action + "\tImpact: " + reward.getDouble(1) + "\tReward: " + reward.getDouble(0));
        	System.out.println();
        }
        return returnAction;
    }

    @Override
    public void agent_end(Reward reward) 
    {
  	  	numOfSteps++;
  	  	epsilon -= epsilonLinearDecay;
  	  	temperature *= temperatureDecayRatio;
        if (!policyFrozen) {
            double currentLambda = lambda;
            for (int i = tracingStack.size() - 1; i >= 0; i--) {
                StateActionDiscrete pair = tracingStack.get(i);

                int prevAction = pair.getAction().getInt(0);
                int prevState = stateConverter.getStateNumber(pair.getObservation());

                if (i + 1 == tracingStack.size()) 
                {
                    vf.calculateTerminalErrors(prevAction, prevState, gamma, reward);
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
        }
        if (debugging)
        {
        	System.out.println("Step: " + numOfSteps + "\tImpact: " + reward.getDouble(1) + "\tReward: " + reward.getDouble(0));
        	System.out.println("---------------------------------------------");
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
        if (!policyFrozen)
        {
        	switch (explorationStrategy)
        	{
	        	case TLO_LookupTable.EGREEDY: 
	        		action = valueFunction.choosePossiblyExploratoryAction(epsilon, state); 
	        		break;
	        	case TLO_LookupTable.SOFTMAX_TOURNAMENT: 
	        	case TLO_LookupTable.SOFTMAX_ADDITIVE_EPSILON : 
	        		action = valueFunction.choosePossiblyExploratoryAction(temperature, state);
	        		break;
	        	default:
	        		action = -1; // this should never happen - if it does we'll return an invalid value to force the program to halt
        	}
        } 
        else 
        {
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
    	if (message.equals("get_agent_name"))
    	{
    		return "SafetyFirstMO";
    	}
        if (message.equals("freeze_learning")) {
            policyFrozen = true;
            System.out.println("Learning has been freezed");
            return "message understood, policy frozen";
        }
        else if (message.startsWith("change_weights")){
            System.out.print("SafetyFirstMOAgent: Weights can not be changed");
            return "SafetyFirstMOAgent: Weights can not be changed";
        }
        if (message.startsWith("set_learning_parameters")){
        	System.out.println(message);
        	String[] parts = message.split(" ");
        	alpha = Double.valueOf(parts[1]).doubleValue();
        	lambda = Double.valueOf(parts[2]).doubleValue(); 
        	gamma = Double.valueOf(parts[3]).doubleValue();
        	explorationStrategy = Integer.valueOf(parts[4]).intValue();
        	vf.setExplorationStrategy(explorationStrategy);
        	System.out.print("Alpha = " + alpha + " Lambda = " + lambda + " Gamma = " + gamma + " exploration = " + TLO_LookupTable.explorationStrategyToString(explorationStrategy));
            System.out.println();
            return "Learning parameters set";
        }
        if (message.startsWith("set_egreedy_parameters")){
        	String[] parts = message.split(" ");
        	startingEpsilon = Double.valueOf(parts[1]).doubleValue();
        	epsilonLinearDecay = startingEpsilon / Double.valueOf(parts[2]).doubleValue(); // 2nd param is number of online episodes over which e should decay to 0
            System.out.println("Starting epsilon changed to " + startingEpsilon);
            return "egreedy parameters changed";
        }
        if (message.startsWith("set_softmax_parameters")){
        	String[] parts = message.split(" ");
        	startingTemperature = Double.valueOf(parts[1]).doubleValue();
        	int numEpisodes =  Integer.valueOf(parts[2]).intValue(); // 2nd param is number of online episodes over which temperature should decay to 0.01
        	temperatureDecayRatio = Math.pow(0.01/startingTemperature,1.0/numEpisodes);
            System.out.println("Starting temperature changed to " + startingTemperature + " Decay ratio = " + temperatureDecayRatio);
            return "softmax parameters changed";
        } 
        else if (message.equals("start_new_trial")){
        	resetForNewTrial();
            System.out.println("New trial started: Q-values and other variables reset");
            return "New trial started: Q-values and other variables reset";
        }
        else if (message.equals("start-debugging"))
    	{
    		debugging = true;
    		return "Debugging enabled in agent";
    	}
        else if (message.equals("stop-debugging"))
    	{
    		debugging = false;
    		return "Debugging disabled in agent";
    	}
        System.out.println("SafetyFirstMOAgent - unknown message: " + message);
        return "SafetyFirstMOAgent does not understand your message.";
    }
    
    // used for debugging with the ComparisonAgentForDebugging
    // dumps Q-values and feedback on action-selection for the current Observation
    public void dumpInfo(Observation observation, Action thisAction, Action otherAgentAction)
    {
        int state = stateConverter.getStateNumber(observation);
        int action = thisAction.getInt(0);
        int otherAction = otherAgentAction.getInt(0);
        System.out.println("SafetyFirstMO");
		System.out.println("\tEpisode" + numEpisodes + "Step: " + numOfSteps +"\tState: " + "\tAction: " + action);
		System.out.println("\tIs other agent's action greedy for me? " + ((ActionSelector)vf).isGreedy(state,otherAction));
    	for (int i=0; i<numActions; i++)
    	{
    		System.out.print("\t(");
    		double[] q = vf.getQValues(i, state);
    		for (int j=0; j<numOfObjectives; j++)
    		{
    			System.out.print(q[j]+" ");
    		}
    		System.out.print(") ");
    	}
        System.out.println();    	
    }

    public static void main(String[] args) {
        AgentLoader theLoader = new AgentLoader( new SafetyFirstMOAgent() );
        theLoader.run();

    }


}
